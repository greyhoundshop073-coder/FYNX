package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Production messaging boundary. The server is the source of truth for chat state. */
object FynxProductionMessaging {
    private const val MAX_MEDIA_BYTES = 12 * 1024 * 1024

    data class RemoteMedia(val id: String, val mimeType: String, val byteSize: Int)

    data class RemoteMessage(
        val id: String,
        val senderId: String,
        val senderUsername: String? = null,
        val senderDisplayName: String? = null,
        val recipientId: String,
        val recipientUsername: String? = null,
        val recipientDisplayName: String? = null,
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

    suspend fun uploadMedia(context: Context, uri: Uri, mimeTypeOverride: String? = null): Result<RemoteMedia> =
        withContext(Dispatchers.IO) {
            try {
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
                Result.success(RemoteMedia(item.getString("id"), item.getString("mimeType"), item.optInt("byteSize", bytes.size)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }

    /**
     * Downloads an authenticated remote media item into the app cache so platform
     * players such as MediaPlayer/VideoView can read it without exposing a token
     * in a URL. The returned file is private to this app and may be evicted safely.
     */
    suspend fun cacheRemoteMedia(context: Context, mediaId: String, mediaUrl: String): Result<Uri> =
        withContext(Dispatchers.IO) {
            try {
                val safeId = mediaId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                require(safeId.isNotBlank()) { "Invalid media id." }
                val directory = File(context.cacheDir, "fynx_media").apply { mkdirs() }
                val existing = directory.listFiles()?.firstOrNull { it.name.startsWith("${safeId}.") && it.length() > 0L }
                if (existing != null) return@withContext Result.success(Uri.fromFile(existing))

                val absoluteUrl = if (mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://")) mediaUrl
                else FynxBackendClient.baseUrl(context).trimEnd('/') + "/" + mediaUrl.trimStart('/')
                val connection = (URL(absoluteUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 20_000
                    useCaches = false
                    setRequestProperty("Accept", "*/*")
                    FynxBackendClient.accessToken(context)?.let { setRequestProperty("Authorization", "Bearer $it") }
                }
                try {
                    val status = connection.responseCode
                    if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                        FynxBackendClient.saveAccessToken(context, null)
                        throw IllegalStateException("FYNX media session expired")
                    }
                    require(status in 200..299) { "FYNX media could not be loaded (HTTP $status)." }
                    val contentLength = connection.contentLengthLong
                    require(contentLength <= MAX_MEDIA_BYTES || contentLength < 0L) { "Remote media is too large." }
                    val extension = when (connection.contentType.orEmpty().lowercase()) {
                        "video/mp4" -> "mp4"
                        "video/webm" -> "webm"
                        "video/quicktime" -> "mov"
                        "audio/mp4", "audio/x-m4a" -> "m4a"
                        "audio/mpeg" -> "mp3"
                        "audio/aac" -> "aac"
                        "audio/wav" -> "wav"
                        "image/png" -> "png"
                        "image/webp" -> "webp"
                        "image/gif" -> "gif"
                        else -> "bin"
                    }
                    val target = File(directory, "$safeId.$extension")
                    connection.inputStream.use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var total = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                total += read
                                require(total <= MAX_MEDIA_BYTES) { "Remote media is too large." }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    require(target.length() > 0L) { "Remote media is empty." }
                    Result.success(Uri.fromFile(target))
                } finally {
                    connection.disconnect()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
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
        voiceDurationMs = message.voiceDurationMs,
        senderName = message.senderDisplayName,
        senderUsername = message.senderUsername
    )

    fun fromJson(item: JSONObject): RemoteMessage = RemoteMessage(
        id = item.optString("id"),
        senderId = item.optString("sender_id", item.optString("senderId")),
        senderUsername = item.optString("sender_username", item.optString("senderUsername")).takeIf { it.isNotBlank() },
        senderDisplayName = item.optString("sender_display_name", item.optString("senderDisplayName")).takeIf { it.isNotBlank() },
        recipientId = item.optString("recipient_id", item.optString("recipientId")),
        recipientUsername = item.optString("recipient_username", item.optString("recipientUsername")).takeIf { it.isNotBlank() },
        recipientDisplayName = item.optString("recipient_display_name", item.optString("recipientDisplayName")).takeIf { it.isNotBlank() },
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
