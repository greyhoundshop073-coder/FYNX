package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FynxChatSettingsPanel(chatUsername: String, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var muted by rememberSaveable(chatUsername) { mutableStateOf(FynxPreferencesStore.isChatMuted(context, chatUsername)) }
    var showClearDialog by rememberSaveable(chatUsername) { mutableStateOf(false) }
    var showResetDialog by rememberSaveable(chatUsername) { mutableStateOf(false) }
    var wallpaper by rememberSaveable(chatUsername) { mutableStateOf(FynxConversationPreferences.chatWallpaper(context, chatUsername)) }
    val textSizeOptions = listOf("Small" to 14f, "Medium" to 16f, "Large" to 18f, "Extra Large" to 20f)
    var textSize by rememberSaveable(chatUsername) { mutableStateOf(FynxConversationPreferences.chatTextSize(context, chatUsername)) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear local chat history?") },
            text = { Text("This removes the saved copy of this conversation from this device. It does not delete messages from the FYNX server.") },
            confirmButton = {
                TextButton(onClick = {
                    FynxChatStore.clear(context, chatUsername)
                    showClearDialog = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset chat settings?") },
            text = { Text("The chat notification preference will return to its FYNX default.") },
            confirmButton = {
                TextButton(onClick = {
                    FynxPreferencesStore.setChatMuted(context, chatUsername, false)
                    muted = false
                    showResetDialog = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
    }

    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, tonalElevation = 0.dp) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(52.dp)) { Icon(Icons.Default.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(26.dp)) }
                Column(Modifier.weight(1f)) {
                    Text("Chat Settings", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(chatUsername, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            }
        }

        ChatSettingsSection("Notifications", Icons.Default.Notifications) {
            ChatSwitchRow(
                "Mute notifications",
                "Keep this chat quiet without hiding the conversation",
                muted
            ) {
                muted = it
                FynxPreferencesStore.setChatMuted(context, chatUsername, it)
            }
            Text(
                "Other notification controls are managed by FYNX notification settings until their runtime behavior is connected.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ChatSettingsSection("Appearance", Icons.Default.RestartAlt) {
            Text("Message text size", style = MaterialTheme.typography.titleMedium)
            val selectedTextSize = textSizeOptions.firstOrNull { it.first == textSize } ?: textSizeOptions[1]
            Text(selectedTextSize.first, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = selectedTextSize.second,
                onValueChange = { value ->
                    val nearest = textSizeOptions.minByOrNull { kotlin.math.abs(it.second - value) }?.first ?: "Medium"
                    textSize = nearest
                    FynxConversationPreferences.setChatTextSize(context, chatUsername, nearest)
                },
                valueRange = 14f..20f,
                steps = 2,
                modifier = Modifier.fillMaxWidth()
            )
            val previewTheme = FynxGlassThemeId.entries.firstOrNull { it.label == wallpaper }
                ?: FynxGlassThemeId.PURE_BLACK
            val previewPalette = fynxGlassPalette(previewTheme)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(18.dp))
            ) {
                FynxChatWallpaperBackground(wallpaperOverride = wallpaper) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Live preview",
                            style = MaterialTheme.typography.labelMedium,
                            color = previewPalette.doodleHighlight
                        )
                        GlassPreviewBubble(
                            text = "Hello! This is how your messages will look.",
                            fontSizeSp = selectedTextSize.second,
                            palette = previewPalette,
                            outgoing = false
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            GlassPreviewBubble(
                                text = "Looks good.",
                                fontSizeSp = selectedTextSize.second,
                                palette = previewPalette,
                                outgoing = true
                            )
                        }
                        Text(
                            "Preview updates instantly with text size and wallpaper.",
                            style = MaterialTheme.typography.labelSmall,
                            color = previewPalette.messageMuted
                        )
                    }
                }
            }
            Text("Chat wallpaper", style = MaterialTheme.typography.titleMedium)
            FynxGlassThemeId.entries.map { it.label }.forEach { option ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(option)
                    RadioButton(
                        selected = wallpaper == option,
                        onClick = {
                            wallpaper = option
                            FynxConversationPreferences.setChatWallpaper(context, chatUsername, option)
                        }
                    )
                }
            }
        }

        ChatSettingsSection("Chat Management", Icons.Default.DeleteOutline) {
            ChatActionRow(
                "Clear local chat history",
                "Remove the saved conversation from this device; server messages remain available",
                Icons.Default.DeleteOutline
            ) { showClearDialog = true }
            ChatActionRow(
                "Reset chat settings",
                "Restore this chat's supported local settings to FYNX defaults",
                Icons.Default.RestartAlt
            ) { showResetDialog = true }
        }

        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun GlassPreviewBubble(
    text: String,
    fontSizeSp: Float,
    palette: FynxGlassThemePalette,
    outgoing: Boolean
) {
    val shape = RoundedCornerShape(16.dp)
    val brush = if (outgoing) {
        Brush.horizontalGradient(listOf(palette.outgoingStart, palette.outgoingEnd))
    } else {
        Brush.linearGradient(listOf(palette.incomingGlass, palette.backgroundMid.copy(alpha = 0.92f)))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth(if (outgoing) 0.78f else 0.84f)
            .background(brush, shape)
            .border(1.dp, palette.bubbleRim.copy(alpha = 0.58f), shape)
    ) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {
            Text(
                text,
                fontSize = fontSizeSp.sp,
                color = palette.messageText
            )
            Text(
                if (outgoing) "20:42  ✓✓" else "20:41",
                style = MaterialTheme.typography.labelSmall,
                color = palette.messageMuted,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun ChatSettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 10.dp))
        }
        content()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun ChatSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ChatActionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Column(Modifier.weight(1f).padding(start = 12.dp), horizontalAlignment = Alignment.Start) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}