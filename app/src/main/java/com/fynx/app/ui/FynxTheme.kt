package com.fynx.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun FynxTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme()
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}
