package com.fynx.app.ui

import android.net.Uri

/**
 * Legacy compatibility type retained for existing call sites.
 * Local music selection/upload is intentionally disabled. Posts must use
 * FynxMusicCatalogueClient and a published FYNX catalogue track.
 */
@Deprecated("Use FynxMusicCatalogueClient for published FYNX music.")
data class FynxSelectedMusic(
    val uri: Uri,
    val title: String,
    val artist: String,
    val durationMs: Long
)

@Deprecated("Local music uploads are disabled in FYNX.")
object FynxMusicLibraryClient {
    fun readSelection(context: android.content.Context, uri: Uri): Result<FynxSelectedMusic> =
        Result.failure(IllegalStateException("Local music uploads are disabled. Choose music from the FYNX catalogue."))
}
