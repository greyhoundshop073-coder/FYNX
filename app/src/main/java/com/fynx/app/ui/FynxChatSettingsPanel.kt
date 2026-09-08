package com.fynx.app.ui

import android.content.Context
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
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp

private const val CHAT_PREFS = "fynx_chat_settings"

private fun chatPrefs(context: Context) = context.getSharedPreferences(CHAT_PREFS, Context.MODE_PRIVATE)

@Composable
fun FynxChatSettingsPanel(chatUsername: String, onBack: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(chatUsername) { chatPrefs(context) }
    var notifications by rememberSaveable(chatUsername) { mutableStateOf(prefs.getBoolean("notifications_$chatUsername", true)) }
    var muted by rememberSaveable(chatUsername) { mutableStateOf(FynxPreferencesStore.isChatMuted(context, chatUsername)) }
    var previews by rememberSaveable(chatUsername) { mutableStateOf(prefs.getBoolean("previews_$chatUsername", true)) }
    var sounds by rememberSaveable(chatUsername) { mutableStateOf(prefs.getBoolean("sounds_$chatUsername", true)) }
    var vibration by rememberSaveable(chatUsername) { mutableStateOf(prefs.getBoolean("vibration_$chatUsername", true)) }
    var autoDownload by rememberSaveable(chatUsername) { mutableStateOf(prefs.getBoolean("autodownload_$chatUsername", true)) }
    var saveGallery by rememberSaveable(chatUsername) { mutableStateOf(prefs.getBoolean("gallery_$chatUsername", false)) }
    var linkPreviews by rememberSaveable(chatUsername) { mutableStateOf(prefs.getBoolean("linkpreviews_$chatUsername", true)) }
    var readReceipts by rememberSaveable(chatUsername) { mutableStateOf(prefs.getBoolean("read_$chatUsername", true)) }
    var animations by rememberSaveable(chatUsername) { mutableStateOf(prefs.getBoolean("animations_$chatUsername", true)) }
    var lastSeen by rememberSaveable(chatUsername) { mutableStateOf(prefs.getString("lastseen_$chatUsername", "Everybody") ?: "Everybody") }
    var wallpaper by rememberSaveable(chatUsername) { mutableStateOf(prefs.getString("wallpaper_$chatUsername", "FYNX Default") ?: "FYNX Default") }
    var textSize by rememberSaveable(chatUsername) { mutableStateOf(prefs.getString("textsize_$chatUsername", "Medium") ?: "Medium") }
    var showClearDialog by rememberSaveable(chatUsername) { mutableStateOf(false) }
    var showResetDialog by rememberSaveable(chatUsername) { mutableStateOf(false) }

    fun put(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    fun put(key: String, value: String) = prefs.edit().putString(key, value).apply()

    if (showClearDialog) {
        AlertDialog(onDismissRequest = { showClearDialog = false }, title = { Text("Clear local chat history?") }, text = { Text("This removes the saved copy of this conversation on this device. It does not delete messages from the FYNX server.") }, confirmButton = { TextButton(onClick = { FynxChatStore.clear(context, chatUsername); showClearDialog = false }) { Text("Clear") } }, dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } })
    }
    if (showResetDialog) {
        AlertDialog(onDismissRequest = { showResetDialog = false }, title = { Text("Reset chat settings?") }, text = { Text("All settings for this chat will return to their FYNX defaults.") }, confirmButton = {
            TextButton(onClick = {
                prefs.edit().remove("notifications_$chatUsername").remove("previews_$chatUsername").remove("sounds_$chatUsername").remove("vibration_$chatUsername").remove("autodownload_$chatUsername").remove("gallery_$chatUsername").remove("linkpreviews_$chatUsername").remove("read_$chatUsername").remove("animations_$chatUsername").remove("lastseen_$chatUsername").remove("wallpaper_$chatUsername").remove("textsize_$chatUsername").apply()
                FynxPreferencesStore.setChatMuted(context, chatUsername, false)
                notifications = true; muted = false; previews = true; sounds = true; vibration = true; autoDownload = true; saveGallery = false; linkPreviews = true; readReceipts = true; animations = true; lastSeen = "Everybody"; wallpaper = "FYNX Default"; textSize = "Medium"; showResetDialog = false
            }) { Text("Reset") }
        }, dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } })
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Surface(tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) { Text("Chat Settings", style = MaterialTheme.typography.titleLarge); Text(chatUsername, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        ChatSettingsSection("Notifications", Icons.Default.Notifications) {
            ChatSwitchRow("Notifications", "Messages from this chat", notifications) { notifications = it; put("notifications_$chatUsername", it) }
            ChatSwitchRow("Mute notifications", "Keep the chat quiet without hiding it", muted) { muted = it; FynxPreferencesStore.setChatMuted(context, chatUsername, it) }
            ChatSwitchRow("Message previews", "Show message text in notifications", previews) { previews = it; put("previews_$chatUsername", it) }
            ChatSwitchRow("Sound", "Play notification sounds", sounds) { sounds = it; put("sounds_$chatUsername", it) }
            ChatSwitchRow("Vibration", "Vibrate for new messages", vibration) { vibration = it; put("vibration_$chatUsername", it) }
        }
        ChatSettingsSection("Privacy", Icons.Default.Security) {
            Text("Last seen & online", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                listOf("Everybody", "My contacts", "Nobody").forEach { option -> Column(horizontalAlignment = Alignment.CenterHorizontally) { RadioButton(lastSeen == option, { lastSeen = option; put("lastseen_$chatUsername", option) }); Text(option, style = MaterialTheme.typography.labelSmall) } }
            }
            ChatSwitchRow("Read receipts", "Show when messages have been read", readReceipts) { readReceipts = it; put("read_$chatUsername", it) }
        }
        ChatSettingsSection("Data & Storage", Icons.Default.Storage) {
            ChatSwitchRow("Automatic media download", "Download shared photos and videos automatically", autoDownload) { autoDownload = it; put("autodownload_$chatUsername", it) }
            ChatSwitchRow("Save to gallery", "Save received media to the device gallery", saveGallery) { saveGallery = it; put("gallery_$chatUsername", it) }
            ChatSwitchRow("Link previews", "Show previews for shared links", linkPreviews) { linkPreviews = it; put("linkpreviews_$chatUsername", it) }
        }
        ChatSettingsSection("Chat Appearance", Icons.Default.Palette) {
            Text("Text size", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { listOf("Small", "Medium", "Large").forEach { option -> Column(horizontalAlignment = Alignment.CenterHorizontally) { RadioButton(textSize == option, { textSize = option; put("textsize_$chatUsername", option) }); Text(option, style = MaterialTheme.typography.labelSmall) } } }
            Text("Wallpaper", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { listOf("FYNX Default", "Light", "Dark").forEach { option -> Column(horizontalAlignment = Alignment.CenterHorizontally) { RadioButton(wallpaper == option, { wallpaper = option; put("wallpaper_$chatUsername", option) }); Text(option, style = MaterialTheme.typography.labelSmall) } } }
            ChatSwitchRow("Animations", "Use smooth chat animations", animations) { animations = it; put("animations_$chatUsername", it) }
        }
        ChatSettingsSection("Chat Management", Icons.Default.Storage) {
            ChatActionRow("Clear local chat history", "Remove the saved conversation from this device", Icons.Default.DeleteOutline) { showClearDialog = true }
            ChatActionRow("Reset chat settings", "Restore FYNX defaults for this chat", Icons.Default.RestartAlt) { showResetDialog = true }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

@Composable private fun ChatSettingsSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary); Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 10.dp)) }; content() }
    HorizontalDivider()
}

@Composable private fun ChatSwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f).padding(end = 12.dp)) { Text(title, style = MaterialTheme.typography.bodyLarge); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked = checked, onCheckedChange = onCheckedChange) }
}

@Composable private fun ChatActionRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Icon(icon, contentDescription = null); Column(Modifier.weight(1f).padding(start = 12.dp), horizontalAlignment = Alignment.Start) { Text(title, style = MaterialTheme.typography.bodyLarge); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
}
