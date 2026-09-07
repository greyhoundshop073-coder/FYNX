package com.fynx.app.ui

import androidx.compose.foundation.background
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
    val wallpaper = remember { FynxPreferencesStore.loadChatWallpaper(context) }
    val brush = when (wallpaper) {
        "Midnight" -> Brush.verticalGradient(listOf(Color(0xFF090D18), Color(0xFF1A2338)))
        "Aurora" -> Brush.verticalGradient(listOf(Color(0xFF092B2A), Color(0xFF14243D)))
        "Sunrise" -> Brush.verticalGradient(listOf(Color(0xFF39241A), Color(0xFF261B36)))
        "Ocean" -> Brush.verticalGradient(listOf(Color(0xFF06243A), Color(0xFF0C4260)))
        "Minimal" -> Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface))
        else -> Brush.verticalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surfaceVariant))
    }
    Box(modifier.background(brush), content = content)
}

@Composable
fun FynxChatPersonalizationDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var wallpaper by remember { mutableStateOf(FynxPreferencesStore.loadChatWallpaper(context)) }
    var listView by remember { mutableStateOf(FynxPreferencesStore.loadChatListView(context)) }
    var nightMode by remember { mutableStateOf(FynxPreferencesStore.loadNightMode(context)) }
    var stickerAnimation by remember { mutableStateOf(FynxPreferencesStore.loadStickerAnimation(context)) }
    var emojiSize by remember { mutableStateOf(FynxPreferencesStore.loadEmojiSize(context)) }
    var language by remember { mutableStateOf(FynxPreferencesStore.loadLanguage(context)) }
    var section by remember { mutableStateOf("Appearance") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat & personalization") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    val languages = listOf("Device default", "English", "French", "Arabic", "Portuguese", "Spanish", "German", "Italian", "Dutch", "Turkish", "Hindi", "Hausa", "Yoruba", "Igbo", "Swahili", "Chinese", "Japanese", "Korean", "Russian")
                    languages.forEach { option ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(option)
                            RadioButton(selected = language == option, onClick = { language = option; FynxPreferencesStore.saveLanguage(context, option) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
