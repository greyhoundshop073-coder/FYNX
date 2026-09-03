package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** Production messaging boundary. The server is the source of truth for chat state. */
object FynxProductionMessaging {
    private const val MAX_MEDIA_BYTES = 12 * 1024 * 1024

    data class RemoteMedia(val id: String, val mimeType: String, val byteSize: Int)

    data class RemoteMessage(
        val id: String,
        val senderId: String,
        val recipientId: String,
        val text: String,
        val timestamp: Long,
        val delivered: Boolean,
        val read: Boolean,
        val edited: Boolean,
        val deleted: Boolean,
        val replyToId: String?,
        val mediaId: String? = null,
        val mediaType: String? = null,
        val mediaUrl: String? = null,
        val voiceDurationMs: Long = 0L
    )

    suspend fun history(context: Context, username: String): Result<List<RemoteMessage>> =
        FynxBackendClient.get(context, "/api/messages/${encodePathSegment(username)}")
            .mapCatching { raw ->
                val messages = JSONObject(raw).optJSONArray("messages") ?: JSONArray()
                buildList { for (index in 0 until messages.length()) add(fromJson(messages.getJSONObject(index))) }
            }

    suspend fun uploadMedia(context: Context, uri: Uri, mimeTypeOverride: String? = null): Result<RemoteMedia> = runCatching {
        val mimeType = mimeTypeOverride?.trim()?.lowercase()
            ?: context.contentResolver.getType(uri)?.trim()?.lowercase()
            ?: "application/octet-stream"
        require(mimeType.startsWith("image/") || mimeType.startsWith("video/") || mimeType.startsWith("audio/")) { "Unsupported media type." }
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                require(total <= MAX_MEDIA_BYTES) { "Media is too large. Maximum size is 12 MB." }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: throw IllegalArgumentException("Unable to read the selected media.")
        require(bytes.isNotEmpty()) { "The selected media is empty." }
        val body = JSONObject().apply {
            put("mimeType", mimeType)
            put("dataBase64", Base64.encodeToString(bytes, Base64.NO_WRAP))
        }
        val raw = FynxBackendClient.postJson(context, "/api/media", body.toString()).getOrThrow()
        val item = JSONObject(raw).getJSONObject("media")
        RemoteMedia(item.getString("id"), item.getString("mimeType"), item.optInt("byteSize", bytes.size))
    }

    suspend fun sendText(
        context: Context,
        recipientUsername: String,
        text: String,
        replyToId: String? = null,
        mediaId: String? = null,
        mediaType: String? = null,
        voiceDurationMs: Long = 0L
    ): Result<RemoteMessage> {
        val normalizedRecipient = recipientUsername.trim().removePrefix("@").lowercase()
        val currentUsername = (FynxAuthStore.load(context).username ?: "").trim().removePrefix("@").lowercase()
        if (normalizedRecipient.isBlank()) return Result.failure(IllegalArgumentException("A recipient is required."))
        if (currentUsername.isNotBlank() && normalizedRecipient == currentUsername) return Result.failure(IllegalArgumentException("You cannot send a message to your own account."))
        val cleanText = text.trim()
        if (cleanText.isBlank() && mediaId == null) return Result.failure(IllegalArgumentException("Message content is required."))
        val body = JSONObject().apply {
            put("recipientUsername", normalizedRecipient)
            put("text", cleanText)
            put("replyToId", replyToId?.toLongOrNull() ?: JSONObject.NULL)
            put("mediaId", mediaId?.toLongOrNull() ?: JSONObject.NULL)
            put("mediaType", mediaType ?: JSONObject.NULL)
            put("voiceDurationMs", voiceDurationMs)
        }
        return FynxBackendClient.postJson(context, "/api/messages", body.toString()).mapCatching { raw -> fromJson(JSONObject(raw).getJSONObject("message")) }
    }

    suspend fun markRead(context: Context, messageIds: List<String>): Result<Int> {
        val body = JSONObject().apply { put("messageIds", JSONArray(messageIds.mapNotNull { it.toLongOrNull() })) }
        return FynxBackendClient.postJson(context, "/api/messages/read", body.toString()).mapCatching { raw -> JSONObject(raw).optInt("updated", 0) }
    }

    fun toChatMessage(message: RemoteMessage, currentUserId: String): ChatMessage = ChatMessage(
        text = if (message.deleted) "Message deleted" else message.text,
        fromMe = message.senderId == currentUserId,
        id = message.id,
        timestamp = message.timestamp,
        delivered = message.delivered,
        read = message.read,
        replyToId = message.replyToId,
        edited = message.edited,
        attachmentUri = message.mediaUrl,
        attachmentType = message.mediaType,
        voiceUri = if (message.mediaType == "audio") message.mediaUrl else null,
        voiceDurationMs = message.voiceDurationMs
    )

    fun fromJson(item: JSONObject): RemoteMessage = RemoteMessage(
        id = item.optString("id"),
        senderId = item.optString("sender_id", item.optString("senderId")),
        recipientId = item.optString("recipient_id", item.optString("recipientId")),
        text = item.optString("text"),
        timestamp = item.optDouble("timestamp", 0.0).toLong(),
        delivered = item.optBoolean("delivered", false),
        read = item.optBoolean("read", false),
        edited = item.optBoolean("edited", false),
        deleted = item.optBoolean("deleted", false),
        replyToId = if (item.isNull("reply_to_id") && item.isNull("replyToId")) null else item.optString("reply_to_id", item.optString("replyToId")).takeIf { it.isNotBlank() },
        mediaId = if (item.isNull("media_id") && item.isNull("mediaId")) null else item.optString("media_id", item.optString("mediaId")).takeIf { it.isNotBlank() },
        mediaType = item.optString("media_type", item.optString("mediaType")).takeIf { it.isNotBlank() },
        mediaUrl = item.optString("mediaUrl").takeIf { it.isNotBlank() },
        voiceDurationMs = item.optLong("voiceDurationMs", 0L)
    )

    private fun encodePathSegment(value: String): String = java.net.URLEncoder.encode(value.trim().removePrefix("@"), "UTF-8")
}
