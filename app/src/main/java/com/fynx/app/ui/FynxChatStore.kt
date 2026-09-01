package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local chat persistence foundation. Server sync can replace this later without changing the UI contract. */
object FynxChatStore {
    private const val PREFS = "fynx_chat_store"

    fun load(context: Context, chatKey: String, fallback: ChatMessage): List<ChatMessage> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(chatKey), null) ?: return listOf(fallback)
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        ChatMessage(
                            text = item.optString("text"),
                            fromMe = item.optBoolean("fromMe"),
                            id = item.optString("id"),
                            timestamp = item.optLong("timestamp"),
                            delivered = item.optBoolean("delivered"),
                            read = item.optBoolean("read"),
                            replyToId = item.optString("replyToId").takeIf { it.isNotEmpty() },
                            reaction = item.optString("reaction").takeIf { it.isNotEmpty() },
                            edited = item.optBoolean("edited")
                        )
                    )
                }
            }.ifEmpty { listOf(fallback) }
        }.getOrElse { listOf(fallback) }
    }

    fun save(context: Context, chatKey: String, messages: List<ChatMessage>) {
        val array = JSONArray()
        messages.forEach { message ->
            // Voice recordings and picked files stay local to the current session/cache.
            // Text metadata is persisted so reopening a chat does not lose the conversation.
            array.put(
                JSONObject().apply {
                    put("text", message.text)
                    put("fromMe", message.fromMe)
                    put("id", message.id)
                    put("timestamp", message.timestamp)
                    put("delivered", message.delivered)
                    put("read", message.read)
                    put("replyToId", message.replyToId ?: "")
                    put("reaction", message.reaction ?: "")
                    put("edited", message.edited)
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key(chatKey), array.toString())
            .apply()
    }

    fun clear(context: Context, chatKey: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(key(chatKey))
            .apply()
    }

    private fun key(chatKey: String): String = "chat_${chatKey.replace(Regex("[^A-Za-z0-9_@.-]"), "_")}"
}
