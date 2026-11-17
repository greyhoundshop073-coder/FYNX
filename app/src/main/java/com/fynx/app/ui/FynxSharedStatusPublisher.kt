package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import java.util.UUID

/** Publishes a Home composer item through the existing authenticated Status service. */
object FynxSharedStatusPublisher {
    suspend fun publish(
        context: Context,
        username: String,
        statusId: String,
        caption: String,
        mediaUri: Uri?,
        mediaType: String?,
        audience: FynxStatusAudience,
        music: FynxMusicCatalogueTrack?
    ): Result<Unit> = runCatching {
        val owner = FynxAuthStore.load(context).username
            ?.removePrefix("@")?.trim()?.takeIf { it.isNotBlank() }
            ?: username.removePrefix("@").trim().takeIf { it.isNotBlank() }
            ?: error("Sign in to publish a Status.")
        require(caption.length <= FYNX_STATUS_MAX_TEXT_LENGTH) {
            "Status text is limited to 700 characters. Shorten the caption before publishing to Status."
        }
        val type = when (mediaType) {
            "image" -> FynxStatusType.PHOTO
            "video", "video_note" -> FynxStatusType.VIDEO
            else -> if (music != null && mediaUri == null) FynxStatusType.MUSIC else FynxStatusType.TEXT
        }
        require(type != FynxStatusType.TEXT || caption.isNotBlank()) {
            "Add a caption or choose a photo, video or music before publishing to Status."
        }
        require(type != FynxStatusType.MUSIC || music != null) {
            "Choose a published FYNX music track before publishing to Status."
        }

        val displayName = FynxProfileRemoteClient.get(context, owner).getOrNull()
            ?.displayName?.trim()?.takeIf { it.isNotBlank() } ?: owner
        val mediaId = if (type == FynxStatusType.PHOTO || type == FynxStatusType.VIDEO) {
            val source = mediaUri ?: error("Choose one photo or video for Status.")
            val mime = context.contentResolver.getType(source)?.substringBefore(';')?.trim()?.lowercase()
                ?: if (type == FynxStatusType.PHOTO) "image/jpeg" else "video/mp4"
            val prefix = if (type == FynxStatusType.PHOTO) "image/" else "video/"
            require(mime.startsWith(prefix)) { "The selected media does not match the Status type." }
            FynxStatusClient.uploadMedia(context, source, mime).getOrThrow()
        } else null

        val now = System.currentTimeMillis()
        val status = FynxStatus(
            id = statusId.ifBlank { UUID.randomUUID().toString() },
            ownerUsername = owner,
            ownerDisplayName = displayName,
            type = type,
            contentUri = mediaId?.let { "/api/media/$it" },
            text = caption.trim().ifBlank { null },
            createdAtMillis = now,
            expiresAtMillis = now + FYNX_STATUS_EXPIRY_MS,
            textStyle = FynxStatusTextStyle(),
            privateStatus = audience != FynxStatusAudience.EVERYONE,
            audience = audience,
            musicCatalogueId = music?.id,
            musicTitle = music?.title,
            musicArtist = music?.artist,
            musicDurationMs = music?.durationMs ?: 0L
        )
        FynxStatusClient.create(context, status, mediaId).getOrThrow()
        FynxStatusStore.save(context, status)
    }
}
