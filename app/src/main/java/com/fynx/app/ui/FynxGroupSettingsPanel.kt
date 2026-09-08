package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Dedicated group settings surface. Conversation-level actions stay out of this screen;
 * group/admin controls belong here so the group chat remains clean and easy to use.
 */
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
    var notifications by remember { mutableStateOf(prefs.getBoolean("notifications", true)) }
    var mute by remember { mutableStateOf(prefs.getBoolean("mute", false)) }
    var sendMessages by remember { mutableStateOf(prefs.getBoolean("send_messages", true)) }
    var sendMedia by remember { mutableStateOf(prefs.getBoolean("send_media", true)) }
    var addMembers by remember { mutableStateOf(prefs.getBoolean("add_members", true)) }
    var inviteLinks by remember { mutableStateOf(prefs.getBoolean("invite_links", true)) }
    var saveMedia by remember { mutableStateOf(prefs.getBoolean("save_media", false)) }
    var chatHistory by remember { mutableStateOf(prefs.getBoolean("chat_history", true)) }
    var appearance by remember { mutableStateOf(prefs.getString("appearance", "FYNX Default") ?: "FYNX Default") }

    fun save(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(groupName, style = MaterialTheme.typography.titleLarge)
                Text("Group settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }

        GroupSettingsSection("Notifications", Icons.Default.Notifications) {
            GroupSwitchRow("Group notifications", notifications) {
                notifications = it
                save("notifications", it)
            }
            GroupSwitchRow("Mute notifications", mute) {
                mute = it
                save("mute", it)
            }
        }

        GroupSettingsSection("Permissions", Icons.Default.Security) {
            GroupSwitchRow("Members can send messages", sendMessages, enabled = isAdmin) {
                sendMessages = it
                save("send_messages", it)
            }
            GroupSwitchRow("Members can send media and files", sendMedia, enabled = isAdmin) {
                sendMedia = it
                save("send_media", it)
            }
            GroupSwitchRow("Members can add people", addMembers, enabled = isAdmin) {
                addMembers = it
                save("add_members", it)
            }
            GroupSwitchRow("Invite links", inviteLinks, enabled = isAdmin) {
                inviteLinks = it
                save("invite_links", it)
            }
        }

        GroupSettingsSection("Media & history", Icons.Default.Settings) {
            GroupSwitchRow("Save received media", saveMedia) {
                saveMedia = it
                save("save_media", it)
            }
            GroupSwitchRow("Show chat history to new members", chatHistory, enabled = isAdmin) {
                chatHistory = it
                save("chat_history", it)
            }
        }

        GroupSettingsSection("Appearance", Icons.Default.Settings) {
            Text("Chat appearance", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("FYNX Default", "Light", "Dark").forEach { option ->
                    FilterChip(
                        selected = appearance == option,
                        onClick = {
                            appearance = option
                            prefs.edit().putString("appearance", option).apply()
                        },
                        label = { Text(option) }
                    )
                }
            }
        }

        if (!isAdmin) {
            Text(
                "Admin-only group controls are shown only to administrators.",
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GroupSettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
