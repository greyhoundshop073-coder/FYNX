package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** Uploads selected assets through the existing authenticated media path, then creates one real post. */
object FynxMultiMediaPostClient {
    private const val MAX_MEDIA = 4
    private const val MAX_SINGLE_MEDIA_BYTES = 12L * 1024L * 1024L
    private const val MAX_TOTAL_MEDIA_BYTES = 48L * 1024L * 1024L

    fun mediaKind(context: Context, uri: Uri): String {
        val mime = detectMimeType(context, uri)
        return when {
            mime.startsWith("image/") -> "image"
            mime.startsWith("video/") -> "video"
            mime.startsWith("audio/") -> "audio"
            else -> "unknown"
        }
    }

    suspend fun createPost(
        context: Context,
        text: String,
        visibility: FynxPostVisibility,
        uris: List<Uri>,
        selectedAudienceUserIds: List<String> = emptyList(),
        textBackground: FynxPostTextBackground? = null,
        location: String? = null,
        music: FynxSelectedMusic? = null,
        catalogueMusic: FynxMusicCatalogueTrack? = null,
        feelingActivity: FynxFeelingActivityOption? = null
    ): Result<String> = runCatching {
        val selected = uris.distinct().take(MAX_MEDIA)
        val caption = text.trim().take(4000)
        if (caption.isBlank() && selected.isEmpty()) {
            throw IllegalArgumentException("Add a caption or at least one media item.")
        }

        require(music == null || catalogueMusic == null) { "Choose either a FYNX catalogue track or a local music file." }
        var musicMediaId: Long? = catalogueMusic?.mediaId
        if (music != null) {
            val mime = detectMimeType(context, music.uri)
            require(mime.startsWith("audio/")) { "The selected music track is not a valid audio file." }
            val size = runCatching { context.contentResolver.openAssetFileDescriptor(music.uri, "r")?.use { it.length } ?: -1L }.getOrDefault(-1L)
            require(size != 0L && size <= MAX_SINGLE_MEDIA_BYTES) { "The music file must be 12 MB or smaller." }
            musicMediaId = FynxProductionMessaging.uploadMedia(context, music.uri, mime).getOrThrow().id.toLong()
        }

        val postId = if (selected.isEmpty()) {
            val raw = FynxBackendClient.postJson(
                context,
                "/api/social/posts",
                JSONObject().apply {
                    put("text", caption)
                    put("visibility", visibility.name)
                    put("audienceUserIds", JSONArray(selectedAudienceUserIds.distinct().take(100)))
                    put("textBackground", textBackground?.key ?: "")
                    put("location", location?.trim()?.take(160) ?: JSONObject.NULL)
                    put("mediaId", JSONObject.NULL)
                    put("mediaType", JSONObject.NULL)
                    put("musicMediaId", musicMediaId ?: JSONObject.NULL)
                    put("musicTitle", music?.title?.trim()?.take(120) ?: JSONObject.NULL)
                    put("musicArtist", music?.artist?.trim()?.take(120) ?: JSONObject.NULL)
                    put("musicDurationMs", music?.durationMs?.coerceAtLeast(0L) ?: JSONObject.NULL)
                    put("feelingActivityType", feelingActivity?.type ?: JSONObject.NULL)
                    put("feelingActivity", feelingActivity?.label ?: JSONObject.NULL)
                }.toString()
            ).getOrThrow()
            JSONObject(raw).optString("postId").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("FYNX created the post but the server did not return its post ID.")
        } else {
            val mediaIds = JSONArray()
            val mediaTypes = JSONArray()
            var totalBytes = 0L
            var hasAudio = false

            for ((index, uri) in selected.withIndex()) {
                val mime = detectMimeType(context, uri)
                val type = when {
                    mime.startsWith("image/") -> "image"
                    mime.startsWith("video/") -> "video"
                    mime.startsWith("audio/") -> "audio"
                    else -> throw IllegalArgumentException("FYNX supports images, videos and audio files only.")
                }
                if (type == "audio") hasAudio = true
                if (hasAudio && (selected.size != 1 || index != 0)) {
                    throw IllegalArgumentException("Voice posts use one audio recording. Use photos/videos for a multi-media post.")
                }

                val size = runCatching {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor -> descriptor.length }
                        ?: -1L
                }.getOrDefault(-1L)

                if (size == 0L) {
                    throw IllegalArgumentException("One of the selected media files is empty or unavailable.")
                }
                if (size > MAX_SINGLE_MEDIA_BYTES) {
                    throw IllegalArgumentException("A selected media file is too large. Each file must be 12 MB or smaller.")
                }
                if (size > 0L) {
                    totalBytes += size
                    if (totalBytes > MAX_TOTAL_MEDIA_BYTES) {
                        throw IllegalArgumentException("The selected media is too large to publish together. Keep the total at or below 48 MB.")
                    }
                }

                val uploaded = FynxProductionMessaging.uploadMedia(context, uri, mime).getOrThrow()
                mediaIds.put(uploaded.id)
                mediaTypes.put(type)
            }

            val raw = FynxBackendClient.postJson(
                context,
                "/api/social/posts/multi",
                JSONObject().apply {
                    put("text", caption)
                    put("visibility", visibility.name)
                    put("audienceUserIds", JSONArray(selectedAudienceUserIds.distinct().take(100)))
                    put("textBackground", textBackground?.key ?: "")
                    put("location", location?.trim()?.take(160) ?: JSONObject.NULL)
                    put("mediaIds", mediaIds)
                    put("mediaTypes", mediaTypes)
                    put("musicMediaId", musicMediaId ?: JSONObject.NULL)
                    put("musicTitle", music?.title?.trim()?.take(120) ?: JSONObject.NULL)
                    put("musicArtist", music?.artist?.trim()?.take(120) ?: JSONObject.NULL)
                    put("musicDurationMs", music?.durationMs?.coerceAtLeast(0L) ?: JSONObject.NULL)
                    put("feelingActivityType", feelingActivity?.type ?: JSONObject.NULL)
                    put("feelingActivity", feelingActivity?.label ?: JSONObject.NULL)
                }.toString()
            ).getOrThrow()
            JSONObject(raw).optString("postId").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("FYNX created the post but the server did not return its post ID.")
        }

        FynxHomeLifecycleRefreshBus.request(context)
        postId
    }

    private fun detectMimeType(context: Context, uri: Uri): String {
        val resolverType = context.contentResolver.getType(uri)?.trim()?.lowercase()
        if (!resolverType.isNullOrBlank()) return resolverType
        return when (uri.scheme?.lowercase()) {
            "file" -> {
                val path = uri.path
                    ?: throw IllegalArgumentException("FYNX could not determine the selected media type.")
                when (path.substringAfterLast('.', "").lowercase()) {
                    "m4a", "aac" -> "audio/mp4"
                    "mp4" -> "video/mp4"
                    "mp3" -> "audio/mpeg"
                    "wav" -> "audio/wav"
                    "3gp" -> "audio/3gpp"
                    "webm" -> "video/webm"
                    "mov" -> "video/quicktime"
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    else -> throw IllegalArgumentException("FYNX could not determine the selected media type.")
                }
            }
            else -> throw IllegalArgumentException("FYNX could not determine the selected media type.")
        }
    }
}
