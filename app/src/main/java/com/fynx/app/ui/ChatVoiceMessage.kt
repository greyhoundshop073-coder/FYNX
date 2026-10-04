package com.fynx.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Chat-only voice-message presentation boundary.
 * Playback/loading remains in the existing shared audio component.
 */
@Composable
fun ChatVoiceMessage(
    mediaUrl: String,
    modifier: Modifier = Modifier,
    maxWidth: Dp,
    maxDurationMs: Long = 0L,
) {
    FynxRemoteAudio(
        mediaUrl = mediaUrl,
        modifier = modifier.widthIn(max = maxWidth),
    )
}
