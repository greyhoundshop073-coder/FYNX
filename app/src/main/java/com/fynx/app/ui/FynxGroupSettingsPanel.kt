package com.fynx.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
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
import kotlinx.coroutines.launch

/** Dedicated, grouped settings surface for one group. Server permissions are authoritative. */
@Composable
fun FynxGroupSettingsPanel(
    groupId: String,
    groupName: String = "Group",
    isAdmin: Boolean = false,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(groupId) { FynxConversationPreferences.group(context, groupId) }
    val storedGroup = remember(groupId) { FynxGroupsStore.load(context).firstOrNull { it.id == groupId } }
    var currentGroup by remember(groupId) { mutableStateOf(storedGroup) }
    var notifications by remember(groupId) { mutableStateOf(prefs.getBoolean("notifications", true)) }
    var mute by remember(groupId) { mutableStateOf(prefs.getBoolean("mute", false)) }
    var sendMessages by remember(groupId) { mutableStateOf(true) }
    var sendMedia by remember(groupId) { mutableStateOf(true) }
    var addMembers by remember(groupId) { mutableStateOf(true) }
    var inviteLinks by remember(groupId) { mutableStateOf(true) }
    var approveNewMembers by remember(groupId) { mutableStateOf(false) }
    var permissionsLoading by remember(groupId) { mutableStateOf(false) }
    var permissionsSaving by remember(groupId) { mutableStateOf(false) }
    var permissionsMessage by remember(groupId) { mutableStateOf<String?>(null) }
    var saveMedia by remember(groupId) { mutableStateOf(prefs.getBoolean("save_media", false)) }
    var chatHistory by remember(groupId) { mutableStateOf(prefs.getBoolean("chat_history", true)) }
    var appearance by remember(groupId) { mutableStateOf(prefs.getString("appearance", "FYNX Default") ?: "FYNX Default") }
    var showWallpaper by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showEditInfo by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(currentGroup?.name ?: groupName) }
    var editDescription by remember { mutableStateOf(currentGroup?.description.orEmpty()) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var savingInfo by remember { mutableStateOf(false) }
    var inviteMessage by remember { mutableStateOf<String?>(null) }

    fun save(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    LaunchedEffect(groupId, isAdmin) {
        if (!FynxBackendClient.hasAccessToken(context)) return@LaunchedEffect
        permissionsLoading = true
        FynxGroupRemoteClient.loadPermissions(context, groupId)
            .onSuccess { remote ->
                sendMessages = remote.sendMessages
                sendMedia = remote.sendMedia
                addMembers = remote.addMembers
                inviteLinks = remote.inviteLinks
                approveNewMembers = remote.approveNewMembers
                permissionsMessage = null
            }
            .onFailure { permissionsMessage = it.message ?: "Unable to load group permissions." }
        permissionsLoading = false
    }

    fun updatePermissions(next: FynxGroupRemoteClient.RemotePermissions) {
        permissionsSaving = true
        permissionsMessage = null
        scope.launch {
            FynxGroupRemoteClient.updatePermissions(context, groupId, next)
                .onSuccess { remote ->
                    sendMessages = remote.sendMessages
                    sendMedia = remote.sendMedia
                    addMembers = remote.addMembers
                    inviteLinks = remote.inviteLinks
                    approveNewMembers = remote.approveNewMembers
                }
                .onFailure { permissionsMessage = it.message ?: "Group permissions could not be saved." }
            permissionsSaving = false
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear local group history?") },
            text = { Text("This removes the saved copy of this group's conversation on this device. It does not delete messages from the FYNX server.") },
            confirmButton = { TextButton(onClick = { FynxChatStore.clear(context, "group_$groupId"); showClearDialog = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset group settings?") },
            text = { Text("Local notification, media, history and appearance preferences will return to their FYNX defaults. Server permissions are not reset by this local action.") },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().clear().apply()
                    notifications = true; mute = false; saveMedia = false; chatHistory = true; appearance = "FYNX Default"
                    showResetDialog = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
    }

    if (showEditInfo && currentGroup != null) {
        AlertDialog(
            onDismissRequest = { if (!savingInfo) showEditInfo = false },
            title = { Text("Group information") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(editName, { editName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Group name") })
                    OutlinedTextField(editDescription, { editDescription = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4, label = { Text("Description") })
                    infoMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(enabled = !savingInfo, onClick = {
                    val existing = currentGroup ?: return@TextButton
                    val name = editName.trim(); val description = editDescription.trim()
                    if (name.length < 2 || description.length < 2) { infoMessage = "Group name and description must be at least 2 characters."; return@TextButton }
                    val updated = existing.copy(name = name, description = description)
                    if (FynxGroupsBatch1.validate(updated).isNotEmpty()) { infoMessage = "The group information could not be saved."; return@TextButton }
                    savingInfo = true; infoMessage = null
                    if (!FynxGroupsStore.updateGroup(context, updated)) { savingInfo = false; infoMessage = "Could not save group information on this device."; return@TextButton }
                    currentGroup = updated
                    scope.launch {
                        FynxGroupRemoteClient.syncGroup(context, updated)
                            .onSuccess { savingInfo = false; showEditInfo = false }
                            .onFailure { savingInfo = false; infoMessage = it.message ?: "Group information could not sync." }
                    }
                }) { Text(if (savingInfo) "Saving…" else "Save") }
            },
            dismissButton = { TextButton(enabled = !savingInfo, onClick = { showEditInfo = false }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Column(Modifier.weight(1f)) {
                Text(currentGroup?.name ?: groupName, style = MaterialTheme.typography.titleLarge)
                Text("Group settings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.primary)
        }

        GroupSettingsSection("Group information", Icons.Default.Edit) {
            Text(currentGroup?.name ?: groupName, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(3.dp))
            Text(currentGroup?.description ?: "Group information", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isAdmin && currentGroup != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { editName = currentGroup?.name.orEmpty(); editDescription = currentGroup?.description.orEmpty(); infoMessage = null; showEditInfo = true }) {
                    Icon(Icons.Default.Edit, null); Spacer(Modifier.width(8.dp)); Text("Edit group information")
                }
            }
        }

        GroupSettingsSection("Notifications", Icons.Default.Notifications) {
            GroupSwitchRow("Group notifications", notifications) { notifications = it; save("notifications", it) }
            GroupSwitchRow("Mute notifications", mute) { mute = it; save("mute", it) }
        }

        GroupSettingsSection("Permissions", Icons.Default.Security) {
            if (permissionsLoading) Text("Loading server permissions…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            permissionsMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            GroupSwitchRow("Members can send messages", sendMessages, isAdmin && !permissionsSaving) {
                val next = FynxGroupRemoteClient.RemotePermissions(it, sendMedia, addMembers, inviteLinks, approveNewMembers)
                updatePermissions(next)
            }
            GroupSwitchRow("Members can send media and files", sendMedia, isAdmin && !permissionsSaving) {
                val next = FynxGroupRemoteClient.RemotePermissions(sendMessages, it, addMembers, inviteLinks, approveNewMembers)
                updatePermissions(next)
            }
            GroupSwitchRow("Members can add people", addMembers, isAdmin && !permissionsSaving) {
                val next = FynxGroupRemoteClient.RemotePermissions(sendMessages, sendMedia, it, inviteLinks, approveNewMembers)
                updatePermissions(next)
            }
            GroupSwitchRow("Invite links", inviteLinks, isAdmin && !permissionsSaving) {
                val next = FynxGroupRemoteClient.RemotePermissions(sendMessages, sendMedia, addMembers, it, approveNewMembers)
                updatePermissions(next)
                if (!it) inviteMessage = null
            }
            GroupSwitchRow("Approve new members", approveNewMembers, isAdmin && !permissionsSaving) {
                val next = FynxGroupRemoteClient.RemotePermissions(sendMessages, sendMedia, addMembers, inviteLinks, it)
                updatePermissions(next)
            }
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
                    FilterChip(selected = appearance == option, onClick = { appearance = option; prefs.edit().putString("appearance", option).apply() }, label = { Text(option) })
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showWallpaper = true }, modifier = Modifier.fillMaxWidth()) { Text("Group wallpaper") }
        }

        GroupSettingsSection("Group management", Icons.Default.Settings) {
            if (isAdmin && inviteLinks) {
                GroupActionRow("Create invite link", "Generate a shareable link for this group", Icons.Default.Group) {
                    val group = currentGroup
                    if (group == null) inviteMessage = "This group is no longer available."
                    else {
                        val link = FynxGroupsBatch3.createInviteLink(group, group.ownerUsername)
                        if (link == null) inviteMessage = "Only a group admin or moderator can create invite links."
                        else {
                            inviteMessage = link.asShareText()
                            runCatching {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, link.asShareText()) }, "Share group invite"))
                            }.onFailure { inviteMessage = "Invite link created: ${link.asShareText()}" }
                        }
                    }
                }
                inviteMessage?.let { Text(it, Modifier.padding(horizontal = 4.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            GroupActionRow("Clear local group history", "Remove the saved conversation from this device", Icons.Default.DeleteOutline) { showClearDialog = true }
            GroupActionRow("Reset local group settings", "Restore FYNX defaults for local preferences", Icons.Default.RestartAlt) { showResetDialog = true }
        }

        if (!isAdmin) Text("Admin-only permissions are locked for members.", Modifier.padding(horizontal = 4.dp, vertical = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (showWallpaper) FynxGroupWallpaperDialog(groupId) { showWallpaper = false }
}

@Composable
private fun GroupSettingsSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)); Text(title, style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(8.dp)); content()
        }
    }
}

@Composable
private fun GroupSwitchRow(title: String, checked: Boolean, enabled: Boolean = true, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge); Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) }
}

@Composable
private fun GroupActionRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Column(Modifier.weight(1f).padding(start = 12.dp), horizontalAlignment = Alignment.Start) { Text(title, style = MaterialTheme.typography.bodyLarge); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
