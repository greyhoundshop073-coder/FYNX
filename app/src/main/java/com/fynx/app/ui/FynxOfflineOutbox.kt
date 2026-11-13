package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Small durable outbox for retryable chat sends. Existing server chat remains the source of truth. */
object FynxOfflineOutbox {
    private const val PREFS_PREFIX = "fynx_offline_outbox_"
    private const val KEY = "items"
    private const val MAX_ATTACHMENT_BYTES = 12L * 1024L * 1024L

    /** Notifies the active conversation when its durable outbox changes. */
    val updates = MutableSharedFlow<String>(extraBufferCapacity = 64)

    private fun notifyRecipient(recipient: String) {
        updates.tryEmit(recipient.trim().removePrefix("@").lowercase())
    }

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
        val messagePayload: Map<String, String> = emptyMap(),
        val localMediaPath: String? = null,
        val localMediaMimeType: String? = null,
        val marketplaceListingId: String? = null,
        val sentMessageId: String? = null
    )

    private fun preferencesName(context: Context): String? {
        val accountKey = FynxAuthStore.accountStorageKey(context)?.takeIf { it.isNotBlank() } ?: return null
        val encodedAccount = Base64.encodeToString(
            accountKey.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return PREFS_PREFIX + encodedAccount
    }

    fun enqueue(context: Context, item: Item): Boolean {
        val name = preferencesName(context) ?: return false
        return enqueue(context, name, item)
    }

    @Synchronized
    private fun enqueue(context: Context, name: String, item: Item): Boolean {
        val current = loadForPreferences(context, name).filterNot { it.id == item.id }
        save(context, name, current + item)
        notifyRecipient(item.recipient)
        return true
    }

    @Synchronized
    fun remove(context: Context, id: String) {
        val name = preferencesName(context) ?: return
        val current = load(context)
        val removed = current.firstOrNull { it.id == id }
        removed?.localMediaPath?.let { path -> runCatching { File(path).delete() } }
        save(context, name, current.filterNot { it.id == id })
        removed?.let { notifyRecipient(it.recipient) }
    }

    @Synchronized
    fun updateMediaId(context: Context, id: String, mediaId: String) {
        val name = preferencesName(context) ?: return
        val current = load(context)
        save(context, name, current.map { item -> if (item.id == id) item.copy(mediaId = mediaId) else item })
        current.firstOrNull { it.id == id }?.let { notifyRecipient(it.recipient) }
    }

    @Synchronized
    fun markSent(context: Context, id: String, messageId: String) {
        val cleanMessageId = messageId.trim()
        if (cleanMessageId.isBlank()) return
        val name = preferencesName(context) ?: return
        val current = load(context)
        save(context, name, current.map { item ->
            if (item.id == id) item.copy(sentMessageId = cleanMessageId) else item
        })
        current.firstOrNull { it.id == id }?.let { notifyRecipient(it.recipient) }
    }

    suspend fun enqueueAttachment(context: Context, item: Item, sourceUri: Uri, mimeType: String): String? = withContext(Dispatchers.IO) {
        val accountKey = FynxAuthStore.accountStorageKey(context)?.takeIf { it.isNotBlank() }
            ?: return@withContext null
        val name = preferencesName(context) ?: return@withContext null
        val accountSuffix = name.removePrefix(PREFS_PREFIX)
        val directory = File(context.filesDir, "fynx_chat_outbox_media_$accountSuffix")
        if (!directory.exists() && !directory.mkdirs()) return@withContext null
        val safeId = item.id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80).ifBlank { UUID.randomUUID().toString() }
        val extension = when (mimeType.substringBefore(';').trim().lowercase()) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "video/mp4" -> "mp4"
            "video/quicktime" -> "mov"
            "audio/mpeg" -> "mp3"
            "audio/wav" -> "wav"
            "audio/aac" -> "aac"
            "audio/mp4" -> "m4a"
            "application/pdf" -> "pdf"
            else -> "bin"
        }
        val target = File(directory, "$safeId.$extension")
        try {
            val input = if (sourceUri.scheme.equals("file", true)) sourceUri.path?.let { File(it).inputStream() }
                else context.contentResolver.openInputStream(sourceUri)
            if (input == null) return@withContext null
            input.use { source ->
                target.outputStream().use { destination ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_ATTACHMENT_BYTES) throw IllegalArgumentException("Attachment exceeds the 12 MB offline limit.")
                        destination.write(buffer, 0, count)
                    }
                }
            }
            if (target.length() !in 1..MAX_ATTACHMENT_BYTES) throw IllegalArgumentException("Attachment is empty or too large.")
            val path = target.absolutePath
            if (FynxAuthStore.accountStorageKey(context) != accountKey) {
                target.delete()
                return@withContext null
            }
            if (enqueue(context, name, item.copy(localMediaPath = path, localMediaMimeType = mimeType))) path else {
                target.delete()
                null
            }
        } catch (_: Throwable) {
            target.delete()
            null
        }
    }

    fun load(context: Context): List<Item> {
        val name = preferencesName(context) ?: return emptyList()
        return loadForPreferences(context, name)
    }

    private fun loadForPreferences(context: Context, name: String): List<Item> {
        val raw = context.getSharedPreferences(name, Context.MODE_PRIVATE).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    val payload = o.optJSONObject("messagePayload")?.let { stored ->
                        val keys = stored.keys()
                        buildMap {
                            while (keys.hasNext()) {
                                val key = keys.next()
                                val value = stored.opt(key)
                                if (value != null && value != JSONObject.NULL) put(key, value.toString())
                            }
                        }
                    }.orEmpty()
                    add(
                        Item(
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
                            messageType = o.optString("messageType").ifBlank { "text" },
                            messagePayload = payload,
                            localMediaPath = o.optString("localMediaPath").takeIf { it.isNotBlank() && it != "null" },
                            localMediaMimeType = o.optString("localMediaMimeType").takeIf { it.isNotBlank() && it != "null" },
                            marketplaceListingId = o.optString("marketplaceListingId").takeIf { it.isNotBlank() && it != "null" },
                            sentMessageId = o.optString("sentMessageId").takeIf { it.isNotBlank() && it != "null" }
                        )
                    )
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
                put("localMediaPath", item.localMediaPath ?: JSONObject.NULL); put("localMediaMimeType", item.localMediaMimeType ?: JSONObject.NULL)
                put("marketplaceListingId", item.marketplaceListingId ?: JSONObject.NULL)
                put("sentMessageId", item.sentMessageId ?: JSONObject.NULL)
            })
        }
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
