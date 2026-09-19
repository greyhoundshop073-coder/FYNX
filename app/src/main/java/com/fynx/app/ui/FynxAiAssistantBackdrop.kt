package com.fynx.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Dedicated AI-home pattern: the same charcoal foundation, with a different doodle story. */
@Composable
fun FynxAiAssistantBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Color(0xFF202326))

            val ink = Color.White.copy(alpha = 0.045f)
            val sw = 1.05.dp.toPx()
            val tileW = 205.dp.toPx()
            val tileH = 168.dp.toPx()

            fun line(a: Offset, b: Offset) = drawLine(ink, a, b, sw)
            fun circle(x: Float, y: Float, r: Float) =
                drawCircle(ink, r, Offset(x, y), style = Stroke(sw))

            fun spark(x: Float, y: Float, s: Float) {
                line(Offset(x, y - 16*s), Offset(x, y + 16*s))
                line(Offset(x - 16*s, y), Offset(x + 16*s, y))
                line(Offset(x - 9*s, y - 9*s), Offset(x + 9*s, y + 9*s))
                line(Offset(x + 9*s, y - 9*s), Offset(x - 9*s, y + 9*s))
            }

            fun lightbulb(x: Float, y: Float, s: Float) {
                circle(x, y, 15*s)
                line(Offset(x - 8*s, y + 11*s), Offset(x - 5*s, y + 20*s))
                line(Offset(x + 8*s, y + 11*s), Offset(x + 5*s, y + 20*s))
                line(Offset(x - 5*s, y + 20*s), Offset(x + 5*s, y + 20*s))
                line(Offset(x - 5*s, y + 25*s), Offset(x + 5*s, y + 25*s))
            }

            fun rocket(x: Float, y: Float, s: Float) {
                circle(x, y, 8*s)
                line(Offset(x, y - 18*s), Offset(x + 9*s, y - 5*s))
                line(Offset(x + 9*s, y - 5*s), Offset(x + 9*s, y + 10*s))
                line(Offset(x + 9*s, y + 10*s), Offset(x, y + 18*s))
                line(Offset(x, y + 18*s), Offset(x - 9*s, y + 10*s))
                line(Offset(x - 9*s, y + 10*s), Offset(x - 9*s, y - 5*s))
                line(Offset(x - 9*s, y - 5*s), Offset(x, y - 18*s))
                line(Offset(x - 9*s, y + 7*s), Offset(x - 18*s, y + 14*s))
                line(Offset(x + 9*s, y + 7*s), Offset(x + 18*s, y + 14*s))
            }

            fun network(x: Float, y: Float, s: Float) {
                circle(x, y, 5*s)
                circle(x + 28*s, y - 17*s, 5*s)
                circle(x + 33*s, y + 18*s, 5*s)
                circle(x + 62*s, y, 5*s)
                line(Offset(x + 5*s, y - 2*s), Offset(x + 24*s, y - 15*s))
                line(Offset(x + 5*s, y + 2*s), Offset(x + 28*s, y + 16*s))
                line(Offset(x + 33*s, y - 14*s), Offset(x + 57*s, y - 2*s))
                line(Offset(x + 38*s, y + 16*s), Offset(x + 57*s, y + 2*s))
            }

            fun chatSpark(x: Float, y: Float, s: Float) {
                drawRoundRect(
                    color = ink,
                    topLeft = Offset(x, y),
                    size = Size(52*s, 34*s),
                    cornerRadius = CornerRadius(10*s, 10*s),
                    style = Stroke(sw)
                )
                line(Offset(x + 11*s, y + 34*s), Offset(x + 7*s, y + 43*s))
                spark(x + 35*s, y + 17*s, .32f)
            }

            var row = 0
            var y = -35f
            while (y < size.height + tileH) {
                var col = 0
                var x = if (row % 2 == 0) -55f else -155f
                while (x < size.width + tileW) {
                    when ((row * 7 + col) % 8) {
                        0 -> lightbulb(x + 35, y + 46, .78f)
                        1 -> rocket(x + 38, y + 48, .70f)
                        2 -> network(x + 18, y + 50, .66f)
                        3 -> chatSpark(x + 18, y + 35, .72f)
                        4 -> spark(x + 38, y + 48, .85f)
                        5 -> {
                            circle(x + 32, y + 44, 17f)
                            line(Offset(x + 32, y + 27), Offset(x + 32, y + 61))
                            line(Offset(x + 15, y + 44), Offset(x + 49, y + 44))
                        }
                        6 -> {
                            line(Offset(x + 16, y + 55), Offset(x + 34, y + 32))
                            line(Offset(x + 34, y + 32), Offset(x + 53, y + 55))
                            line(Offset(x + 22, y + 49), Offset(x + 46, y + 49))
                        }
                        else -> {
                            circle(x + 30, y + 42, 9f)
                            line(Offset(x + 21, y + 42), Offset(x + 39, y + 42))
                            line(Offset(x + 30, y + 33), Offset(x + 30, y + 51))
                        }
                    }
                    x += tileW
                    col++
                }
                y += tileH
                row++
            }
        }
        content()
    }
}
