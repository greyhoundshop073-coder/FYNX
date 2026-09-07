package com.fynx.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Central visual language for FYNX. */
enum class FynxAccent(val primary: Color, val secondary: Color) {
    Blue(Color(0xFF2F8CFF), Color(0xFF22C7F2)),
    Purple(Color(0xFF7C5CFF), Color(0xFFB18CFF)),
    Cyan(Color(0xFF009FB7), Color(0xFF24C6D8)),
    Green(Color(0xFF168A62), Color(0xFF38B887)),
    Pink(Color(0xFFD13F91), Color(0xFFF276B7)),
    Orange(Color(0xFFE66A16), Color(0xFFF49A52)),
    Red(Color(0xFFD83A4A), Color(0xFFF16B78)),
    Black(Color.Black, Color(0xFF303030)),
    White(Color.White, Color(0xFFE0E0E0))
}

object FynxDesign {
    val Background = Color(0xFF071326)
    val Surface = Color(0xFF0D1B2E)
    val SurfaceRaised = Color(0xFF15263D)
    val TextPrimary = Color(0xFFF5F8FF)
    val TextSecondary = Color(0xFFB9C6D8)
    val Outline = Color(0xFF31445F)
    val SelectedContainer = Color(0xFF132B49)

    val LightBackground = Color(0xFFF5F7FB)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceRaised = Color(0xFFEAF0F7)
    val LightTextPrimary = Color(0xFF17202A)
    val LightTextSecondary = Color(0xFF5E6B78)
    val LightOutline = Color(0xFFD2DAE5)
    val LightSelectedContainer = Color(0xFFE4EFFC)

    // True AMOLED mode: surfaces/backgrounds are pure #000000 and text is pure white.
    val AmoledBackground = Color.Black
    val AmoledSurface = Color.Black
    val AmoledSurfaceRaised = Color.Black
    val AmoledTextPrimary = Color.White
    val AmoledTextSecondary = Color(0xFFE0E0E0)
    val AmoledOutline = Color(0xFF303030)
    val AmoledSelectedContainer = Color(0xFF111111)

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
    darkMode: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val amoled = FynxPreferencesStore.loadAppearance(context) == "Black AMOLED"
    val effectiveDarkMode = amoled || darkMode
    val onAccent = if (accent.primary.luminance() > 0.5f) Color.Black else Color.White
    val scheme = if (effectiveDarkMode) {
        darkColorScheme(
            primary = accent.primary,
            onPrimary = onAccent,
            secondary = accent.secondary,
            onSecondary = if (accent.secondary.luminance() > 0.5f) Color.Black else Color.White,
            background = if (amoled) FynxDesign.AmoledBackground else FynxDesign.Background,
            onBackground = if (amoled) FynxDesign.AmoledTextPrimary else FynxDesign.TextPrimary,
            surface = if (amoled) FynxDesign.AmoledSurface else FynxDesign.Surface,
            onSurface = if (amoled) FynxDesign.AmoledTextPrimary else FynxDesign.TextPrimary,
            surfaceVariant = if (amoled) FynxDesign.AmoledSurfaceRaised else FynxDesign.SurfaceRaised,
            onSurfaceVariant = if (amoled) FynxDesign.AmoledTextSecondary else FynxDesign.TextSecondary,
            outline = if (amoled) FynxDesign.AmoledOutline else FynxDesign.Outline,
            surfaceTint = if (amoled) Color.Black else accent.primary
        )
    } else {
        lightColorScheme(
            primary = accent.primary,
            onPrimary = onAccent,
            secondary = accent.secondary,
            onSecondary = if (accent.secondary.luminance() > 0.5f) Color.Black else Color.White,
            background = FynxDesign.LightBackground,
            onBackground = FynxDesign.LightTextPrimary,
            surface = FynxDesign.LightSurface,
            onSurface = FynxDesign.LightTextPrimary,
            surfaceVariant = FynxDesign.LightSurfaceRaised,
            onSurfaceVariant = FynxDesign.LightTextSecondary,
            outline = FynxDesign.LightOutline
        )
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = fynxTypography(),
        shapes = Shapes(
            small = FynxDesign.ControlShape,
            medium = FynxDesign.CardShape,
            large = FynxDesign.LargeCardShape
        ),
        content = {
            FynxCustomizationBackground(content)
        }
    )
}
