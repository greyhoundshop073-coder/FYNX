package com.fynx.app.ui

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier

/**
 * Chat-only media presentation metrics.
 *
 * This keeps media presentation decisions outside ConversationPanel so future
 * photo/video sizing changes do not require editing the large conversation
 * screen. The original media is not cropped by this frame.
 */
fun chatMediaPreviewModifier(): Modifier =
    Modifier
        .fillMaxWidth()
        .aspectRatio(9f / 16f)
