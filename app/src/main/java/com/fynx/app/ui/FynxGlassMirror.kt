package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared visual primitives for the FYNX Glass Mirror / water-clear design.
 *
 * Keep this surface intentionally pale and translucent: dark text remains legible,
 * while a restrained blue wash, white rim and soft highlight suggest clear glass
 * rather than a solid neon card. Use for shared controls, compact panels and
 * navigation surfaces; do not wrap scrolling content in this component.
 */
object FynxGlassMirror {
    val WaterBlue = Color(0xFFBFEAFF)
    val ClearBlue = Color(0xFFDFF5FF)
    val Reflection = Color(0xF2FFFFFF)
    val Rim = Color(0xBFFFFFFF)
    val DeepRim = Color(0x6679BDE2)
    val Ink = Color(0xFF10212B)
    val MutedInk = Color(0xFF496675)
    val Accent = Color(0xFF168AC4)
    val ShadowTint = Color(0x263D86AD)

    val glassBrush: Brush
        get() = Brush.linearGradient(
            colors = listOf(
                Color(0xF7FFFFFF),
                Color(0xE8E7F7FF),
                Color(0xD9BDEAFF),
                Color(0xEAF8FDFF)
            )
        )

    val reflectionBrush: Brush
        get() = Brush.verticalGradient(
            colors = listOf(
                Color(0xBFFFFFFF),
                Color(0x33FFFFFF),
                Color(0x0879BDE2)
            )
        )
}

/**
 * A reusable water-clear glass panel with a fine reflective rim.
 * Content is not tinted; callers should use [FynxGlassMirror.Ink] for text
 * and icons to maintain contrast on the light glass surface.
 */
@Composable
fun FynxGlassMirrorSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Surface(
        modifier = modifier
            .clip(shape)
            .border(1.dp, FynxGlassMirror.Rim, shape),
        shape = shape,
        color = Color.Transparent,
        shadowElevation = 3.dp,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .background(FynxGlassMirror.glassBrush)
                .background(FynxGlassMirror.reflectionBrush)
                .border(0.5.dp, FynxGlassMirror.DeepRim, shape),
            content = content
        )
    }
}

/** Compact, high-contrast text colors for glass controls and labels. */
object FynxGlassMirrorContent {
    val primaryText: Color get() = FynxGlassMirror.Ink
    val secondaryText: Color get() = FynxGlassMirror.MutedInk
    val interactive: Color get() = FynxGlassMirror.Accent
}
