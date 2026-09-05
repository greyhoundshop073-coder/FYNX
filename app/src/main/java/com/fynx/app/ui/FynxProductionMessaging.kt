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
import java.net.HttpURLConnection
import java.net.URL

/** Production messaging boundary. The server is the source of truth for chat state. */
object FynxProductionMessaging {
    private const val MAX_MEDIA_BYTES = 12 * 1024 * 1024
    private const val MAX_IMAGE_DIMENSION = 1600
    private const val IMAGE_RECOMPRESS_THRESHOLD = 2 * 1024 * 1024
    private const val IMAGE_QUALITY = 85

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
                        "m4a", "mp4", "aac" -> "audio/mp4"
                        "mp3" -> "audio/mpeg"
                        "wav" -> "audio/wav"
                        else -> "application/octet-stream"
                    }
                    else -> "application/octet-stream"
                }
            require(detectedMimeType.startsWith("image/") || detectedMimeType.startsWith("video/") || detectedMimeType.startsWith("audio/")) { "Unsupported media type." }

            val prepared = if (detectedMimeType.startsWith("image/")) {
                prepareImageUpload(context, uri, detectedMimeType)
            } else {
                readMediaBytes(context, uri) to detectedMimeType
            }
            val bytes = prepared.first
            val effectiveMimeType = prepared.second
            require(bytes.isNotEmpty()) { "The selected media is empty." }
            require(bytes.size <= MAX_MEDIA_BYTES) { "Media is too large. Maximum size is 12 MB." }

            val body = JSONObject().apply {
                put("mimeType", effectiveMimeType)
                put("dataBase64", Base64.encodeToString(bytes, Base64.NO_WRAP))
            }
            val raw = FynxBackendClient.postJson(context, "/api/media", body.toString()).getOrThrow()
            val item = JSONObject(raw).getJSONObject("media")
            Result.success(RemoteMedia(item.getString("id"), item.getString("mimeType"), item.optInt("byteSize", bytes.size)))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Throwable) { Result.failure(error) }
    }

    private fun readMediaBytes(context: Context, uri: Uri): ByteArray {
        val input = if (uri.scheme.equals("file", true)) uri.path?.let { File(it).inputStream() } else context.contentResolver.openInputStream(uri)
        return input?.use { stream ->
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
        } ?: throw IllegalArgumentException("Unable to read the selected media.")
    }

    private fun prepareImageUpload(context: Context, uri: Uri, mimeType: String): Pair<ByteArray, String> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openMediaInput(context, uri).use { input -> BitmapFactory.decodeStream(input, null, bounds) }
        val width = bounds.outWidth
        val height = bounds.outHeight
        require(width > 0 && height > 0) { "Unable to read the selected image." }

        val original = readMediaBytes(context, uri)
        val needsResize = width > MAX_IMAGE_DIMENSION || height > MAX_IMAGE_DIMENSION
        if (!needsResize && original.size <= IMAGE_RECOMPRESS_THRESHOLD) return original to mimeType

        var sample = 1
        while (width / sample > MAX_IMAGE_DIMENSION * 2 || height / sample > MAX_IMAGE_DIMENSION * 2) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
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
            } finally {
                if (!outputBitmap.isRecycled) outputBitmap.recycle()
            }
        } catch (error: Throwable) {
            if (!bitmap.isRecycled) bitmap.recycle()
            if (original.size <= MAX_MEDIA_BYTES) original to mimeType else throw error
        }
    }

    private fun openMediaInput(context: Context, uri: Uri): java.io.InputStream =
        if (uri.scheme.equals("file", true)) uri.path?.let { File(it).inputStream() }
            ?: throw IllegalArgumentException("Unable to open the selected media.")
        else context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Unable to open the selected media.")

    private fun cacheRemoteMedia(context: Context, media: RemoteMedia, authToken: String): File {
        val dir = File(context.cacheDir, "fynx_media").apply { mkdirs() }
        val extension = when (media.mimeType.lowercase()) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "video/mp4" -> "mp4"
            "audio/mp4" -> "m4a"
            "audio/mpeg" -> "mp3"
            "audio/wav" -> "wav"
            else -> "bin"
        }
        val file = File(dir, "${media.id}.$extension")
        if (file.exists() && file.length() > 0) return file
        val connection = (URL(media.id).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $authToken")
            setRequestProperty("Connection", "keep-alive")
        }
        try {
            require(connection.responseCode in 200..299) { "Media download failed: HTTP ${connection.responseCode}" }
            val temp = File(dir, "${media.id}.$extension.part")
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
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
            check(temp.renameTo(file)) { "Unable to cache remote media." }
            return file
        } finally { connection.disconnect() }
    }

    suspend fun sendText(context: Context, username: String, text: String, replyToId: String? = null): Result<RemoteMessage> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply { put("recipientUsername", username); put("text", text); if (replyToId != null) put("replyToId", replyToId) }
            fromJson(JSONObject(FynxBackendClient.postJson(context, "/api/messages", body.toString()).getOrThrow()).getJSONObject("message"))
        }
    }

    suspend fun editMessage(context: Context, messageId: String, text: String): Result<RemoteMessage> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().put("text", text)
            fromJson(JSONObject(FynxBackendClient.patchJson(context, "/api/messages/${encodePathSegment(messageId)}", body.toString()).getOrThrow()).getJSONObject("message"))
        }
    }

    suspend fun deleteMessage(context: Context, messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        FynxBackendClient.delete(context, "/api/messages/${encodePathSegment(messageId)}")
    }

    suspend fun markRead(context: Context, username: String): Result<Unit> = withContext(Dispatchers.IO) {
        FynxBackendClient.postJson(context, "/api/messages/${encodePathSegment(username)}/read", "{}")
    }

    fun toChatMessage(message: RemoteMessage): ChatMessage = ChatMessage(
        id = message.id,
        senderId = message.senderId,
        senderUsername = message.senderUsername,
        senderDisplayName = message.senderDisplayName,
        recipientId = message.recipientId,
        recipientUsername = message.recipientUsername,
        recipientDisplayName = message.recipientDisplayName,
        text = message.text,
        timestamp = message.timestamp,
        delivered = message.delivered,
        read = message.read,
        edited = message.edited,
        deleted = message.deleted,
        replyToId = message.replyToId,
        mediaId = message.mediaId,
        mediaType = message.mediaType,
        mediaUrl = message.mediaUrl,
        voiceDurationMs = message.voiceDurationMs
    )

    private fun fromJson(item: JSONObject): RemoteMessage = RemoteMessage(
        id = item.getString("id"),
        senderId = item.getString("senderId"),
        senderUsername = item.optString("senderUsername").ifBlank { null },
        senderDisplayName = item.optString("senderDisplayName").ifBlank { null },
        recipientId = item.getString("recipientId"),
        recipientUsername = item.optString("recipientUsername").ifBlank { null },
        recipientDisplayName = item.optString("recipientDisplayName").ifBlank { null },
        text = item.optString("text"),
        timestamp = item.optLong("timestamp"),
        delivered = item.optBoolean("delivered"),
        read = item.optBoolean("read"),
        edited = item.optBoolean("edited"),
        deleted = item.optBoolean("deleted"),
        replyToId = item.optString("replyToId").ifBlank { null },
        mediaId = item.optString("mediaId").ifBlank { null },
        mediaType = item.optString("mediaType").ifBlank { null },
        mediaUrl = item.optString("mediaUrl").ifBlank { null },
        voiceDurationMs = item.optLong("voiceDurationMs")
    )

    private fun encodePathSegment(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
