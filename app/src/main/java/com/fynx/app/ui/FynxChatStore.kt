package com.fynx.app.ui

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local chat persistence foundation.
 * Data is isolated per signed-in account so multiple FYNX users sharing a device
 * cannot see each other's conversations. Server sync is layered onto the existing
 * UI contract so edit/delete actions remain authoritative on the production backend.
 */
object FynxChatStore {
    private const val PREFS = "fynx_chat_store"
    private const val SYNC_INITIALIZED_SUFFIX = "_sync_initialized"

    fun load(context: Context, chatKey: String, fallback: ChatMessage? = null): List<ChatMessage> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(context, chatKey), null) ?: return fallback?.let { listOf(it) } ?: emptyList()
        return parseMessages(raw, fallback)
    }

    fun save(context: Context, chatKey: String, messages: List<ChatMessage>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storageKey = key(context, chatKey)
        val previous = prefs.getString(storageKey, null)?.let { parseMessages(it, null) }.orEmpty()
        val syncInitialized = prefs.getBoolean(syncKey(context, chatKey), false)

        // ConversationPanel already updates its local state for edit/delete. Reconcile
        // those state transitions with the real server without changing that UI contract.
        if (syncInitialized && previous.isNotEmpty()) {
            val previousById = previous.associateBy { it.id }
            val currentById = messages.associateBy { it.id }
            val edited = messages.filter { current ->
                val old = previousById[current.id]
                current.id.toLongOrNull() != null && current.fromMe && current.edited &&
                    old != null && old.text != current.text && current.text.isNotBlank()
            }
            val deleted = previous.filter { old ->
                old.id.toLongOrNull() != null && old.fromMe && currentById[old.id] == null
            }
            if (edited.isNotEmpty() || deleted.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    edited.forEach { message ->
                        retryServerOperation { FynxProductionMessaging.editMessage(context, message.id, message.text) }
                    }
                    deleted.forEach { message ->
                        retryServerOperation { FynxProductionMessaging.deleteMessage(context, message.id) }
                    }
                }
            }
        }

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
                    put("mediaId", message.mediaId ?: "")
                }
            )
        }
        prefs.edit()
            .putString(storageKey, array.toString())
            .putBoolean(syncKey(context, chatKey), true)
            .apply()
    }

    private suspend fun retryServerOperation(operation: suspend () -> Result<*>) {
        repeat(3) { attempt ->
            val success = runCatching { operation().getOrThrow() }.isSuccess
            if (success) return
            if (attempt < 2) delay(750L * (attempt + 1))
        }
    }

    fun loadPreviews(context: Context): List<ChatPreview> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(previewKey(context), null) ?: return emptyList()
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
                        online = item.optBoolean("online"),
                        avatarUri = item.optString("avatarUri").takeIf { it.isNotEmpty() }
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
                put("avatarUri", item.avatarUri ?: "")
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(previewKey(context), array.toString()).apply()
    }

    fun clear(context: Context, chatKey: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(key(context, chatKey)).remove(syncKey(context, chatKey)).apply()
    }

    private fun parseMessages(raw: String, fallback: ChatMessage?): List<ChatMessage> = runCatching {
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
                        voiceDurationMs = item.optLong("voiceDurationMs"),
                        mediaId = item.optString("mediaId").takeIf { it.isNotEmpty() }
                    )
                )
            }
        }
    }.getOrElse { fallback?.let { listOf(it) } ?: emptyList() }

    private fun accountKey(context: Context): String =
        FynxAuthStore.storedUsername(context)?.trim()?.lowercase()?.ifBlank { "preview" } ?: "preview"

    private fun previewKey(context: Context): String = "chat_previews_${safeKey(accountKey(context))}"

    private fun key(context: Context, chatKey: String): String =
        "chat_${safeKey(accountKey(context))}_${safeKey(chatKey)}"

    private fun syncKey(context: Context, chatKey: String): String =
        key(context, chatKey) + SYNC_INITIALIZED_SUFFIX

    private fun safeKey(value: String): String = value.replace(Regex("[^A-Za-z0-9_@.-]"), "_")
}
