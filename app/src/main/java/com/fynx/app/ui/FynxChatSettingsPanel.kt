package com.fynx.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp

@Composable
fun FynxChatSettingsPanel(chatUsername: String, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var muted by rememberSaveable(chatUsername) { mutableStateOf(FynxPreferencesStore.isChatMuted(context, chatUsername)) }
    var showClearDialog by rememberSaveable(chatUsername) { mutableStateOf(false) }
    var showResetDialog by rememberSaveable(chatUsername) { mutableStateOf(false) }
    var wallpaper by rememberSaveable(chatUsername) { mutableStateOf(FynxConversationPreferences.chatWallpaper(context, chatUsername)) }

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

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Surface(tonalElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text("Chat Settings", style = MaterialTheme.typography.titleLarge)
                    Text(chatUsername, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text("Chat wallpaper", style = MaterialTheme.typography.titleMedium)
            listOf("FYNX Default", "Midnight", "Aurora", "Sunrise", "Ocean", "Minimal").forEach { option ->
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
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp))
        }
        content()
    }
    HorizontalDivider()
}

@Composable
private fun ChatSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
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