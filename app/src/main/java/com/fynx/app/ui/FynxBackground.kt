package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Subtle brand background layer. Kept behind content so readability is preserved. */
@Composable
fun FynxBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        FynxDesign.Background,
                        Color(0xFF09182B),
                        FynxDesign.Background
                    )
                )
            )
    ) {
        content()
    }
}
