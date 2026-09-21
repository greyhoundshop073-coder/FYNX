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
import java.time.LocalTime

/** Central visual language for FYNX. */
enum class FynxAccent(val primary: Color, val secondary: Color) {
    Blue(Color(0xFF2F8CFF), Color(0xFF22C7F2)),
    Purple(Color(0xFF7C5CFF), Color(0xFFB18CFF)),
    Charcoal(Color(0xFF263238), Color(0xFF607D8B))
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
    val CharcoalBackground = Color(0xFF1F2428)
    val CharcoalSurface = Color(0xFF272D32)
    val CharcoalSurfaceRaised = Color(0xFF31383E)
    val CharcoalTextPrimary = Color(0xFFF2F5F7)
    val CharcoalTextSecondary = Color(0xFFB8C1C8)
    val CharcoalOutline = Color(0xFF465159)
    val CharcoalSelectedContainer = Color(0xFF37434B)
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

private fun scheduledNightModeActive(context: android.content.Context): Boolean {
    if (FynxPreferencesStore.loadNightMode(context) != "Scheduled") return false
    val start = runCatching { LocalTime.parse(FynxPreferencesStore.loadNightModeStart(context)) }.getOrDefault(LocalTime.of(22, 0))
    val end = runCatching { LocalTime.parse(FynxPreferencesStore.loadNightModeEnd(context)) }.getOrDefault(LocalTime.of(7, 0))
    val now = LocalTime.now()
    return if (start == end) true else if (start < end) now >= start && now < end else now >= start || now < end
}

@Composable
fun FynxTheme(
    accent: FynxAccent? = null,
    darkMode: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val appearance = FynxPreferencesStore.loadAppearance(context)
    val effectiveAccent = accent ?: FynxPreferencesStore.loadAccent(context)
    val charcoal = effectiveAccent == FynxAccent.Charcoal
    val amoled = appearance == "Black AMOLED"
    val scheduledNight = appearance == "System" && scheduledNightModeActive(context)
    val effectiveDarkMode = when (appearance) {
        "Light" -> false
        "Dark" -> true
        "Black AMOLED" -> true
        "System" -> darkMode || scheduledNight
        else -> darkMode || scheduledNight
    }
    val onAccent = if (effectiveAccent.primary.luminance() > 0.5f) Color.Black else Color.White
    val scheme = if (effectiveDarkMode) {
        darkColorScheme(
            primary = effectiveAccent.primary,
            onPrimary = onAccent,
            secondary = effectiveAccent.secondary,
            onSecondary = if (effectiveAccent.secondary.luminance() > 0.5f) Color.Black else Color.White,
            background = when { amoled -> FynxDesign.CharcoalBackground; charcoal -> FynxDesign.CharcoalBackground; else -> FynxDesign.Background },
            onBackground = if (charcoal || amoled) FynxDesign.CharcoalTextPrimary else FynxDesign.TextPrimary,
            surface = when { amoled -> FynxDesign.CharcoalSurface; charcoal -> FynxDesign.CharcoalSurface; else -> FynxDesign.Surface },
            onSurface = if (charcoal || amoled) FynxDesign.CharcoalTextPrimary else FynxDesign.TextPrimary,
            surfaceVariant = if (charcoal || amoled) FynxDesign.CharcoalSurfaceRaised else FynxDesign.SurfaceRaised,
            onSurfaceVariant = if (charcoal || amoled) FynxDesign.CharcoalTextSecondary else FynxDesign.TextSecondary,
            outline = if (charcoal || amoled) FynxDesign.CharcoalOutline else FynxDesign.Outline,
            surfaceContainerLowest = if (charcoal || amoled) FynxDesign.CharcoalBackground else FynxDesign.Background,
            surfaceContainerLow = if (charcoal || amoled) FynxDesign.CharcoalSurface else FynxDesign.Surface,
            surfaceContainer = if (charcoal || amoled) FynxDesign.CharcoalSurface else FynxDesign.Surface,
            surfaceContainerHigh = if (charcoal || amoled) FynxDesign.CharcoalSurfaceRaised else FynxDesign.SurfaceRaised,
            surfaceContainerHighest = if (charcoal || amoled) FynxDesign.CharcoalSurfaceRaised else FynxDesign.SurfaceRaised,
            surfaceDim = if (charcoal || amoled) FynxDesign.CharcoalBackground else FynxDesign.Background,
            surfaceBright = if (charcoal || amoled) FynxDesign.CharcoalSurfaceRaised else FynxDesign.SurfaceRaised,
            surfaceTint = if (charcoal || amoled) effectiveAccent.primary else effectiveAccent.primary
        )
    } else {
        lightColorScheme(
            primary = effectiveAccent.primary,
            onPrimary = onAccent,
            secondary = effectiveAccent.secondary,
            onSecondary = if (effectiveAccent.secondary.luminance() > 0.5f) Color.Black else Color.White,
            background = Color(0xFFF8F9FB),
            onBackground = Color(0xFF11161B),
            surface = Color.White,
            onSurface = Color(0xFF11161B),
            surfaceVariant = Color(0xFFF0F2F5),
            onSurfaceVariant = Color(0xFF59636D),
            outline = Color(0xFFD5DBE1),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color.White,
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color(0xFFF0F2F5),
            surfaceContainerHighest = Color(0xFFE7EBEF),
            surfaceDim = Color(0xFFE1E5E9),
            surfaceBright = Color.White
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = fynxTypography(),
        shapes = Shapes(small = FynxDesign.ControlShape, medium = FynxDesign.CardShape, large = FynxDesign.LargeCardShape),
        content = { FynxCustomizationBackground(content) }
    )
}
