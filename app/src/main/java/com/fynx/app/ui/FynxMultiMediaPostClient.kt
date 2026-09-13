package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** Uploads every selected asset through the existing authenticated media path, then creates one real post. */
object FynxMultiMediaPostClient {
    private const val MAX_MEDIA = 12
    private const val MAX_SINGLE_MEDIA_BYTES = 200L * 1024L * 1024L
    private const val MAX_TOTAL_MEDIA_BYTES = 500L * 1024L * 1024L

    suspend fun createPost(
        context: Context,
        text: String,
        visibility: FynxPostVisibility,
        uris: List<Uri>
    ): Result<Unit> = runCatching {
        val selected = uris.distinct().take(MAX_MEDIA)
        val mediaIds = JSONArray()
        val mediaTypes = JSONArray()
        var totalBytes = 0L

        for (uri in selected) {
            val mime = context.contentResolver.getType(uri)?.lowercase()
                ?: throw IllegalArgumentException("FYNX could not determine the selected media type.")
            val type = when {
                mime.startsWith("image/") -> "image"
                mime.startsWith("video/") -> "video"
                mime.startsWith("audio/") -> "audio"
                else -> throw IllegalArgumentException("FYNX supports images, videos and audio files only.")
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

        if (text.trim().isBlank() && selected.isEmpty()) {
            throw IllegalArgumentException("Add a caption or at least one media item.")
        }

        FynxBackendClient.postJson(
            context,
            "/api/social/posts/multi",
            JSONObject().apply {
                put("text", text.trim().take(4000))
                put("visibility", visibility.name)
                put("mediaIds", mediaIds)
                put("mediaTypes", mediaTypes)
            }.toString()
        ).getOrThrow()
        Unit
    }
}
