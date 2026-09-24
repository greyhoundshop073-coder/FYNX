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

private val FynxChatWallpaperOptions = listOf("FYNX Default", "Midnight", "Aurora", "Sunrise", "Ocean", "Minimal")

@Composable
fun FynxChatWallpaperBackground(modifier: Modifier = Modifier, wallpaperOverride: String? = null, content: @Composable BoxScope.() -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val wallpaper = wallpaperOverride ?: FynxPreferencesStore.loadChatWallpaper(context)
    val base = when (wallpaper) {
        "Minimal" -> Color(0xFF0B0E14)
        else -> Color(0xFF0D0E12)
    }
    Box(modifier.background(base)) {
        FynxChatDoodlePattern()
        content()
    }
}

@Composable
fun FynxChatDoodlePattern() {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val ink = Color(0xFF8A94A6).copy(alpha = 0.085f)
        val sw = 0.72.dp.toPx()
        val tileW = 420.dp.toPx()
        val tileH = 520.dp.toPx()

        fun p(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x, y)
        fun line(a: androidx.compose.ui.geometry.Offset, b: androidx.compose.ui.geometry.Offset) = drawLine(ink, a, b, sw)
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

        fun drawMotif(index: Int, x: Float, y: Float, scale: Float) {
            when (index % 14) {
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
                else -> link(x, y, scale)
            }
        }

        val placements = listOf(
            Triple(30f, 28f, 0.78f), Triple(166f, 4f, 0.62f), Triple(308f, 48f, 0.70f),
            Triple(86f, 126f, 0.66f), Triple(244f, 142f, 0.76f), Triple(366f, 106f, 0.58f),
            Triple(14f, 230f, 0.64f), Triple(142f, 270f, 0.72f), Triple(294f, 232f, 0.62f),
            Triple(48f, 376f, 0.70f), Triple(206f, 344f, 0.60f), Triple(352f, 404f, 0.72f),
            Triple(118f, 470f, 0.62f), Triple(270f, 486f, 0.68f)
        )

        val tilesX = (size.width / tileW).toInt() + 2
        val tilesY = (size.height / tileH).toInt() + 2
        for (tx in -1 until tilesX) {
            for (ty in -1 until tilesY) {
                val offsetX = tx * tileW
                val offsetY = ty * tileH
                placements.forEachIndexed { index, (x, y, scale) ->
                    val rotation = when ((index + tx * 3 + ty * 5) and 3) {
                        0 -> -12f
                        1 -> -4f
                        2 -> 7f
                        else -> 14f
                    }
                    val px = offsetX + x.dp.toPx()
                    val py = offsetY + y.dp.toPx()
                    withTransform({ rotate(rotation, pivot = p(px, py)) }) {
                        drawMotif(index + tx * 7 + ty * 11, px, py, scale)
                    }
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
    val options = listOf("System", "Light", "Charcoal Black", "Dark")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Appearance") }, text = {
        Column { options.forEach { option -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(option); if (option == "Charcoal Black") Text("Mature charcoal surfaces with soft contrast", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }; RadioButton(selected = current == option, onClick = { onSelected(option) }) } } }
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
