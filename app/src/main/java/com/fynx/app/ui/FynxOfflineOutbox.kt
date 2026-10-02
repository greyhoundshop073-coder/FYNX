package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Small durable outbox for retryable chat sends. Existing server chat remains the source of truth. */
object FynxOfflineOutbox {
    private const val PREFS = "fynx_offline_outbox"
    private const val KEY = "items"

    data class Item(
        val id: String,
        val recipient: String,
        val text: String,
        val replyToId: String?,
        val mediaId: String?,
        val mediaType: String?,
        val voiceDurationMs: Long,
        val createdAt: Long
    )

    @Synchronized
    fun enqueue(context: Context, item: Item) {
        val current = load(context).filterNot { it.id == item.id }.toMutableList()
        current += item
        save(context, current)
    }

    @Synchronized
    fun remove(context: Context, id: String) = save(context, load(context).filterNot { it.id == id })

    fun load(context: Context): List<Item> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(Item(o.getString("id"), o.getString("recipient"), o.optString("text"), o.optString("replyToId").takeIf { it.isNotBlank() }, o.optString("mediaId").takeIf { it.isNotBlank() }, o.optString("mediaType").takeIf { it.isNotBlank() }, o.optLong("voiceDurationMs"), o.optLong("createdAt")))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun save(context: Context, items: List<Item>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id); put("recipient", item.recipient); put("text", item.text)
                put("replyToId", item.replyToId ?: JSONObject.NULL); put("mediaId", item.mediaId ?: JSONObject.NULL)
                put("mediaType", item.mediaType ?: JSONObject.NULL); put("voiceDurationMs", item.voiceDurationMs); put("createdAt", item.createdAt)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
