package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Subtle mirror-glass treatment for existing screen headers.
 * Deliberately changes paint only; callers retain their existing sizing and actions.
 */
fun Modifier.fynxHeaderGlass(): Modifier {
    val shape = RoundedCornerShape(18.dp)
    return this
        .background(
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.17f),
                    Color(0xFFB9DCE8).copy(alpha = 0.075f),
                    Color.White.copy(alpha = 0.035f)
                )
            ),
            shape
        )
        .border(
            1.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.42f),
                    Color(0xFFB9DCE8).copy(alpha = 0.16f)
                )
            ),
            shape
        )
}
