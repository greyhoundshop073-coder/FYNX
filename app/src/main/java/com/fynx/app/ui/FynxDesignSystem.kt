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
enum class FynxAccent(
    val primary: Color,
    val secondary: Color,
    /** Mature companion tone used sparingly for tertiary accents and reflective depth. */
    val companion: Color
) {
    // Keep the established FYNX identity colors unchanged.
    Blue(Color(0xFF2F8CFF), Color(0xFF22C7F2), Color(0xFFB7A36A)),
    Purple(Color(0xFF7C5CFF), Color(0xFFB18CFF), Color(0xFFA4778A)),
    Charcoal(Color(0xFF37474F), Color(0xFF607D8B), Color(0xFF8A9499))
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
    // Subtle neutral/reflective tones: deliberately restrained so light mode stays white.
    val BlueLightCompanionContainer = Color(0xFFF2EBDD)
    val BlueDarkCompanionContainer = Color(0xFF3A3324)
    val PurpleLightCompanionContainer = Color(0xFFF3E7EC)
    val PurpleDarkCompanionContainer = Color(0xFF3A2B32)
    val CharcoalLightCompanionContainer = Color(0xFFE7EBED)
    val CharcoalDarkCompanionContainer = Color(0xFF2C3235)
    val BlueLightOutlineVariant = Color(0xFFE1EAF6)
    val PurpleLightOutlineVariant = Color(0xFFECE3E8)
    val CharcoalLightOutlineVariant = Color(0xFFDCE1E3)
    val BlueDarkOutlineVariant = Color(0xFF3A4A5D)
    val PurpleDarkOutlineVariant = Color(0xFF4A3F4A)
    val CharcoalDarkOutlineVariant = Color(0xFF394146)
    val CharcoalBackground = Color(0xFF1F2428)
    val CharcoalSurface = Color(0xFF272D32)
    val CharcoalSurfaceRaised = Color(0xFF31383E)
    val CharcoalTextPrimary = Color(0xFFF2F5F7)
    val CharcoalTextSecondary = Color(0xFFB8C1C8)
    val CharcoalOutline = Color(0xFF465159)
    val CharcoalSelectedContainer = Color(0xFF37434B)
    val AmoledBackground = Color.Black
    val AmoledSurface = Color.Black
    val AmoledSurfaceRaised = Color(0xFF121212)
    val AmoledTextPrimary = Color.White
    val AmoledTextSecondary = Color(0xFFBDBDBD)
    val AmoledOutline = Color(0xFF2A2A2A)
    val AmoledSelectedContainer = Color(0xFF1C1C1C)
    val CardShape = RoundedCornerShape(16.dp)
    val LargeCardShape = RoundedCornerShape(20.dp)
    val ControlShape = RoundedCornerShape(24.dp)
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
    val charcoal = appearance == "Charcoal Black"
    val amoled = appearance == "Black AMOLED"
    val scheduledNight = appearance == "System" && scheduledNightModeActive(context)
    val effectiveDarkMode = when (appearance) {
        "Light" -> false
        "Dark" -> true
        "Charcoal Black" -> true
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
            tertiary = effectiveAccent.companion,
            onTertiary = if (effectiveAccent.companion.luminance() > 0.5f) Color.Black else Color.White,
            background = when { amoled -> FynxDesign.AmoledBackground; charcoal -> FynxDesign.CharcoalBackground; else -> FynxDesign.Background },
            onBackground = when { amoled -> FynxDesign.AmoledTextPrimary; charcoal -> FynxDesign.CharcoalTextPrimary; else -> FynxDesign.TextPrimary },
            surface = when { amoled -> FynxDesign.AmoledSurface; charcoal -> FynxDesign.CharcoalSurface; else -> FynxDesign.Surface },
            onSurface = when { amoled -> FynxDesign.AmoledTextPrimary; charcoal -> FynxDesign.CharcoalTextPrimary; else -> FynxDesign.TextPrimary },
            surfaceVariant = when { amoled -> FynxDesign.AmoledSurfaceRaised; charcoal -> FynxDesign.CharcoalSurfaceRaised; else -> FynxDesign.SurfaceRaised },
            onSurfaceVariant = when { amoled -> FynxDesign.AmoledTextSecondary; charcoal -> FynxDesign.CharcoalTextSecondary; else -> FynxDesign.TextSecondary },
            outline = when { amoled -> FynxDesign.AmoledOutline; charcoal -> FynxDesign.CharcoalOutline; else -> FynxDesign.Outline },
            surfaceContainerLowest = when { amoled -> FynxDesign.AmoledBackground; charcoal -> FynxDesign.CharcoalBackground; else -> FynxDesign.Background },
            surfaceContainerLow = when { amoled -> FynxDesign.AmoledSurface; charcoal -> FynxDesign.CharcoalSurface; else -> FynxDesign.Surface },
            surfaceContainer = when { amoled -> FynxDesign.AmoledSurface; charcoal -> FynxDesign.CharcoalSurface; else -> FynxDesign.Surface },
            surfaceContainerHigh = when { amoled -> FynxDesign.AmoledSurfaceRaised; charcoal -> FynxDesign.CharcoalSurfaceRaised; else -> FynxDesign.SurfaceRaised },
            surfaceContainerHighest = when { amoled -> FynxDesign.AmoledSurfaceRaised; charcoal -> FynxDesign.CharcoalSurfaceRaised; else -> FynxDesign.SurfaceRaised },
            surfaceDim = when { amoled -> FynxDesign.AmoledBackground; charcoal -> FynxDesign.CharcoalBackground; else -> FynxDesign.Background },
            surfaceBright = when { amoled -> FynxDesign.AmoledSurfaceRaised; charcoal -> FynxDesign.CharcoalSurfaceRaised; else -> FynxDesign.SurfaceRaised },
            surfaceTint = effectiveAccent.primary,
            tertiaryContainer = when {
                amoled -> FynxDesign.AmoledSelectedContainer
                charcoal -> FynxDesign.CharcoalDarkCompanionContainer
                effectiveAccent == FynxAccent.Purple -> FynxDesign.PurpleDarkCompanionContainer
                effectiveAccent == FynxAccent.Blue -> FynxDesign.BlueDarkCompanionContainer
                else -> FynxDesign.CharcoalDarkCompanionContainer
            },
            onTertiaryContainer = when {
                amoled -> FynxDesign.AmoledTextPrimary
                else -> Color.White
            },
            outlineVariant = when {
                amoled -> FynxDesign.AmoledOutline
                charcoal -> FynxDesign.CharcoalDarkOutlineVariant
                effectiveAccent == FynxAccent.Purple -> FynxDesign.PurpleDarkOutlineVariant
                effectiveAccent == FynxAccent.Blue -> FynxDesign.BlueDarkOutlineVariant
                else -> FynxDesign.CharcoalDarkOutlineVariant
            }
        )
    } else {
        lightColorScheme(
            primary = effectiveAccent.primary,
            onPrimary = onAccent,
            secondary = effectiveAccent.secondary,
            onSecondary = if (effectiveAccent.secondary.luminance() > 0.5f) Color.Black else Color.White,
            tertiary = effectiveAccent.companion,
            onTertiary = if (effectiveAccent.companion.luminance() > 0.5f) Color.Black else Color.White,
            tertiaryContainer = when (effectiveAccent) {
                FynxAccent.Blue -> FynxDesign.BlueLightCompanionContainer
                FynxAccent.Purple -> FynxDesign.PurpleLightCompanionContainer
                FynxAccent.Charcoal -> FynxDesign.CharcoalLightCompanionContainer
            },
            onTertiaryContainer = Color(0xFF20252A),
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
            surfaceBright = Color.White,
            surfaceTint = effectiveAccent.primary,
            outlineVariant = when (effectiveAccent) {
                FynxAccent.Blue -> FynxDesign.BlueLightOutlineVariant
                FynxAccent.Purple -> FynxDesign.PurpleLightOutlineVariant
                FynxAccent.Charcoal -> FynxDesign.CharcoalLightOutlineVariant
            }
        )
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = fynxTypography(),
        shapes = Shapes(small = FynxDesign.ControlShape, medium = FynxDesign.CardShape, large = FynxDesign.LargeCardShape),
        content = { FynxCustomizationBackground(content) }
    )
}
