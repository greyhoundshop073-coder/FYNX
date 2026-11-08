package com.fynx.app.ui

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONArray
import org.json.JSONObject

internal data class FynxChatOutboxReconciliation(
    val messages: List<ChatMessage>,
    val remainingAliases: Map<String, String>
)

/**
 * Reconciles late Compose snapshots with messages already acknowledged by the
 * server. The ID alias prevents stale local pending rows from being restored.
 */
internal fun reconcilePendingOutboxMessages(
    snapshot: List<ChatMessage>,
    previous: List<ChatMessage>,
    aliases: Map<String, String>
): FynxChatOutboxReconciliation {
    if (aliases.isEmpty()) return FynxChatOutboxReconciliation(snapshot, emptyMap())

    val previousById = previous.associateBy { it.id }
    val snapshotIds = snapshot.mapTo(mutableSetOf()) { it.id }
    val reconciled = snapshot.map { message ->
        val remoteId = aliases[message.id]
        remoteId?.let(previousById::get) ?: message
    }.toMutableList()

    aliases.forEach { (localId, remoteId) ->
        val authoritative = previousById[remoteId] ?: return@forEach
        if (localId !in snapshotIds && remoteId !in snapshotIds) {
            reconciled += authoritative
        }
    }

    val remainingAliases = aliases.filter { (localId, remoteId) ->
        localId in snapshotIds || remoteId !in snapshotIds
    }
    return FynxChatOutboxReconciliation(
        messages = reconciled.distinctBy { it.id }.sortedBy { it.timestamp },
        remainingAliases = remainingAliases
    )
}

/**
 * Local chat persistence foundation.
 * Data is isolated per signed-in account so multiple FYNX users sharing a device
 * cannot see each other's conversations. Server sync is layered onto the existing
 * UI contract so edit/delete actions remain authoritative on the production backend.
 */
object FynxChatStore {
    /** Emits a chat username whenever its local preview may have changed. */
    val previewUpdates = MutableSharedFlow<String>(extraBufferCapacity = 64)

    private const val PREFS = "fynx_chat_store"
    private const val SYNC_INITIALIZED_SUFFIX = "_sync_initialized"
    private const val OUTBOX_REPLACEMENTS_SUFFIX = "_outbox_replacements"

    @Synchronized
    fun load(context: Context, chatKey: String, fallback: ChatMessage? = null): List<ChatMessage> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(context, chatKey), null) ?: return fallback?.let { listOf(it) } ?: emptyList()
        return parseMessages(raw, fallback)
    }

    @Synchronized
    fun save(context: Context, chatKey: String, messages: List<ChatMessage>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val storageKey = key(context, chatKey)
        val previous = prefs.getString(storageKey, null)?.let { parseMessages(it, null) }.orEmpty()
        val syncInitialized = prefs.getBoolean(syncKey(context, chatKey), false)

        // A transient/failed remote history response must never erase an already
        // established local conversation. The screen can reconcile authoritative
        // data later, but persistence must keep the last known good history.
        if (syncInitialized && previous.isNotEmpty() && messages.isEmpty()) return

        val aliases = loadOutboxReplacements(prefs, context, chatKey)
        val reconciliation = reconcilePendingOutboxMessages(messages, previous, aliases)
        val persistedMessages = reconciliation.messages
        saveOutboxReplacements(prefs, context, chatKey, reconciliation.remainingAliases)

        // ConversationPanel keeps local state responsive while production edit/delete
        // operations are sent to the server. The server remains the source of truth.
        if (syncInitialized && previous.isNotEmpty()) {
            val previousById = previous.associateBy { it.id }
            val currentById = persistedMessages.associateBy { it.id }
            val edited = persistedMessages.filter { current ->
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
        persistedMessages.forEach { message ->
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
                    put("messageType", message.messageType)
                    put("messagePayload", JSONObject(message.messagePayload))
                }
            )
        }
        prefs.edit()
            .putString(storageKey, array.toString())
            .putBoolean(syncKey(context, chatKey), true)
            .apply()
    }

    /** Replace or insert a pending/outbox message with the authoritative server message. */
    @Synchronized
    fun replaceMessage(context: Context, chatKey: String, localId: String, replacement: ChatMessage) {
        val current = load(context, chatKey)
        val reconciled = if (current.any { it.id == localId }) {
            current.map { if (it.id == localId) replacement else it }
        } else {
            current.filterNot { it.id == replacement.id } + replacement
        }
        save(context, chatKey, reconciled)

        // Keep a local-ID -> server-ID alias so a late Compose snapshot cannot
        // overwrite the successful server replacement with the pending local item.
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val aliases = loadOutboxReplacements(prefs, context, chatKey) + (localId to replacement.id)
        saveOutboxReplacements(prefs, context, chatKey, aliases)
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
        val unreadCount = load(context, preview.username)
            .count { !it.fromMe && !it.read }
        val normalizedPreview = preview.copy(unreadCount = unreadCount)
        val previews = loadPreviews(context).filterNot { it.username.equals(normalizedPreview.username, ignoreCase = true) }
        val updated = listOf(normalizedPreview) + previews
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
        previewUpdates.tryEmit(normalizedPreview.username)
    }

    fun clear(context: Context, chatKey: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val normalizedChatKey = chatKey.trim().removePrefix("@").lowercase()
        val updatedPreviews = loadPreviews(context).filterNot {
            it.username.trim().removePrefix("@").equals(normalizedChatKey, ignoreCase = true)
        }
        val array = JSONArray()
        updatedPreviews.forEach { item ->
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
        prefs.edit()
            .remove(key(context, chatKey))
            .remove(syncKey(context, chatKey))
            .putString(previewKey(context), array.toString())
            .apply()
    }

    private fun parseMessages(raw: String, fallback: ChatMessage?): List<ChatMessage> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val storedId = item.optString("id").trim()
                add(
                    ChatMessage(
                        text = item.optString("text"),
                        fromMe = item.optBoolean("fromMe"),
                        id = storedId.ifBlank { "local-${index}-${item.optLong("timestamp")}" },
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
                        mediaId = item.optString("mediaId").takeIf { it.isNotEmpty() },
                        messageType = item.optString("messageType").ifBlank { "text" },
                        messagePayload = item.optJSONObject("messagePayload")?.let { payload ->
                            payload.keys().asSequence().associateWith { key -> payload.optString(key) }
                        }.orEmpty()
                    )
                )
            }
        }.distinctBy { it.id }
    }.getOrElse { fallback?.let { listOf(it) } ?: emptyList() }

    private fun accountKey(context: Context): String =
        FynxAuthStore.storedUsername(context)?.trim()?.lowercase()?.ifBlank { "preview" } ?: "preview"

    private fun previewKey(context: Context): String = "chat_previews_${safeKey(accountKey(context))}"

    private fun key(context: Context, chatKey: String): String =
        "chat_${safeKey(accountKey(context))}_${safeKey(chatKey)}"

    private fun syncKey(context: Context, chatKey: String): String =
        key(context, chatKey) + SYNC_INITIALIZED_SUFFIX

    private fun outboxReplacementKey(context: Context, chatKey: String): String =
        key(context, chatKey) + OUTBOX_REPLACEMENTS_SUFFIX

    private fun loadOutboxReplacements(
        prefs: android.content.SharedPreferences,
        context: Context,
        chatKey: String
    ): Map<String, String> {
        val raw = prefs.getString(outboxReplacementKey(context, chatKey), null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            val keys = json.keys()
            buildMap {
                while (keys.hasNext()) {
                    val localId = keys.next()
                    val remoteId = json.optString(localId).trim()
                    if (localId.isNotBlank() && remoteId.isNotBlank() && remoteId != "null") {
                        put(localId, remoteId)
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun saveOutboxReplacements(
        prefs: android.content.SharedPreferences,
        context: Context,
        chatKey: String,
        aliases: Map<String, String>
    ) {
        val editor = prefs.edit()
        if (aliases.isEmpty()) {
            editor.remove(outboxReplacementKey(context, chatKey)).apply()
        } else {
            editor.putString(outboxReplacementKey(context, chatKey), JSONObject(aliases).toString()).apply()
        }
    }

    private fun safeKey(value: String): String = value.replace(Regex("[^A-Za-z0-9_@.-]"), "_")
}