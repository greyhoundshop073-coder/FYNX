package com.fynx.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Central visual language for FYNX. Keep feature screens dependent on this
 * layer instead of defining their own colors and shapes.
 */
enum class FynxAccent(val primary: Color, val secondary: Color) {
    Blue(Color(0xFF2F8CFF), Color(0xFF22C7F2)),
    Purple(Color(0xFF7C5CFF), Color(0xFFB18CFF)),
    Cyan(Color(0xFF16C7E8), Color(0xFF57E5F7)),
    Green(Color(0xFF19B77A), Color(0xFF5DE0A8)),
    Pink(Color(0xFFE85AAD), Color(0xFFFF8FD0)),
    Black(Color(0xFFE8ECF2), Color(0xFFB9C2CF)),
    White(Color(0xFFF7F9FC), Color(0xFFDCE4EF))
}

object FynxDesign {
    val Background = Color(0xFF071326)
    val Surface = Color(0xFF0D1B2E)
    val SurfaceRaised = Color(0xFF15263D)
    val TextPrimary = Color(0xFFF5F8FF)
    val TextSecondary = Color(0xFFB9C6D8)
    val Outline = Color(0xFF31445F)
    val SelectedContainer = Color(0xFF132B49)

    val CardShape = RoundedCornerShape(16.dp)
    val LargeCardShape = RoundedCornerShape(20.dp)
    val ControlShape = RoundedCornerShape(14.dp)
}

private fun fynxTypography(): Typography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun FynxTheme(
    accent: FynxAccent = FynxAccent.Blue,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent.primary,
            onPrimary = Color.White,
            secondary = accent.secondary,
            onSecondary = Color.White,
            background = FynxDesign.Background,
            onBackground = FynxDesign.TextPrimary,
            surface = FynxDesign.Surface,
            onSurface = FynxDesign.TextPrimary,
            surfaceVariant = FynxDesign.SurfaceRaised,
            onSurfaceVariant = FynxDesign.TextSecondary,
            outline = FynxDesign.Outline
        ),
        typography = fynxTypography(),
        shapes = androidx.compose.material3.Shapes(
            small = FynxDesign.ControlShape,
            medium = FynxDesign.CardShape,
            large = FynxDesign.LargeCardShape
        ),
        content = content
    )
}
