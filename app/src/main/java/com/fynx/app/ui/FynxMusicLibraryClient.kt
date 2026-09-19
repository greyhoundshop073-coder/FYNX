package com.fynx.app.ui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

data class FynxSelectedMusic(
    val uri: Uri,
    val title: String,
    val artist: String,
    val durationMs: Long
)

object FynxMusicLibraryClient {
    fun readSelection(context: Context, uri: Uri): Result<FynxSelectedMusic> = runCatching {
        val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        require(mime.startsWith("audio/") || uri.toString().lowercase().matches(Regex(".*\\.(mp3|m4a|aac|wav|ogg|flac|opus)([?#].*)?$"))) {
            "Choose an audio track from your music library."
        }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim().orEmpty()
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim().orEmpty()
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            FynxSelectedMusic(
                uri = uri,
                title = title.ifBlank { uri.lastPathSegment?.substringBeforeLast('.').orEmpty().ifBlank { "Selected music" }.take(120) },
                artist = artist.ifBlank { "Unknown artist" }.take(120),
                durationMs = duration
            )
        } finally {
            retriever.release()
        }
    }
}
