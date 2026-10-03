package com.fynx.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/**
 * Chat-only media preview surface.
 *
 * Keeps the 9:16 presentation decision outside ConversationPanel while leaving
 * FynxRemoteMedia responsible for loading/caching/decoding the original media.
 */
@Composable
fun ChatMediaPreview(
    mediaUrl: String,
    type: String,
    modifier: Modifier = Modifier,
    loopVideo: Boolean = true,
    onVideoCompleted: (() -> Unit)? = null,
    onMediaClick: (() -> Unit)? = null,
    playbackActive: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(onMediaClick?.let { Modifier.clickable(onClick = it) } ?: Modifier),
    ) {
        FynxRemoteMedia(
            mediaUrl = mediaUrl,
            type = type,
            modifier = chatMediaPreviewModifier(),
            loopVideo = loopVideo,
            onVideoCompleted = onVideoCompleted,
            contentScale = ContentScale.Fit,
            rounded = true,
            autoPlay = true,
            playbackActive = playbackActive,
        )
    }
}
