package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Dedicated, grouped settings surface for one group. */
@Composable
fun FynxGroupSettingsPanel(
    groupId: String,
    groupName: String = "Group",
    isAdmin: Boolean = false,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember(groupId) {
        context.getSharedPreferences("fynx_group_settings_$groupId", Context.MODE_PRIVATE)
    }
    var notifications by remember(groupId) { mutableStateOf(prefs.getBoolean("notifications", true)) }
    var mute by remember(groupId) { mutableStateOf(prefs.getBoolean("mute", false)) }
    var sendMessages by remember(groupId) { mutableStateOf(prefs.getBoolean("send_messages", true)) }
    var sendMedia by remember(groupId) { mutableStateOf(prefs.getBoolean("send_media", true)) }
    var addMembers by remember(groupId) { mutableStateOf(prefs.getBoolean("add_members", true)) }
    var inviteLinks by remember(groupId) { mutableStateOf(prefs.getBoolean("invite_links", true)) }
    var saveMedia by remember(groupId) { mutableStateOf(prefs.getBoolean("save_media", false)) }
    var chatHistory by remember(groupId) { mutableStateOf(prefs.getBoolean("chat_history", true)) }
    var appearance by remember(groupId) { mutableStateOf(prefs.getString("appearance", "FYNX Default") ?: "FYNX Default") }
    var showWallpaper by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    fun save(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear local group history?") },
            text = { Text("This removes the saved copy of this group's conversation on this device. It does not delete messages from the FYNX server.") },
            confirmButton = {
                TextButton(onClick = {
                    FynxChatStore.clear(context, "group_$groupId")
                    showClearDialog = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset group settings?") },
            text = { Text("All settings for this group will return to their FYNX defaults.") },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().clear().apply()
                    notifications = true
                    mute = false
                    sendMessages = true
                    sendMedia = true
                    addMembers = true
                    inviteLinks = true
                    saveMedia = false
                    chatHistory = true
                    appearance = "FYNX Default"
                    showResetDialog = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text(groupName, style = MaterialTheme.typography.titleLarge)
                Text("Group settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.primary)
        }

        GroupSettingsSection("Notifications", Icons.Default.Notifications) {
            GroupSwitchRow("Group notifications", notifications) { notifications = it; save("notifications", it) }
            GroupSwitchRow("Mute notifications", mute) { mute = it; save("mute", it) }
        }

        GroupSettingsSection("Permissions", Icons.Default.Security) {
            GroupSwitchRow("Members can send messages", sendMessages, isAdmin) { sendMessages = it; save("send_messages", it) }
            GroupSwitchRow("Members can send media and files", sendMedia, isAdmin) { sendMedia = it; save("send_media", it) }
            GroupSwitchRow("Members can add people", addMembers, isAdmin) { addMembers = it; save("add_members", it) }
            GroupSwitchRow("Invite links", inviteLinks, isAdmin) { inviteLinks = it; save("invite_links", it) }
        }

        GroupSettingsSection("Media & history", Icons.Default.Settings) {
            GroupSwitchRow("Save received media", saveMedia) { saveMedia = it; save("save_media", it) }
            GroupSwitchRow("Show chat history to new members", chatHistory, isAdmin) { chatHistory = it; save("chat_history", it) }
        }

        GroupSettingsSection("Appearance", Icons.Default.Settings) {
            Text("Chat appearance", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("FYNX Default", "Light", "Dark").forEach { option ->
                    FilterChip(
                        selected = appearance == option,
                        onClick = { appearance = option; prefs.edit().putString("appearance", option).apply() },
                        label = { Text(option) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showWallpaper = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Group wallpaper")
            }
        }

        GroupSettingsSection("Group management", Icons.Default.Settings) {
            GroupActionRow(
                "Clear local group history",
                "Remove the saved conversation from this device",
                Icons.Default.DeleteOutline
            ) { showClearDialog = true }
            GroupActionRow(
                "Reset group settings",
                "Restore FYNX defaults for this group",
                Icons.Default.RestartAlt
            ) { showResetDialog = true }
        }

        if (!isAdmin) {
            Text(
                "Admin-only permissions are locked for members.",
                Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showWallpaper) FynxGroupWallpaperDialog(groupId) { showWallpaper = false }
}

@Composable
private fun GroupSettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun GroupSwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun GroupActionRow(
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
