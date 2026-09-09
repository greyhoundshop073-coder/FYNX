package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** Uploads every selected asset through the existing authenticated media path, then creates one real post. */
object FynxMultiMediaPostClient {
    private const val MAX_MEDIA = 12

    suspend fun createPost(
        context: Context,
        text: String,
        visibility: FynxPostVisibility,
        uris: List<Uri>
    ): Result<Unit> = runCatching {
        val selected = uris.distinct().take(MAX_MEDIA)
        val mediaIds = JSONArray()
        val mediaTypes = JSONArray()

        for (uri in selected) {
            val mime = context.contentResolver.getType(uri)?.lowercase()
                ?: throw IllegalArgumentException("FYNX could not determine the selected media type.")
            val type = when {
                mime.startsWith("image/") -> "image"
                mime.startsWith("video/") -> "video"
                mime.startsWith("audio/") -> "audio"
                else -> throw IllegalArgumentException("FYNX supports images, videos and audio files only.")
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
