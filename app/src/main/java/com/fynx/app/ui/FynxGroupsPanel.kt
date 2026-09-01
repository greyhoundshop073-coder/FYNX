package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.UUID

@Composable
fun FynxGroupsPanel(onOpenGroup: (String) -> Unit = {}) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var groups by remember { mutableStateOf(FynxGroupsStore.load(context)) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val visible = groups.filter { it.name.contains(query, true) || it.description.contains(query, true) }
    Column(Modifier.fillMaxSize().background(FynxDesign.Background).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Groups", style = MaterialTheme.typography.headlineSmall); Text("Your communities in one place", color = MaterialTheme.colorScheme.onSurfaceVariant) }; FilledIconButton(onClick = { showCreateDialog = true }) { Icon(Icons.Default.Add, "Create group") } }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, "Search groups") }, placeholder = { Text("Search groups…") }, shape = MaterialTheme.shapes.large)
        Spacer(Modifier.height(14.dp))
        if (visible.isEmpty()) Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) { Text("No groups found. Try another search or create a group.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(visible, key = { it.id }) { group -> Card(onClick = { onOpenGroup(group.name) }, modifier = Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Default.Group, "Group", tint = MaterialTheme.colorScheme.primary) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(group.name, style = MaterialTheme.typography.titleMedium); Text("${group.members.size} members • ${group.visibility.name.lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(3.dp)); Text(group.description, maxLines = 1, style = MaterialTheme.typography.bodyMedium) } } } } }
    }
    if (showCreateDialog) FynxCreateGroupDialog({ showCreateDialog = false }) { name, description, visibility -> val group = FynxGroup(UUID.randomUUID().toString(), name.trim(), description.trim(), visibility, "@username", listOf(FynxGroupMember("@username", FynxGroupRole.ADMIN))); if (FynxGroupsStore.add(context, group)) { groups = FynxGroupsStore.load(context); showCreateDialog = false } }
}

@Composable private fun FynxCreateGroupDialog(onDismiss: () -> Unit, onCreate: (String, String, FynxGroupVisibility) -> Unit) {
    var name by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }; var visibility by remember { mutableStateOf(FynxGroupVisibility.PRIVATE) }; val canCreate = name.trim().length >= 2 && description.trim().length >= 2
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Create group") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Group name") }); OutlinedTextField(description, { description = it }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(), label = { Text("Description") }); Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(visibility == FynxGroupVisibility.PRIVATE, { visibility = FynxGroupVisibility.PRIVATE }); Text("Private"); Spacer(Modifier.width(8.dp)); RadioButton(visibility == FynxGroupVisibility.PUBLIC, { visibility = FynxGroupVisibility.PUBLIC }); Text("Public") } } }, confirmButton = { TextButton(enabled = canCreate, onClick = { onCreate(name, description, visibility) }) { Text("Create") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun FynxGroupConversationPanel(groupName: String, onBack: () -> Unit) {
    val context = LocalContext.current; var text by remember { mutableStateOf("") }; val group = remember(groupName) { FynxGroupsStore.load(context).firstOrNull { it.name == groupName } }; var currentGroup by remember(groupName) { mutableStateOf(group) }; var messages by remember(groupName) { mutableStateOf(FynxChatStore.load(context, "group_$groupName", ChatMessage("Welcome to $groupName", false, "welcome-${groupName.hashCode()}"))) }; var showMembers by remember { mutableStateOf(false) }; var showSettings by remember { mutableStateOf(false) }; var showTools by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(FynxDesign.Background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Text("‹") }; Text(groupName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); IconButton(enabled = currentGroup != null, onClick = { showTools = true }) { Icon(Icons.Default.MoreVert, "Group tools") }; IconButton(enabled = currentGroup != null, onClick = { showMembers = true }) { Icon(Icons.Default.Group, "Members") }; IconButton(enabled = currentGroup != null, onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Group settings") } }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(messages, key = { it.id }) { message -> Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large) { Column(Modifier.padding(12.dp)) { Text(message.text); if (message.attachmentUri != null) Text("📷 Photo attached", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) } } } }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) { OutlinedTextField(text, { text = it }, modifier = Modifier.weight(1f), placeholder = { Text("Message…") }, maxLines = 4, shape = MaterialTheme.shapes.large); Spacer(Modifier.width(6.dp)); IconButton(onClick = { val trimmed = text.trim(); if (trimmed.isNotEmpty()) { val next = messages + ChatMessage(trimmed, true, UUID.randomUUID().toString(), delivered = true, read = true); messages = next; FynxChatStore.save(context, "group_$groupName", next); text = "" } }) { Icon(Icons.Default.Send, "Send") } }
    }
    currentGroup?.let { selectedGroup ->
        if (showMembers) FynxGroupMembersDialog(selectedGroup, { showMembers = false }) { updated -> if (FynxGroupsStore.updateGroup(context, updated)) currentGroup = updated }
        if (showSettings) FynxGroupSettingsDialog(selectedGroup.id, { showSettings = false })
        if (showTools) FynxGroupSocialDialog(selectedGroup, { showTools = false }, onInvite = { username -> val updated = if (selectedGroup.members.any { it.username.equals(username, true) }) selectedGroup else selectedGroup.copy(members = selectedGroup.members + FynxGroupMember(username)); if (FynxGroupsBatch1.validate(updated).isEmpty()) { FynxGroupsStore.updateGroup(context, updated); currentGroup = updated } }, onMedia = { uri -> val next = messages + createGroupMediaMessage(uri); messages = next; FynxChatStore.save(context, "group_$groupName", next) }, onStoryShare = { val next = messages + ChatMessage("Story shared to $groupName", true, UUID.randomUUID().toString(), delivered = true, read = true); messages = next; FynxChatStore.save(context, "group_$groupName", next) })
    }
}

@Composable private fun FynxGroupMembersDialog(group: FynxGroup, onDismiss: () -> Unit, onGroupChanged: (FynxGroup) -> Unit) {
    val context = LocalContext.current; var current by remember(group.id) { mutableStateOf(group) }; val management = remember(group.id) { mutableStateOf(FynxGroupManagementStore.load(context, group.id)) }; var username by remember { mutableStateOf("") }; var message by remember { mutableStateOf("") }; val myRole = current.members.firstOrNull { it.username == "@username" }?.role ?: FynxGroupRole.MEMBER; val canManage = FynxGroupsBatch3.canManage(myRole)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Members • ${current.members.size}") }, text = { Column {
        if (canManage) { Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(username, { username = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Username") }); IconButton(onClick = { val clean = username.trim(); if (clean.isBlank() || current.members.any { it.username.equals(clean, true) }) message = "Enter a new username" else { val updated = current.copy(members = current.members + FynxGroupMember(clean)); if (FynxGroupsBatch1.validate(updated).isEmpty()) { current = updated; onGroupChanged(updated); username = ""; message = "Member added" } else message = "Could not add member" } }) { Icon(Icons.Default.Add, "Add member") } }; if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(8.dp)) }
        LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { items(current.members, key = { it.username }) { member -> val blocked = management.value.state.blockedUsernames.contains(member.username); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(member.username, style = MaterialTheme.typography.titleSmall); Text(if (blocked) "Blocked" else member.role.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; if (canManage && member.username != current.ownerUsername) { TextButton(onClick = { val action = if (member.role == FynxGroupRole.MODERATOR) FynxGroupMemberAction.DEMOTE_MODERATOR else FynxGroupMemberAction.PROMOTE_MODERATOR; val nextState = FynxGroupsBatch3.applyAction(management.value.state, member.username, action); if (nextState != null) { management.value = management.value.copy(state = nextState); FynxGroupManagementStore.save(context, management.value); val role = if (member.role == FynxGroupRole.MODERATOR) FynxGroupRole.MEMBER else FynxGroupRole.MODERATOR; val updated = current.copy(members = current.members.map { if (it.username == member.username) it.copy(role = role) else it }); current = updated; onGroupChanged(updated) } }) { Text(if (member.role == FynxGroupRole.MODERATOR) "Demote" else "Mod") }; TextButton(onClick = { val nextState = FynxGroupsBatch3.applyAction(management.value.state, member.username, if (blocked) FynxGroupMemberAction.UNBLOCK else FynxGroupMemberAction.BLOCK); if (nextState != null) { management.value = management.value.copy(state = nextState); FynxGroupManagementStore.save(context, management.value) } }) { Text(if (blocked) "Unblock" else "Block") }; TextButton(onClick = { val updated = current.copy(members = current.members.filterNot { it.username == member.username }); if (FynxGroupsBatch1.validate(updated).isEmpty()) { current = updated; onGroupChanged(updated) } else message = "The group must keep one admin" }) { Text("Remove") } } } } }
    } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable private fun FynxGroupSettingsDialog(groupId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current; var data by remember(groupId) { mutableStateOf(FynxGroupManagementStore.load(context, groupId)) }; fun save(next: FynxGroupManagementStore.GroupManagementData) { data = next; FynxGroupManagementStore.save(context, next) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Group settings") }, text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Permissions", style = MaterialTheme.typography.titleMedium); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Member posts", Modifier.weight(1f)); Switch(data.settings.allowMemberPosts, { save(data.copy(settings = data.settings.copy(allowMemberPosts = it))) }) }; Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Member invites", Modifier.weight(1f)); Switch(data.settings.allowMemberInvites, { save(data.copy(settings = data.settings.copy(allowMemberInvites = it))) }) }; Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Marketplace shares", Modifier.weight(1f)); Switch(data.settings.allowMarketplaceShares, { save(data.copy(settings = data.settings.copy(allowMarketplaceShares = it))) }) }; HorizontalDivider(Modifier.padding(vertical = 6.dp)); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Group notifications", Modifier.weight(1f)); Switch(data.settings.notificationsEnabled, { save(data.copy(settings = data.settings.copy(notificationsEnabled = it))) }) }; if (data.state.moderatorUsernames.isNotEmpty()) Text("Moderators: ${data.state.moderatorUsernames.joinToString()}", style = MaterialTheme.typography.bodySmall); if (data.state.blockedUsernames.isNotEmpty()) Text("Blocked: ${data.state.blockedUsernames.joinToString()}", style = MaterialTheme.typography.bodySmall) } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
