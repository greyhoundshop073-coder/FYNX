package com.fynx.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/**
 * Chat-only media presentation surface.
 *
 * ConversationPanel remains the coordinator, while this component owns the
 * presentation of photo/video attachments. FynxRemoteMedia remains the shared
 * loader/cache/decoder and is intentionally not modified.
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
            .aspectRatio(9f / 16f)
            .then(onMediaClick?.let { Modifier.clickable(onClick = it) } ?: Modifier),
    ) {
        FynxRemoteMedia(
            mediaUrl = mediaUrl,
            type = type,
            modifier = Modifier.fillMaxWidth(),
            loopVideo = loopVideo,
            onVideoCompleted = onVideoCompleted,
            contentScale = ContentScale.Fit,
            rounded = true,
            autoPlay = true,
            playbackActive = playbackActive,
        )
    }
}
