package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val FynxChatWallpaperOptions = listOf("FYNX Default", "Midnight", "Aurora", "Sunrise", "Ocean", "Minimal")

@Composable
fun FynxChatWallpaperBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val wallpaper = FynxPreferencesStore.loadChatWallpaper(context)
    val base = when (wallpaper) {
        "Midnight" -> Color(0xFF171A20)
        "Aurora" -> Color(0xFF182323)
        "Sunrise" -> Color(0xFF211D21)
        "Ocean" -> Color(0xFF17242B)
        "Minimal" -> MaterialTheme.colorScheme.background
        else -> Color(0xFF202326)
    }
    Box(modifier.background(base)) {
        if (wallpaper == "FYNX Default") FynxChatDoodlePattern()
        content()
    }
}

@Composable
private fun FynxChatDoodlePattern() {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val ink = Color.White.copy(alpha = 0.052f)
        val sw = 1.1.dp.toPx()
        val w = 210.dp.toPx()
        val h = 175.dp.toPx()
        fun line(a: androidx.compose.ui.geometry.Offset, b: androidx.compose.ui.geometry.Offset) =
            drawLine(ink, a, b, sw)
        fun circle(x: Float, y: Float, r: Float) =
            drawCircle(color = ink, radius = r, center = androidx.compose.ui.geometry.Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(width = sw))
        fun bubble(x: Float, y: Float, s: Float) {
            drawRoundRect(color = ink, topLeft = androidx.compose.ui.geometry.Offset(x, y), size = androidx.compose.ui.geometry.Size(42*s, 28*s), cornerRadius = androidx.compose.ui.geometry.CornerRadius(9*s, 9*s), style = androidx.compose.ui.graphics.drawscope.Stroke(width = sw))
            line(androidx.compose.ui.geometry.Offset(x+8*s,y+28*s),
                androidx.compose.ui.geometry.Offset(x+5*s,y+36*s))
        }
        fun camera(x: Float, y: Float, s: Float) {
            drawRect(color = ink, topLeft = androidx.compose.ui.geometry.Offset(x,y), size = androidx.compose.ui.geometry.Size(42*s,30*s), style = androidx.compose.ui.graphics.drawscope.Stroke(width = sw))
            circle(x+21*s,y+15*s,7*s)
            line(androidx.compose.ui.geometry.Offset(x+8*s,y),
                androidx.compose.ui.geometry.Offset(x+14*s,y-6*s))
        }
        fun bicycle(x: Float, y: Float, s: Float) {
            circle(x,y,13*s); circle(x+42*s,y,13*s)
            line(androidx.compose.ui.geometry.Offset(x,y),
                androidx.compose.ui.geometry.Offset(x+18*s,y-20*s))
            line(androidx.compose.ui.geometry.Offset(x+18*s,y-20*s),
                androidx.compose.ui.geometry.Offset(x+42*s,y))
            line(androidx.compose.ui.geometry.Offset(x,y),
                androidx.compose.ui.geometry.Offset(x+34*s,y))
        }
        fun pin(x: Float, y: Float, s: Float) {
            circle(x,y,7*s)
            line(androidx.compose.ui.geometry.Offset(x-7*s,y+2*s),
                androidx.compose.ui.geometry.Offset(x,y+19*s))
            line(androidx.compose.ui.geometry.Offset(x,y+19*s),
                androidx.compose.ui.geometry.Offset(x+7*s,y+2*s))
        }
        fun house(x: Float, y: Float, s: Float) {
            line(androidx.compose.ui.geometry.Offset(x,y+18*s),
                androidx.compose.ui.geometry.Offset(x+21*s,y))
            line(androidx.compose.ui.geometry.Offset(x+21*s,y),
                androidx.compose.ui.geometry.Offset(x+42*s,y+18*s))
            line(androidx.compose.ui.geometry.Offset(x,y+18*s),
                androidx.compose.ui.geometry.Offset(x,y+43*s))
            line(androidx.compose.ui.geometry.Offset(x+42*s,y+18*s),
                androidx.compose.ui.geometry.Offset(x+42*s,y+43*s))
            line(androidx.compose.ui.geometry.Offset(x,y+43*s),
                androidx.compose.ui.geometry.Offset(x+42*s,y+43*s))
        }
        fun fynx(x: Float, y: Float) {
            line(androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x, y + 18))
            line(androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x + 11, y))
            line(androidx.compose.ui.geometry.Offset(x, y + 8), androidx.compose.ui.geometry.Offset(x + 8, y + 8))
            line(androidx.compose.ui.geometry.Offset(x + 15, y), androidx.compose.ui.geometry.Offset(x + 15, y + 18))
            line(androidx.compose.ui.geometry.Offset(x + 15, y), androidx.compose.ui.geometry.Offset(x + 25, y))
            line(androidx.compose.ui.geometry.Offset(x + 15, y + 9), androidx.compose.ui.geometry.Offset(x + 23, y + 9))
            line(androidx.compose.ui.geometry.Offset(x + 15, y + 18), androidx.compose.ui.geometry.Offset(x + 25, y + 18))
            line(androidx.compose.ui.geometry.Offset(x + 29, y), androidx.compose.ui.geometry.Offset(x + 41, y + 18))
            line(androidx.compose.ui.geometry.Offset(x + 41, y), androidx.compose.ui.geometry.Offset(x + 29, y + 18))
        }
        var row = 0
        var y = -30f
        while (y < size.height + h) {
            var col = 0
            var x = if (row % 2 == 0) -45f else -145f
            while (x < size.width + w) {
                when ((row * 5 + col) % 10) {
                    0 -> bicycle(x, y+58, .62f)
                    1 -> camera(x+25, y+20, .68f)
                    2 -> pin(x+52, y+48, .72f)
                    3 -> bubble(x+18, y+55, .72f)
                    4 -> house(x+10, y+20, .68f)
                    5 -> fynx(x+20, y+50)
                    6 -> { circle(x+34,y+45,14f); line(androidx.compose.ui.geometry.Offset(x+20,y+45),androidx.compose.ui.geometry.Offset(x+48,y+45)) }
                    7 -> { line(androidx.compose.ui.geometry.Offset(x+10,y+62),androidx.compose.ui.geometry.Offset(x+30,y+42)); line(androidx.compose.ui.geometry.Offset(x+30,y+42),androidx.compose.ui.geometry.Offset(x+50,y+62)) }
                    8 -> { circle(x+28,y+42,8f); line(androidx.compose.ui.geometry.Offset(x+28,y+42),androidx.compose.ui.geometry.Offset(x+28,y+25)) }
                    else -> { circle(x+30,y+45,10f); line(androidx.compose.ui.geometry.Offset(x+20,y+35),androidx.compose.ui.geometry.Offset(x+40,y+55)); line(androidx.compose.ui.geometry.Offset(x+40,y+35),androidx.compose.ui.geometry.Offset(x+20,y+55)) }
                }
                x += w
                col++
            }
            y += h
            row++
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
    val options = listOf("System", "Light", "Dark")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Appearance") }, text = {
        Column { options.forEach { option -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(option); RadioButton(selected = current == option, onClick = { onSelected(option) }) } } }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
fun AccentDialog(current: FynxAccent, onSelected: (FynxAccent) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Colors & accent") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            FynxAccent.entries.forEach { option ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(option.name); RadioButton(selected = current == option, onClick = { onSelected(option) }) }
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
