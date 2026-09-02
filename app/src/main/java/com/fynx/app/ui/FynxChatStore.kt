package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local chat persistence foundation. Server sync can replace this later without changing the UI contract. */
object FynxChatStore {
    private const val PREFS = "fynx_chat_store"
    private const val CHAT_LIST_KEY = "chat_previews"

    fun load(context: Context, chatKey: String, fallback: ChatMessage? = null): List<ChatMessage> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(chatKey), null) ?: return fallback?.let { listOf(it) } ?: emptyList()
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
                            edited = item.optBoolean("edited"),
                            attachmentUri = item.optString("attachmentUri").takeIf { it.isNotEmpty() },
                            attachmentType = item.optString("attachmentType").takeIf { it.isNotEmpty() },
                            voiceUri = item.optString("voiceUri").takeIf { it.isNotEmpty() },
                            voiceDurationMs = item.optLong("voiceDurationMs")
                        )
                    )
                }
            }
        }.getOrElse { fallback?.let { listOf(it) } ?: emptyList() }
    }

    fun save(context: Context, chatKey: String, messages: List<ChatMessage>) {
        val array = JSONArray()
        messages.forEach { message ->
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
                    put("attachmentUri", message.attachmentUri ?: "")
                    put("attachmentType", message.attachmentType ?: "")
                    put("voiceUri", message.voiceUri ?: "")
                    put("voiceDurationMs", message.voiceDurationMs)
                }
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key(chatKey), array.toString()).apply()
    }

    fun loadPreviews(context: Context): List<ChatPreview> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CHAT_LIST_KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(ChatPreview(
                        name = item.optString("name"),
                        username = item.optString("username"),
                        lastMessage = item.optString("lastMessage"),
                        time = item.optString("time"),
                        unreadCount = item.optInt("unreadCount"),
                        online = item.optBoolean("online")
                    ))
                }
            }
        }.getOrElse { emptyList() }
    }

    fun savePreview(context: Context, preview: ChatPreview) {
        val previews = loadPreviews(context).filterNot { it.username.equals(preview.username, ignoreCase = true) }
        val updated = listOf(preview) + previews
        val array = JSONArray()
        updated.forEach { item ->
            array.put(JSONObject().apply {
                put("name", item.name)
                put("username", item.username)
                put("lastMessage", item.lastMessage)
                put("time", item.time)
                put("unreadCount", item.unreadCount)
                put("online", item.online)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(CHAT_LIST_KEY, array.toString()).apply()
    }

    fun clear(context: Context, chatKey: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(key(chatKey)).apply()
    }

    private fun key(chatKey: String): String = "chat_${chatKey.replace(Regex("[^A-Za-z0-9_@.-]"), "_")}"
}
