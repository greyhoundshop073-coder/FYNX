package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val FynxChatWallpaperOptions = FynxGlassThemeId.entries.map { it.label }

@Composable
fun FynxChatWallpaperBackground(modifier: Modifier = Modifier, wallpaperOverride: String? = null, settingsKey: String? = null, content: @Composable BoxScope.() -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val wallpaper = wallpaperOverride ?: FynxPreferencesStore.loadChatWallpaper(context)
    val themeId = FynxGlassThemeId.entries.firstOrNull { it.label == wallpaper }
        ?: FynxGlassThemeId.PURE_BLACK
    val palette = fynxGlassPalette(themeId)
    val key = settingsKey
    val rotation = if (key != null) FynxConversationPreferences.chatGradientRotation(context, key) else 45f
    val glow = if (key != null) FynxConversationPreferences.chatBackgroundGlow(context, key) else 1f
    val angle = Math.toRadians(rotation.toDouble())
    val dx = kotlin.math.cos(angle).toFloat()
    val dy = kotlin.math.sin(angle).toFloat()
    val backgroundBrush = Brush.linearGradient(
        colors = listOf(palette.background, palette.backgroundMid, palette.backgroundGlow.copy(alpha = glow.coerceIn(0.6f, 1.4f))),
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(dx * 900f, dy * 900f)
    )
    Box(modifier.background(backgroundBrush)) {
        FynxChatDoodlePattern(
            palette = palette,
            density = if (key != null) FynxConversationPreferences.chatDoodleDensity(context, key) else 1f,
            scaleMultiplier = if (key != null) FynxConversationPreferences.chatDoodleScale(context, key) else 1f,
            intensity = if (key != null) FynxConversationPreferences.chatDoodleIntensity(context, key) else 1f,
            light = if (key != null) FynxConversationPreferences.chatDoodleLight(context, key) else 1f
        )
        content()
    }
}

@Composable
fun FynxChatDoodlePattern(palette: FynxGlassThemePalette? = null, density: Float = 1f, scaleMultiplier: Float = 1f, intensity: Float = 1f, light: Float = 1f) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val activePalette = palette ?: fynxGlassPalette(FynxGlassThemeId.PURE_BLACK)
        val baseAlpha = if (activePalette.id == FynxGlassThemeId.LIGHT) 0.16f else 0.12f
        val ink = activePalette.doodlePrimary.copy(alpha = (baseAlpha * intensity).coerceIn(0.02f, 0.26f))
        val reflection = activePalette.doodleHighlight.copy(alpha = (0.055f * light).coerceIn(0.01f, 0.10f))
        val sw = 0.72.dp.toPx()
        val tileW = 420.dp.toPx()
        val tileH = 520.dp.toPx()

        fun p(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x, y)
        fun line(a: androidx.compose.ui.geometry.Offset, b: androidx.compose.ui.geometry.Offset) { drawLine(ink, a, b, sw); drawLine(reflection, a, b, sw * 2.7f) }
        fun circle(x: Float, y: Float, r: Float) =
            drawCircle(ink, r, p(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))

        fun bubble(x: Float, y: Float, s: Float) {
            drawRoundRect(
                ink, p(x, y), androidx.compose.ui.geometry.Size(34f * s, 24f * s),
                androidx.compose.ui.geometry.CornerRadius(7f * s, 7f * s),
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw)
            )
            line(p(x + 8f * s, y + 24f * s), p(x + 5f * s, y + 29f * s))
            line(p(x + 11f * s, y + 8f * s), p(x + 23f * s, y + 8f * s))
            line(p(x + 11f * s, y + 13f * s), p(x + 19f * s, y + 13f * s))
        }

        fun camera(x: Float, y: Float, s: Float) {
            drawRoundRect(
                ink, p(x, y), androidx.compose.ui.geometry.Size(32f * s, 22f * s),
                androidx.compose.ui.geometry.CornerRadius(5f * s, 5f * s),
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw)
            )
            line(p(x + 8f * s, y), p(x + 12f * s, y - 4f * s))
            circle(x + 16f * s, y + 11f * s, 6f * s)
        }

        fun phone(x: Float, y: Float, s: Float) {
            drawRoundRect(
                ink, p(x, y), androidx.compose.ui.geometry.Size(17f * s, 31f * s),
                androidx.compose.ui.geometry.CornerRadius(5f * s, 5f * s),
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw)
            )
            line(p(x + 6f * s, y + 4f * s), p(x + 11f * s, y + 4f * s))
            circle(x + 8.5f * s, y + 26f * s, 1.2f * s)
        }

        fun music(x: Float, y: Float, s: Float) {
            line(p(x + 15f * s, y), p(x + 15f * s, y + 20f * s))
            line(p(x + 15f * s, y), p(x + 25f * s, y - 3f * s))
            circle(x + 10f * s, y + 22f * s, 5f * s)
        }

        fun pin(x: Float, y: Float, s: Float) {
            circle(x + 10f * s, y + 9f * s, 7f * s)
            line(p(x + 3f * s, y + 12f * s), p(x + 10f * s, y + 26f * s))
            line(p(x + 17f * s, y + 12f * s), p(x + 10f * s, y + 26f * s))
            circle(x + 10f * s, y + 9f * s, 2f * s)
        }

        fun mic(x: Float, y: Float, s: Float) {
            drawRoundRect(
                ink, p(x + 5f * s, y), androidx.compose.ui.geometry.Size(10f * s, 20f * s),
                androidx.compose.ui.geometry.CornerRadius(6f * s, 6f * s),
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw)
            )
            line(p(x + 2f * s, y + 12f * s), p(x + 2f * s, y + 16f * s))
            line(p(x + 2f * s, y + 16f * s), p(x + 10f * s, y + 22f * s))
            line(p(x + 18f * s, y + 12f * s), p(x + 18f * s, y + 16f * s))
            line(p(x + 18f * s, y + 16f * s), p(x + 10f * s, y + 22f * s))
        }

        fun heart(x: Float, y: Float, s: Float) {
            val a = p(x + 10f * s, y + 25f * s)
            val b = p(x, y + 10f * s)
            val c = p(x + 4f * s, y + 3f * s)
            val d = p(x + 10f * s, y + 8f * s)
            val e = p(x + 16f * s, y + 3f * s)
            val f = p(x + 20f * s, y + 10f * s)
            line(a, b); line(b, c); line(c, d); line(d, e); line(e, f); line(f, a)
        }

        fun star(x: Float, y: Float, s: Float) {
            val pts = listOf(
                p(x + 9f*s, y), p(x + 12f*s, y + 6f*s), p(x + 19f*s, y + 7f*s),
                p(x + 14f*s, y + 12f*s), p(x + 16f*s, y + 19f*s), p(x + 9f*s, y + 15f*s),
                p(x + 3f*s, y + 19f*s), p(x + 4f*s, y + 12f*s), p(x, y + 7f*s), p(x + 7f*s, y + 6f*s)
            )
            for (i in pts.indices) line(pts[i], pts[(i + 1) % pts.size])
        }

        fun smile(x: Float, y: Float, s: Float) {
            circle(x + 12f*s, y + 12f*s, 11f*s)
            circle(x + 8f*s, y + 9f*s, 1.2f*s)
            circle(x + 16f*s, y + 9f*s, 1.2f*s)
            line(p(x + 7f*s, y + 15f*s), p(x + 12f*s, y + 18f*s))
            line(p(x + 12f*s, y + 18f*s), p(x + 17f*s, y + 15f*s))
        }

        fun paperclip(x: Float, y: Float, s: Float) {
            drawRoundRect(
                ink, p(x + 5f*s, y), androidx.compose.ui.geometry.Size(10f*s, 24f*s),
                androidx.compose.ui.geometry.CornerRadius(5f*s, 5f*s),
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw)
            )
            line(p(x + 10f*s, y + 5f*s), p(x + 10f*s, y + 17f*s))
        }

        fun video(x: Float, y: Float, s: Float) {
            drawRoundRect(
                ink, p(x, y + 3f*s), androidx.compose.ui.geometry.Size(25f*s, 18f*s),
                androidx.compose.ui.geometry.CornerRadius(4f*s, 4f*s),
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw)
            )
            line(p(x + 25f*s, y + 8f*s), p(x + 32f*s, y + 4f*s))
            line(p(x + 32f*s, y + 4f*s), p(x + 32f*s, y + 20f*s))
            line(p(x + 32f*s, y + 20f*s), p(x + 25f*s, y + 16f*s))
        }

        fun headphones(x: Float, y: Float, s: Float) {
            circle(x + 12f*s, y + 13f*s, 11f*s)
            line(p(x + 1f*s, y + 13f*s), p(x + 1f*s, y + 22f*s))
            line(p(x + 23f*s, y + 13f*s), p(x + 23f*s, y + 22f*s))
        }

        fun coffee(x: Float, y: Float, s: Float) {
            drawRoundRect(
                ink, p(x, y + 4f*s), androidx.compose.ui.geometry.Size(24f*s, 17f*s),
                androidx.compose.ui.geometry.CornerRadius(4f*s, 4f*s),
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw)
            )
            line(p(x + 24f*s, y + 8f*s), p(x + 30f*s, y + 8f*s))
            line(p(x + 30f*s, y + 8f*s), p(x + 30f*s, y + 16f*s))
            line(p(x + 30f*s, y + 16f*s), p(x + 24f*s, y + 16f*s))
            line(p(x + 7f*s, y), p(x + 5f*s, y - 5f*s))
            line(p(x + 15f*s, y), p(x + 17f*s, y - 5f*s))
        }

        fun link(x: Float, y: Float, s: Float) {
            drawRoundRect(ink, p(x, y + 6f*s), androidx.compose.ui.geometry.Size(16f*s, 8f*s), androidx.compose.ui.geometry.CornerRadius(4f*s, 4f*s), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
            drawRoundRect(ink, p(x + 10f*s, y + 6f*s), androidx.compose.ui.geometry.Size(16f*s, 8f*s), androidx.compose.ui.geometry.CornerRadius(4f*s, 4f*s), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
            line(p(x + 10f*s, y + 10f*s), p(x + 16f*s, y + 10f*s))
        }

        fun bicycle(x: Float, y: Float, s: Float) {
            circle(x + 7f*s, y + 22f*s, 7f*s); circle(x + 29f*s, y + 22f*s, 7f*s)
            line(p(x + 7f*s, y + 22f*s), p(x + 15f*s, y + 9f*s)); line(p(x + 15f*s, y + 9f*s), p(x + 22f*s, y + 22f*s))
            line(p(x + 22f*s, y + 22f*s), p(x + 7f*s, y + 22f*s)); line(p(x + 15f*s, y + 9f*s), p(x + 26f*s, y + 9f*s))
            line(p(x + 26f*s, y + 9f*s), p(x + 29f*s, y + 22f*s))
        }
        fun map(x: Float, y: Float, s: Float) {
            line(p(x, y + 5f*s), p(x + 15f*s, y)); line(p(x + 15f*s, y), p(x + 31f*s, y + 6f*s))
            line(p(x, y + 5f*s), p(x, y + 28f*s)); line(p(x + 15f*s, y), p(x + 15f*s, y + 23f*s))
            line(p(x + 31f*s, y + 6f*s), p(x + 31f*s, y + 30f*s)); line(p(x, y + 28f*s), p(x + 15f*s, y + 23f*s)); line(p(x + 15f*s, y + 23f*s), p(x + 31f*s, y + 30f*s))
        }
        fun palette(x: Float, y: Float, s: Float) {
            circle(x + 15f*s, y + 15f*s, 15f*s); circle(x + 9f*s, y + 10f*s, 2f*s); circle(x + 18f*s, y + 7f*s, 2f*s); circle(x + 23f*s, y + 15f*s, 2f*s)
        }
        fun bulb(x: Float, y: Float, s: Float) {
            circle(x + 12f*s, y + 10f*s, 9f*s); line(p(x + 6f*s, y + 17f*s), p(x + 18f*s, y + 17f*s)); line(p(x + 8f*s, y + 23f*s), p(x + 16f*s, y + 23f*s))
        }
        fun guitar(x: Float, y: Float, s: Float) {
            circle(x + 10f*s, y + 18f*s, 7f*s); circle(x + 19f*s, y + 12f*s, 5f*s)
            line(p(x + 15f*s, y + 14f*s), p(x + 31f*s, y)); line(p(x + 31f*s, y), p(x + 34f*s, y + 3f*s))
        }
        fun rocket(x: Float, y: Float, s: Float) {
            drawRoundRect(ink, p(x + 8f*s, y + 2f*s), androidx.compose.ui.geometry.Size(13f*s, 30f*s), androidx.compose.ui.geometry.CornerRadius(8f*s, 8f*s), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
            circle(x + 14.5f*s, y + 10f*s, 2f*s); line(p(x + 8f*s, y + 25f*s), p(x + 2f*s, y + 31f*s)); line(p(x + 21f*s, y + 25f*s), p(x + 27f*s, y + 31f*s))
        }
        fun flowers(x: Float, y: Float, s: Float) {
            line(p(x + 10f*s, y + 10f*s), p(x + 10f*s, y + 32f*s)); circle(x + 10f*s, y + 8f*s, 4f*s); circle(x + 5f*s, y + 8f*s, 4f*s); circle(x + 15f*s, y + 8f*s, 4f*s); line(p(x + 10f*s, y + 32f*s), p(x + 4f*s, y + 38f*s)); line(p(x + 10f*s, y + 32f*s), p(x + 17f*s, y + 37f*s))
        }
        fun clouds(x: Float, y: Float, s: Float) {
            circle(x + 9f*s, y + 14f*s, 7f*s); circle(x + 18f*s, y + 10f*s, 9f*s); circle(x + 28f*s, y + 15f*s, 7f*s)
            line(p(x + 4f*s, y + 20f*s), p(x + 33f*s, y + 20f*s))
        }

        fun sun(x: Float, y: Float, s: Float) {
            circle(x + 14f*s, y + 14f*s, 7f*s)
            for (i in 0 until 8) {
                val a = Math.toRadians((i * 45).toDouble())
                line(p(x + (14f + kotlin.math.cos(a).toFloat() * 11f)*s, y + (14f + kotlin.math.sin(a).toFloat() * 11f)*s),
                     p(x + (14f + kotlin.math.cos(a).toFloat() * 16f)*s, y + (14f + kotlin.math.sin(a).toFloat() * 16f)*s))
            }
        }
        fun plane(x: Float, y: Float, s: Float) {
            line(p(x, y + 14f*s), p(x + 34f*s, y + 14f*s))
            line(p(x + 12f*s, y + 14f*s), p(x + 20f*s, y + 5f*s))
            line(p(x + 12f*s, y + 14f*s), p(x + 20f*s, y + 23f*s))
            line(p(x + 26f*s, y + 14f*s), p(x + 31f*s, y + 8f*s))
        }
        fun palm(x: Float, y: Float, s: Float) {
            line(p(x + 15f*s, y + 32f*s), p(x + 16f*s, y + 12f*s))
            line(p(x + 16f*s, y + 12f*s), p(x + 7f*s, y + 5f*s))
            line(p(x + 16f*s, y + 12f*s), p(x + 16f*s, y + 2f*s))
            line(p(x + 16f*s, y + 12f*s), p(x + 25f*s, y + 4f*s))
            line(p(x + 16f*s, y + 12f*s), p(x + 28f*s, y + 11f*s))
        }
        fun suitcase(x: Float, y: Float, s: Float) {
            drawRoundRect(ink, p(x + 2f*s, y + 7f*s), androidx.compose.ui.geometry.Size(26f*s, 24f*s),
                androidx.compose.ui.geometry.CornerRadius(4f*s, 4f*s), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
            line(p(x + 10f*s, y + 7f*s), p(x + 10f*s, y + 2f*s)); line(p(x + 10f*s, y + 2f*s), p(x + 20f*s, y + 2f*s)); line(p(x + 20f*s, y + 2f*s), p(x + 20f*s, y + 7f*s))
            line(p(x + 15f*s, y + 7f*s), p(x + 15f*s, y + 31f*s))
        }
        fun compass(x: Float, y: Float, s: Float) {
            circle(x + 15f*s, y + 15f*s, 14f*s); circle(x + 15f*s, y + 15f*s, 2f*s)
            line(p(x + 15f*s, y + 4f*s), p(x + 22f*s, y + 22f*s)); line(p(x + 22f*s, y + 22f*s), p(x + 15f*s, y + 15f*s))
        }
        fun gift(x: Float, y: Float, s: Float) {
            drawRoundRect(ink, p(x + 2f*s, y + 10f*s), androidx.compose.ui.geometry.Size(28f*s, 20f*s),
                androidx.compose.ui.geometry.CornerRadius(3f*s, 3f*s), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
            line(p(x + 16f*s, y + 10f*s), p(x + 16f*s, y + 30f*s)); line(p(x + 2f*s, y + 16f*s), p(x + 30f*s, y + 16f*s))
            circle(x + 12f*s, y + 7f*s, 4f*s); circle(x + 20f*s, y + 7f*s, 4f*s)
        }
        fun people(x: Float, y: Float, s: Float) {
            circle(x + 10f*s, y + 8f*s, 5f*s); circle(x + 25f*s, y + 8f*s, 5f*s)
            line(p(x + 2f*s, y + 28f*s), p(x + 18f*s, y + 28f*s)); line(p(x + 17f*s, y + 28f*s), p(x + 33f*s, y + 28f*s))
            line(p(x + 10f*s, y + 13f*s), p(x + 7f*s, y + 27f*s)); line(p(x + 25f*s, y + 13f*s), p(x + 28f*s, y + 27f*s))
        }
        fun trophy(x: Float, y: Float, s: Float) {
            drawRoundRect(ink, p(x + 8f*s, y + 3f*s), androidx.compose.ui.geometry.Size(14f*s, 17f*s),
                androidx.compose.ui.geometry.CornerRadius(3f*s, 3f*s), style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
            line(p(x + 8f*s, y + 7f*s), p(x + 2f*s, y + 7f*s)); line(p(x + 2f*s, y + 7f*s), p(x + 2f*s, y + 14f*s)); line(p(x + 2f*s, y + 14f*s), p(x + 8f*s, y + 14f*s))
            line(p(x + 22f*s, y + 7f*s), p(x + 28f*s, y + 7f*s)); line(p(x + 28f*s, y + 7f*s), p(x + 28f*s, y + 14f*s)); line(p(x + 28f*s, y + 14f*s), p(x + 22f*s, y + 14f*s))
            line(p(x + 15f*s, y + 20f*s), p(x + 15f*s, y + 27f*s)); line(p(x + 8f*s, y + 28f*s), p(x + 22f*s, y + 28f*s))
        }
        fun crown(x: Float, y: Float, s: Float) {
            line(p(x + 2f*s, y + 7f*s), p(x + 7f*s, y + 23f*s)); line(p(x + 7f*s, y + 23f*s), p(x + 25f*s, y + 23f*s)); line(p(x + 25f*s, y + 23f*s), p(x + 30f*s, y + 7f*s))
            line(p(x + 2f*s, y + 7f*s), p(x + 10f*s, y + 14f*s)); line(p(x + 10f*s, y + 14f*s), p(x + 16f*s, y + 5f*s)); line(p(x + 16f*s, y + 5f*s), p(x + 22f*s, y + 14f*s)); line(p(x + 22f*s, y + 14f*s), p(x + 30f*s, y + 7f*s))
        }
        fun medal(x: Float, y: Float, s: Float) {
            line(p(x + 9f*s, y), p(x + 9f*s, y + 10f*s)); line(p(x + 21f*s, y), p(x + 21f*s, y + 10f*s))
            circle(x + 15f*s, y + 20f*s, 10f*s); star(x + 10f*s, y + 15f*s, 0.45f*s)
        }
        fun confetti(x: Float, y: Float, s: Float) {
            line(p(x + 4f*s, y + 4f*s), p(x + 9f*s, y + 12f*s)); line(p(x + 18f*s, y), p(x + 14f*s, y + 10f*s))
            line(p(x + 28f*s, y + 5f*s), p(x + 21f*s, y + 14f*s)); line(p(x + 7f*s, y + 24f*s), p(x + 13f*s, y + 18f*s)); line(p(x + 25f*s, y + 25f*s), p(x + 20f*s, y + 18f*s))
        }
        fun fireworks(x: Float, y: Float, s: Float) {
            circle(x + 16f*s, y + 16f*s, 3f*s)
            for (i in 0 until 8) {
                val a = Math.toRadians((i * 45).toDouble())
                line(p(x + (16f + kotlin.math.cos(a).toFloat()*7f)*s, y + (16f + kotlin.math.sin(a).toFloat()*7f)*s),
                     p(x + (16f + kotlin.math.cos(a).toFloat()*14f)*s, y + (16f + kotlin.math.sin(a).toFloat()*14f)*s))
            }
        }
        fun waves(x: Float, y: Float, s: Float) {
            line(p(x, y + 8f*s), p(x + 7f*s, y + 4f*s)); line(p(x + 7f*s, y + 4f*s), p(x + 14f*s, y + 8f*s)); line(p(x + 14f*s, y + 8f*s), p(x + 21f*s, y + 4f*s)); line(p(x + 21f*s, y + 4f*s), p(x + 28f*s, y + 8f*s))
            line(p(x, y + 18f*s), p(x + 7f*s, y + 14f*s)); line(p(x + 7f*s, y + 14f*s), p(x + 14f*s, y + 18f*s)); line(p(x + 14f*s, y + 18f*s), p(x + 21f*s, y + 14f*s)); line(p(x + 21f*s, y + 14f*s), p(x + 28f*s, y + 18f*s))
        }
        fun boat(x: Float, y: Float, s: Float) {
            line(p(x + 3f*s, y + 17f*s), p(x + 29f*s, y + 17f*s)); line(p(x + 3f*s, y + 17f*s), p(x + 8f*s, y + 25f*s)); line(p(x + 8f*s, y + 25f*s), p(x + 24f*s, y + 25f*s)); line(p(x + 24f*s, y + 25f*s), p(x + 29f*s, y + 17f*s))
            line(p(x + 16f*s, y + 17f*s), p(x + 16f*s, y + 3f*s)); line(p(x + 16f*s, y + 3f*s), p(x + 25f*s, y + 13f*s)); line(p(x + 25f*s, y + 13f*s), p(x + 16f*s, y + 13f*s))
        }
        fun shell(x: Float, y: Float, s: Float) {
            circle(x + 14f*s, y + 16f*s, 12f*s)
            for (i in 0 until 5) line(p(x + (6f+i*4f)*s, y + 8f*s), p(x + (8f+i*3f)*s, y + 25f*s))
        }
        fun fish(x: Float, y: Float, s: Float) {
            line(p(x + 4f*s, y + 15f*s), p(x + 18f*s, y + 7f*s)); line(p(x + 18f*s, y + 7f*s), p(x + 29f*s, y + 15f*s)); line(p(x + 29f*s, y + 15f*s), p(x + 18f*s, y + 23f*s)); line(p(x + 18f*s, y + 23f*s), p(x + 4f*s, y + 15f*s))
            line(p(x + 4f*s, y + 15f*s), p(x, y + 8f*s)); line(p(x, y + 8f*s), p(x, y + 22f*s)); line(p(x, y + 22f*s), p(x + 4f*s, y + 15f*s))
            circle(x + 22f*s, y + 13f*s, 1.2f*s)
        }

        fun drawMotif(index: Int, x: Float, y: Float, scale: Float, theme: FynxGlassThemeId) {
            val stories = when (theme) {
                FynxGlassThemeId.PURE_BLACK -> intArrayOf(0, 1, 2, 3, 5, 6, 7, 9, 10, 11, 12, 13, 4, 8, 2, 3, 1, 6, 7, 5, 10, 0)
                FynxGlassThemeId.AURORA -> intArrayOf(19, 15, 22, 7, 16, 17, 18, 20, 21, 3, 10, 1, 4, 11, 19, 15, 7, 16, 22, 20, 3, 10)
                FynxGlassThemeId.LIGHT -> intArrayOf(12, 20, 2, 0, 6, 1, 3, 5, 16, 8, 4, 13, 12, 20, 2, 0, 6, 1, 3, 5, 16, 8)
                FynxGlassThemeId.EMERALD -> intArrayOf(14, 4, 2, 13, 20, 18, 3, 6, 12, 15, 1, 19, 14, 4, 2, 13, 20, 18, 3, 6, 12, 15)
                FynxGlassThemeId.SUNSET -> intArrayOf(23, 24, 25, 27, 28, 29, 30, 31, 32, 33, 0, 1, 23, 24, 25, 27, 28, 29, 30, 31, 32, 33)
                FynxGlassThemeId.ROSE -> intArrayOf(26, 6, 23, 34, 16, 8, 3, 20, 7, 26, 6, 23, 34, 16, 8, 3, 20, 7)
                FynxGlassThemeId.GOLDEN -> intArrayOf(27, 28, 29, 30, 31, 32, 33, 7, 10, 26, 27, 28, 29, 30, 31, 32, 33, 7, 10, 26)
                FynxGlassThemeId.TURQUOISE -> intArrayOf(35, 36, 37, 38, 39, 21, 15, 19, 24, 35, 36, 37, 38, 39, 21, 15, 19, 24)
            }
            when (stories[index % stories.size]) {
                0 -> bubble(x, y, scale)
                1 -> camera(x, y, scale)
                2 -> phone(x, y, scale)
                3 -> music(x, y, scale)
                4 -> pin(x, y, scale)
                5 -> mic(x, y, scale)
                6 -> heart(x, y, scale)
                7 -> star(x, y, scale)
                8 -> smile(x, y, scale)
                9 -> paperclip(x, y, scale)
                10 -> video(x, y, scale)
                11 -> headphones(x, y, scale)
                12 -> coffee(x, y, scale)
                13 -> link(x, y, scale)
                14 -> bicycle(x, y, scale)
                15 -> map(x, y, scale)
                16 -> palette(x, y, scale)
                17 -> bulb(x, y, scale)
                18 -> guitar(x, y, scale)
                19 -> rocket(x, y, scale)
                20 -> flowers(x, y, scale)
                21 -> clouds(x, y, scale)
                23 -> sun(x, y, scale)
                24 -> plane(x, y, scale)
                25 -> palm(x, y, scale)
                26 -> gift(x, y, scale)
                27 -> trophy(x, y, scale)
                28 -> crown(x, y, scale)
                29 -> medal(x, y, scale)
                30 -> confetti(x, y, scale)
                31 -> fireworks(x, y, scale)
                32 -> waves(x, y, scale)
                33 -> boat(x, y, scale)
                34 -> people(x, y, scale)
                35 -> shell(x, y, scale)
                36 -> fish(x, y, scale)
                37 -> compass(x, y, scale)
                38 -> suitcase(x, y, scale)
                39 -> waves(x, y, scale)
                else -> smile(x, y, scale)
            }
        }

        fun fynxMark(x: Float, y: Float, s: Float) {
            val w = 7f * s
            val h = 18f * s
            fun seg(ax: Float, ay: Float, bx: Float, by: Float) = line(p(x + ax, y + ay), p(x + bx, y + by))
            seg(0f, 0f, 0f, h); seg(0f, 0f, w, 0f); seg(0f, 8f*s, 5f*s, 8f*s)
            seg(10f*s, 0f, 14f*s, 8f*s); seg(18f*s, 0f, 14f*s, 8f*s); seg(14f*s, 8f*s, 14f*s, h)
            seg(24f*s, h, 24f*s, 0f); seg(24f*s, 0f, 31f*s, h); seg(31f*s, h, 31f*s, 0f)
            seg(37f*s, 0f, 44f*s, h); seg(44f*s, 0f, 37f*s, h)
        }

        fun tinyFillers(x: Float, y: Float, s: Float) {
            circle(x, y, 1.4f * s)
            circle(x + 9f*s, y + 5f*s, 0.9f * s)
            line(p(x + 14f*s, y), p(x + 18f*s, y + 4f*s))
            line(p(x + 18f*s, y), p(x + 14f*s, y + 4f*s))
        }

        val placements = listOf(
            Triple(30f, 28f, 0.78f), Triple(166f, 4f, 0.62f), Triple(308f, 48f, 0.70f),
            Triple(86f, 126f, 0.66f), Triple(244f, 142f, 0.76f), Triple(366f, 106f, 0.58f),
            Triple(14f, 230f, 0.64f), Triple(142f, 270f, 0.72f), Triple(294f, 232f, 0.62f),
            Triple(48f, 376f, 0.70f), Triple(206f, 344f, 0.60f), Triple(352f, 404f, 0.72f),
            Triple(118f, 470f, 0.62f), Triple(270f, 486f, 0.68f)
        )

        val extraPlacements = listOf(
            Triple(18f, 74f, 0.46f), Triple(118f, 52f, 0.48f), Triple(218f, 38f, 0.44f),
            Triple(346f, 74f, 0.50f), Triple(72f, 196f, 0.46f), Triple(190f, 204f, 0.48f),
            Triple(328f, 250f, 0.44f), Triple(104f, 328f, 0.48f), Triple(238f, 382f, 0.46f),
            Triple(314f, 344f, 0.44f), Triple(18f, 438f, 0.46f), Triple(166f, 430f, 0.44f)
        )
        val isExpandedTheme = activePalette.id == FynxGlassThemeId.SUNSET ||
            activePalette.id == FynxGlassThemeId.ROSE ||
            activePalette.id == FynxGlassThemeId.GOLDEN ||
            activePalette.id == FynxGlassThemeId.TURQUOISE
        val activePlacements = if (isExpandedTheme) placements + extraPlacements else placements

        val tilesX = (size.width / tileW).toInt() + 2
        val tilesY = (size.height / tileH).toInt() + 2
        for (tx in -1 until tilesX) {
            for (ty in -1 until tilesY) {
                val offsetX = tx * tileW
                val offsetY = ty * tileH
                activePlacements.forEachIndexed { index, (x, y, scale) ->
                    if (index >= (activePlacements.size * density.coerceIn(0.5f, 1.5f)).toInt().coerceAtMost(activePlacements.size)) return@forEachIndexed
                    val rotation = when ((index + tx * 3 + ty * 5) and 3) {
                        0 -> -12f
                        1 -> -4f
                        2 -> 7f
                        else -> 14f
                    }
                    val px = offsetX + x.dp.toPx()
                    val py = offsetY + y.dp.toPx()
                    withTransform({ rotate(rotation, pivot = p(px, py)) }) {
                        drawMotif(index + tx * 7 + ty * 11, px, py, scale * scaleMultiplier.coerceIn(0.7f, 1.3f), activePalette.id)
                    }
                }

                val markX = offsetX + when (activePalette.id) {
                    FynxGlassThemeId.PURE_BLACK -> 318.dp.toPx()
                    FynxGlassThemeId.AURORA -> 294.dp.toPx()
                    FynxGlassThemeId.LIGHT -> 336.dp.toPx()
                    FynxGlassThemeId.EMERALD -> 306.dp.toPx()
                }
                val markY = offsetY + 286.dp.toPx()
                withTransform({ rotate(-6f + ((tx + ty) and 2) * 3f, pivot = p(markX, markY)) }) {
                    fynxMark(markX, markY, 0.40f)
                }
                val fillers = listOf(
                    p(offsetX + 54.dp.toPx(), offsetY + 82.dp.toPx()),
                    p(offsetX + 214.dp.toPx(), offsetY + 92.dp.toPx()),
                    p(offsetX + 338.dp.toPx(), offsetY + 188.dp.toPx()),
                    p(offsetX + 72.dp.toPx(), offsetY + 314.dp.toPx()),
                    p(offsetX + 252.dp.toPx(), offsetY + 300.dp.toPx()),
                    p(offsetX + 326.dp.toPx(), offsetY + 458.dp.toPx())
                )
                fillers.forEachIndexed { i, point ->
                    tinyFillers(point.x, point.y, 0.70f + (i % 2) * 0.12f)
                }
            }
        }
    }
}

@Composable
fun FynxChatPersonalizationDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var wallpaper by remember { mutableStateOf(FynxPreferencesStore.loadChatWallpaper(context)) }
    var listView by remember { mutableStateOf(FynxPreferencesStore.loadChatListView(context)) }
    var nightMode by remember { mutableStateOf(FynxPreferencesStore.loadNightMode(context)) }
    var stickerAnimation by remember { mutableStateOf(FynxPreferencesStore.loadStickerAnimation(context)) }
    var emojiSize by remember { mutableStateOf(FynxPreferencesStore.loadEmojiSize(context)) }
    var section by remember { mutableStateOf("Appearance") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat & personalization") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TabRow(selectedTabIndex = if (section == "Appearance") 0 else 1) {
                    Tab(selected = section == "Appearance", onClick = { section = "Appearance" }, text = { Text("Appearance") })
                    Tab(selected = section == "Chat", onClick = { section = "Chat" }, text = { Text("Chat") })
                }
                if (section == "Appearance") {
                    Text("Chat wallpaper", style = MaterialTheme.typography.titleMedium)
                    FynxChatWallpaperOptions.forEach { option ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(option)
                            RadioButton(selected = wallpaper == option, onClick = { wallpaper = option; FynxPreferencesStore.saveChatWallpaper(context, option) })
                        }
                    }
                    HorizontalDivider()
                    Text("Automatic night mode", style = MaterialTheme.typography.titleMedium)
                    listOf("Follow system", "Off", "Scheduled").forEach { option ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(option)
                            RadioButton(selected = nightMode == option, onClick = { nightMode = option; FynxPreferencesStore.saveNightMode(context, option) })
                        }
                    }
                } else {
                    Text("Chat list view", style = MaterialTheme.typography.titleMedium)
                    listOf("Comfortable", "Compact", "Large").forEach { option ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(option)
                            RadioButton(selected = listView == option, onClick = { listView = option; FynxPreferencesStore.saveChatListView(context, option) })
                        }
                    }
                    HorizontalDivider()
                    Text("Stickers & Emoji", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Sticker animation")
                        Switch(checked = stickerAnimation, onCheckedChange = { stickerAnimation = it; FynxPreferencesStore.saveStickerAnimation(context, it) })
                    }
                    listOf("Small", "Normal", "Large").forEach { option ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Emoji $option")
                            RadioButton(selected = emojiSize == option, onClick = { emojiSize = option; FynxPreferencesStore.saveEmojiSize(context, option) })
                        }
                    }
                    HorizontalDivider()
                    Text("Language", style = MaterialTheme.typography.titleMedium)
                    Text("English", style = MaterialTheme.typography.bodyLarge)
                    Text("Additional languages will appear here when full FYNX translations are available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
fun AppearanceDialog(current: String, onSelected: (String) -> Unit, onDismiss: () -> Unit) {
    val options = listOf("System", "Light", "Charcoal Black", "Dark", "Black AMOLED")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Appearance") }, text = {
        Column { options.forEach { option -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(option); if (option == "Charcoal Black") Text("Mature charcoal surfaces with soft contrast", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall); if (option == "Black AMOLED") Text("Pure black AMOLED surfaces with white text", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }; RadioButton(selected = current == option, onClick = { onSelected(option) }) } } }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
fun AccentDialog(current: FynxAccent, onSelected: (FynxAccent) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Colors & accent") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            FynxAccent.entries.forEach { option ->
                val label = when (option) {
                    FynxAccent.Charcoal -> "Charcoal Black"
                    FynxAccent.Blue -> "FYNX Blue"
                    FynxAccent.Purple -> "FYNX Purple"
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(28.dp).background(option.primary, androidx.compose.foundation.shape.CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(label)
                            Text(
                                "Used across FYNX controls and highlights",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    RadioButton(selected = current == option, onClick = { onSelected(option) })
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
fun ChatPersonalizationDialog(settings: FynxSettings, onSettingsChange: (FynxSettings) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Chat settings") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Read receipts"); Switch(checked = settings.readReceipts, onCheckedChange = { onSettingsChange(settings.copy(readReceipts = it)) }) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Story replies"); Switch(checked = settings.storyReplies, onCheckedChange = { onSettingsChange(settings.copy(storyReplies = it)) }) }
            Text("More chat appearance and personalization options", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
