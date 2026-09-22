package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** FYNX AI surface uses the direct light-blue visual treatment requested for the AI chat. */
@Composable
fun FynxAiAssistantBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val aiScheme = darkColorScheme(
        primary = Color(0xFFE3EFFD),
        onPrimary = Color(0xFF17385F),
        primaryContainer = Color(0xFF5B83AE),
        onPrimaryContainer = Color(0xFFF7FAFF),
        secondary = Color(0xFFBBD2E9),
        onSecondary = Color(0xFF193858),
        secondaryContainer = Color(0xFF4C7199),
        onSecondaryContainer = Color(0xFFF4F8FD),
        background = Color(0xFF244C78),
        onBackground = Color(0xFFF4F8FD),
        surface = Color(0xFF416A94),
        onSurface = Color(0xFFF4F8FD),
        surfaceVariant = Color(0xFF4B739D),
        onSurfaceVariant = Color(0xFFD9E6F2),
        outline = Color(0xFF7898B8),
        surfaceContainerLowest = Color(0xFF244C78),
        surfaceContainerLow = Color(0xFF315B86),
        surfaceContainer = Color(0xFF3A638E),
        surfaceContainerHigh = Color(0xFF416A94),
        surfaceContainerHighest = Color(0xFF4B739D),
        surfaceDim = Color(0xFF1E4167),
        surfaceBright = Color(0xFF527DA7)
    )
    MaterialTheme(colorScheme = aiScheme) {
        Box(modifier = modifier.background(aiScheme.background)) {
            content()
        }
    }
}