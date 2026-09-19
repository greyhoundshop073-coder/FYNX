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

    suspend fun createPost(
        context: Context,
        text: String,
        visibility: FynxPostVisibility,
        uris: List<Uri>
    ): Result<String> = runCatching {
        val selected = uris.distinct().take(MAX_MEDIA)
        val caption = text.trim().take(4000)
        if (caption.isBlank() && selected.isEmpty()) {
            throw IllegalArgumentException("Add a caption or at least one media item.")
        }

        val postId = if (selected.isEmpty()) {
            val raw = FynxBackendClient.postJson(
                context,
                "/api/social/posts",
                JSONObject().apply {
                    put("text", caption)
                    put("visibility", visibility.name)
                    put("mediaId", JSONObject.NULL)
                    put("mediaType", JSONObject.NULL)
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
                val mime = context.contentResolver.getType(uri)?.lowercase()
                    ?: throw IllegalArgumentException("FYNX could not determine the selected media type.")
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
                    throw IllegalArgumentException("A selected media file is too large. Each file must be 200 MB or smaller.")
                }
                if (size > 0L) {
                    totalBytes += size
                    if (totalBytes > MAX_TOTAL_MEDIA_BYTES) {
                        throw IllegalArgumentException("The selected media is too large to publish together. Keep the total below 500 MB.")
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
                    put("mediaIds", mediaIds)
                    put("mediaTypes", mediaTypes)
                }.toString()
            ).getOrThrow()
            JSONObject(raw).optString("postId").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("FYNX created the post but the server did not return its post ID.")
        }

        FynxHomeLifecycleRefreshBus.request(context)
        postId
    }
}
