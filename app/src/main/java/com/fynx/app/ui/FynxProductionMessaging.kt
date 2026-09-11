package com.fynx.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/** Production messaging boundary. The server is the source of truth for chat state. */
object FynxProductionMessaging {
    private const val MAX_MEDIA_BYTES = 12 * 1024 * 1024
    private const val MAX_IMAGE_DIMENSION = 1600
    private const val IMAGE_RECOMPRESS_THRESHOLD = 2 * 1024 * 1024
    private const val IMAGE_QUALITY = 85
    private const val MAX_MESSAGE_LENGTH = 4000
    private const val MAX_VOICE_DURATION_MS = 60 * 60 * 1000L

    data class RemoteMedia(val id: String, val mimeType: String, val byteSize: Int)
    data class RemoteMessage(
        val id: String, val senderId: String, val senderUsername: String? = null, val senderDisplayName: String? = null,
        val recipientId: String, val recipientUsername: String? = null, val recipientDisplayName: String? = null,
        val text: String, val timestamp: Long, val delivered: Boolean, val read: Boolean, val edited: Boolean, val deleted: Boolean,
        val replyToId: String?, val mediaId: String? = null, val mediaType: String? = null, val mediaUrl: String? = null, val voiceDurationMs: Long = 0L
    )

    suspend fun history(context: Context, username: String): Result<List<RemoteMessage>> =
        FynxBackendClient.get(context, "/api/messages/${encodePathSegment(username)}").mapCatching { raw ->
            val messages = JSONObject(raw).optJSONArray("messages") ?: JSONArray()
            buildList { for (index in 0 until messages.length()) add(fromJson(messages.getJSONObject(index))) }
        }

    suspend fun uploadMedia(context: Context, uri: Uri, mimeTypeOverride: String? = null): Result<RemoteMedia> = withContext(Dispatchers.IO) {
        try {
            val detectedMimeType = mimeTypeOverride?.trim()?.lowercase()
                ?: context.contentResolver.getType(uri)?.trim()?.lowercase()
                ?: when (uri.scheme?.lowercase()) {
                    "file" -> when (uri.path?.substringAfterLast('.', "")?.lowercase()) {
                        "m4a", "mp4", "aac" -> "audio/mp4"; "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"; else -> "application/octet-stream"
                    }
                    else -> "application/octet-stream"
                }
            require(detectedMimeType.startsWith("image/") || detectedMimeType.startsWith("video/") || detectedMimeType.startsWith("audio/")) { "Unsupported media type." }
            val prepared: Pair<ByteArray, String> = if (detectedMimeType.startsWith("image/")) prepareImageUpload(context, uri, detectedMimeType) else readMediaBytes(context, uri) to detectedMimeType
            val bytes = prepared.first
            val effectiveMimeType = prepared.second
            require(bytes.isNotEmpty()) { "The selected media is empty." }
            require(bytes.size <= MAX_MEDIA_BYTES) { "Media is too large. Maximum size is 12 MB." }
            val body = JSONObject().apply { put("mimeType", effectiveMimeType); put("dataBase64", Base64.encodeToString(bytes, Base64.NO_WRAP)) }
            val raw = FynxBackendClient.postJson(context, "/api/media", body.toString()).getOrThrow()
            val item = JSONObject(raw).getJSONObject("media")
            Result.success(RemoteMedia(item.getString("id"), item.getString("mimeType"), item.optInt("byteSize", bytes.size)))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Throwable) { Result.failure(error) }
    }

    private fun readMediaBytes(context: Context, uri: Uri): ByteArray {
        val input = openMediaInput(context, uri)
        return input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            var total = 0
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                total += read
                require(total <= MAX_MEDIA_BYTES) { "Media is too large. Maximum size is 12 MB." }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    private fun prepareImageUpload(context: Context, uri: Uri, mimeType: String): Pair<ByteArray, String> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openMediaInput(context, uri).use { input -> BitmapFactory.decodeStream(input, null, bounds) }
        val width = bounds.outWidth
        val height = bounds.outHeight
        require(width > 0 && height > 0) { "Unable to read the selected image." }
        val knownLength = runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L }.getOrDefault(-1L)
        val needsResize = width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
        if (!needsResize && knownLength in 1..IMAGE_RECOMPRESS_THRESHOLD.toLong()) return readMediaBytes(context, uri) to mimeType
        var sample = 1
        while (width / sample > MAX_IMAGE_DIMENSION * 2 || height / sample > MAX_IMAGE_DIMENSION * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }
        val bitmap = openMediaInput(context, uri).use { input -> BitmapFactory.decodeStream(input, null, options) }
            ?: throw IllegalArgumentException("Unable to decode the selected image.")
        return try {
            val scale = maxOf(bitmap.width, bitmap.height).toFloat() / MAX_IMAGE_DIMENSION
            val outputBitmap = if (scale > 1f) {
                val targetWidth = (bitmap.width / scale).toInt().coerceAtLeast(1)
                val targetHeight = (bitmap.height / scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true).also { if (it !== bitmap) bitmap.recycle() }
            } else bitmap
            try {
                val output = ByteArrayOutputStream()
                require(outputBitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, output)) { "Unable to optimize the selected image." }
                output.toByteArray() to "image/jpeg"
            } finally { if (!outputBitmap.isRecycled) outputBitmap.recycle() }
        } catch (error: Throwable) {
            if (!bitmap.isRecycled) bitmap.recycle()
            throw error
        }
    }

    private fun openMediaInput(context: Context, uri: Uri): java.io.InputStream =
        if (uri.scheme.equals("file", true)) uri.path?.let { File(it).inputStream() } ?: throw IllegalArgumentException("Unable to open the selected media.")
        else context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Unable to open the selected media.")

    suspend fun cacheRemoteMedia(context: Context, mediaId: String, mediaUrl: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val safeId = mediaId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            require(safeId.isNotBlank()) { "Invalid media id." }
            val directory = File(context.cacheDir, "fynx_media")
            if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("Unable to create media cache")
            val existing = directory.listFiles()?.firstOrNull { it.name.startsWith("${safeId}.") && it.length() > 0L }
            if (existing != null) return@withContext Result.success(Uri.fromFile(existing))
            val absoluteUrl = if (mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://")) mediaUrl else FynxBackendClient.baseUrl(context).trimEnd('/') + "/" + mediaUrl.trimStart('/')
            val target = File(directory, "$safeId.bin")
            val result = FynxBackendClient.downloadToFile(context, absoluteUrl, target, MAX_MEDIA_BYTES.toLong())
            result.map { Uri.fromFile(target) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Throwable) { Result.failure(error) }
    }

    suspend fun sendText(context: Context, recipientUsername: String, text: String, replyToId: String? = null, mediaId: String? = null, mediaType: String? = null, voiceDurationMs: Long = 0L): Result<RemoteMessage> {
        val normalizedRecipient = recipientUsername.trim().removePrefix("@").lowercase()
        val currentUsername = (FynxAuthStore.load(context).username ?: "").trim().removePrefix("@").lowercase()
        if (normalizedRecipient.isBlank()) return Result.failure(IllegalArgumentException("A recipient is required."))
        if (currentUsername.isNotBlank() && normalizedRecipient == currentUsername) return Result.failure(IllegalArgumentException("You cannot send a message to your own account."))
        val cleanText = text.trim()
        if (cleanText.length > MAX_MESSAGE_LENGTH) return Result.failure(IllegalArgumentException("Message is too long. Maximum is 4000 characters."))
        if (cleanText.isBlank() && mediaId == null) return Result.failure(IllegalArgumentException("Message content is required."))
        if (mediaType != null && mediaType !in setOf("image", "video", "audio")) return Result.failure(IllegalArgumentException("Unsupported message media type."))
        if (mediaId == null && mediaType != null) return Result.failure(IllegalArgumentException("Message media is incomplete."))
        if (voiceDurationMs !in 0L..MAX_VOICE_DURATION_MS) return Result.failure(IllegalArgumentException("Voice message duration is invalid."))
        val body = JSONObject().apply { put("recipientUsername", normalizedRecipient); put("text", cleanText); put("replyToId", replyToId?.toLongOrNull() ?: JSONObject.NULL); put("mediaId", mediaId?.toLongOrNull() ?: JSONObject.NULL); put("mediaType", mediaType ?: JSONObject.NULL); put("voiceDurationMs", voiceDurationMs) }
        val result = FynxBackendClient.postJson(context, "/api/messages", body.toString()).mapCatching { raw -> fromJson(JSONObject(raw).getJSONObject("message")) }
        if (result.isSuccess) return result
        if (isAmbiguousTransportFailure(result.exceptionOrNull())) {
            val recovered = history(context, normalizedRecipient).getOrNull()?.asReconciliationCandidate(
                currentUsername = currentUsername,
                text = cleanText,
                replyToId = replyToId,
                mediaId = mediaId,
                mediaType = mediaType
            )
            if (recovered != null) return Result.success(recovered)
        }
        return result
    }

    private fun isAmbiguousTransportFailure(error: Throwable?): Boolean {
        val message = error?.message.orEmpty().lowercase()
        return message.contains("timeout") || message.contains("timed out") || message.contains("connection") ||
            message.contains("network") || message.contains("socket") || message.contains("http 408") ||
            message.contains("http 429") || message.contains("http 5") || message.contains("503") || message.contains("502")
    }

    private fun List<RemoteMessage>.asReconciliationCandidate(currentUsername: String, text: String, replyToId: String?, mediaId: String?, mediaType: String?): RemoteMessage? {
        val now = System.currentTimeMillis()
        return asSequence()
            .filter { it.senderUsername?.trim()?.removePrefix("@").orEmpty().lowercase() == currentUsername }
            .filter { it.text == text && it.replyToId == replyToId && it.mediaId == mediaId && it.mediaType == mediaType }
            .filter { it.timestamp == 0L || kotlin.math.abs(now - it.timestamp) <= 120_000L }
            .maxByOrNull { it.timestamp }
    }

    suspend fun editMessage(context: Context, messageId: String, text: String): Result<RemoteMessage> {
        val id = messageId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid message id")); val cleanText = text.trim()
        if (cleanText.isBlank() || cleanText.length > MAX_MESSAGE_LENGTH) return Result.failure(IllegalArgumentException("Message text is invalid."))
        return FynxBackendClient.patchJson(context, "/api/messages/$id", JSONObject().put("text", cleanText).toString()).mapCatching { raw -> fromJson(JSONObject(raw).getJSONObject("message")) }
    }

    suspend fun deleteMessage(context: Context, messageId: String): Result<Unit> {
        val id = messageId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid message id"))
        return FynxBackendClient.delete(context, "/api/messages/$id").map { Unit }
    }

    suspend fun markRead(context: Context, messageIds: List<String>): Result<Int> = withContext(Dispatchers.IO) {
        var updated = 0; var failure: Throwable? = null
        for (id in messageIds.mapNotNull { it.toLongOrNull() }.distinct().take(100)) {
            val result = FynxBackendClient.postJson(context, "/api/messages/$id/read", "{}")
            if (result.isSuccess) updated++ else if (failure == null) failure = result.exceptionOrNull()
        }
        if (failure != null && updated == 0) Result.failure(failure!!) else Result.success(updated)
    }

    fun toChatMessage(message: RemoteMessage, currentUserId: String): ChatMessage = ChatMessage(
        text = if (message.deleted) "Message deleted" else message.text, fromMe = message.senderId == currentUserId, id = message.id,
        timestamp = message.timestamp, delivered = message.delivered, read = message.read, replyToId = message.replyToId, edited = message.edited,
        attachmentUri = message.mediaUrl, attachmentType = message.mediaType, voiceUri = if (message.mediaType == "audio") message.mediaUrl else null,
        voiceDurationMs = message.voiceDurationMs, mediaId = message.mediaId, senderName = message.senderDisplayName, senderUsername = message.senderUsername
    )

    fun fromJson(item: JSONObject): RemoteMessage = RemoteMessage(
        id = item.optString("id"), senderId = item.optString("sender_id", item.optString("senderId")),
        senderUsername = item.optString("sender_username", item.optString("senderUsername")).takeIf { it.isNotBlank() }, senderDisplayName = item.optString("sender_display_name", item.optString("senderDisplayName")).takeIf { it.isNotBlank() },
        recipientId = item.optString("recipient_id", item.optString("recipientId")), recipientUsername = item.optString("recipient_username", item.optString("recipientUsername")).takeIf { it.isNotBlank() }, recipientDisplayName = item.optString("recipient_display_name", item.optString("recipientDisplayName")).takeIf { it.isNotBlank() },
        text = item.optString("text"), timestamp = item.optDouble("timestamp", 0.0).toLong(), delivered = item.optBoolean("delivered", false), read = item.optBoolean("read", false), edited = item.optBoolean("edited", false), deleted = item.optBoolean("deleted", false),
        replyToId = if (item.isNull("reply_to_id") && item.isNull("replyToId")) null else item.optString("reply_to_id", item.optString("replyToId")).takeIf { it.isNotBlank() },
        mediaId = if (item.isNull("media_id") && item.isNull("mediaId")) null else item.optString("media_id", item.optString("mediaId")).takeIf { it.isNotBlank() }, mediaType = item.optString("media_type", item.optString("mediaType")).takeIf { it.isNotBlank() },
        mediaUrl = item.optString("mediaUrl").takeIf { it.isNotBlank() }, voiceDurationMs = item.optLong("voiceDurationMs", 0L)
    )

    private fun encodePathSegment(value: String): String = java.net.URLEncoder.encode(value.trim().removePrefix("@"), "UTF-8")
}
