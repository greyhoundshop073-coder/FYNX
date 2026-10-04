package com.fynx.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Chat-only presentation boundary for video-note messages. */
@Composable
fun ChatVideoNote(
    mediaUrl: String,
    modifier: Modifier = Modifier,
) {
    FynxRemoteMedia(
        mediaUrl = mediaUrl,
        modifier = modifier,
        contentDescription = "Video note",
        isVideo = true,
    )
}
