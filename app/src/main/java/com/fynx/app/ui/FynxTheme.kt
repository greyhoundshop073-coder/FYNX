package com.fynx.app.ui

import androidx.compose.runtime.Composable

/**
 * Compatibility entry point for the FYNX theme.
 *
 * The complete visual system lives in FynxDesignSystem.kt so there is only
 * one source of truth for colors, surfaces, typography, shapes and accents.
 */
@Composable
fun FynxTheme(
    accent: FynxAccent = FynxAccent.Blue,
    content: @Composable () -> Unit
) {
    FynxDesignSystemTheme(accent = accent, content = content)
}
