package com.fynx.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun FynxConversationBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 86.dp.toPx()
            val radius = 13.dp.toPx()
            var y = -step
            var row = 0
            while (y < size.height + step) {
                var x = -step
                while (x < size.width + step) {
                    val offset = if (row % 2 == 0) 0f else step / 2f
                    drawCircle(color = scheme.onSurface.copy(alpha = 0.035f), radius = radius, center = Offset(x + offset, y), style = Stroke(width = 1.dp.toPx()))
                    x += step
                }
                y += step
                row++
            }
        }
        content()
    }
}
