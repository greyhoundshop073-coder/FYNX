package com.fynx.app.ui

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Small durable outbox for retryable chat sends. Existing server chat remains the source of truth. */
object FynxOfflineOutbox {
    private const val PREFS_PREFIX = "fynx_offline_outbox_"
    private const val KEY = "items"

    data class Item(
        val id: String,
        val requestId: String,
        val recipient: String,
        val text: String,
        val replyToId: String?,
        val mediaId: String?,
        val mediaType: String?,
        val voiceDurationMs: Long,
        val createdAt: Long,
        val messageType: String = "text",
        val messagePayload: Map<String, String> = emptyMap()
    )

    private fun preferencesName(context: Context): String? {
        val accountKey = FynxAuthStore.accountStorageKey(context)?.takeIf { it.isNotBlank() } ?: return null
        val encodedAccount = Base64.encodeToString(
            accountKey.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return PREFS_PREFIX + encodedAccount
    }

    @Synchronized
    fun enqueue(context: Context, item: Item): Boolean {
        val name = preferencesName(context) ?: return false
        val current = load(context).filterNot { it.id == item.id }.toMutableList()
        current += item
        save(context, name, current)
        return true
    }

    @Synchronized
    fun remove(context: Context, id: String) {
        val name = preferencesName(context) ?: return
        save(context, name, load(context).filterNot { it.id == id })
    }

    fun load(context: Context): List<Item> {
        val name = preferencesName(context) ?: return emptyList()
        val raw = context.getSharedPreferences(name, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(Item(
                        id = o.getString("id"),
                        requestId = o.optString("requestId").takeIf { it.isNotBlank() && it != "null" }
                            ?: UUID.nameUUIDFromBytes(o.getString("id").toByteArray(Charsets.UTF_8)).toString(),
                        recipient = o.getString("recipient"),
                        text = o.optString("text"),
                        replyToId = o.optString("replyToId").takeIf { it.isNotBlank() && it != "null" },
                        mediaId = o.optString("mediaId").takeIf { it.isNotBlank() && it != "null" },
                        mediaType = o.optString("mediaType").takeIf { it.isNotBlank() && it != "null" },
                        voiceDurationMs = o.optLong("voiceDurationMs"),
                        createdAt = o.optLong("createdAt"),
                        messageType = o.optString("messageType").takeIf { it.isNotBlank() && it != "null" } ?: "text",
                        messagePayload = o.optJSONObject("messagePayload")?.let { payload ->
                            buildMap {
                                val keys = payload.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    put(key, payload.optString(key))
                                }
                            }
                        } ?: emptyMap()
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun save(context: Context, name: String, items: List<Item>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id); put("requestId", item.requestId); put("recipient", item.recipient); put("text", item.text)
                put("replyToId", item.replyToId ?: JSONObject.NULL); put("mediaId", item.mediaId ?: JSONObject.NULL)
                put("mediaType", item.mediaType ?: JSONObject.NULL); put("voiceDurationMs", item.voiceDurationMs); put("createdAt", item.createdAt)
                put("messageType", item.messageType); put("messagePayload", JSONObject(item.messagePayload))
            })
        }
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
