package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun FynxGroupsPanel(currentUsername: String = "@preview", onOpenGroup: (String) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var groups by remember { mutableStateOf(FynxGroupsStore.load(context)) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    val visible = groups.filter { it.name.contains(query, true) || it.description.contains(query, true) }

    Column(Modifier.fillMaxSize().background(FynxDesign.Background).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Groups", style = MaterialTheme.typography.headlineSmall)
                Text("Your communities in one place", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledIconButton(onClick = { createError = null; showCreateDialog = true }) {
                Icon(Icons.Default.Add, "Create group")
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, "Search groups") },
            placeholder = { Text("Search groups…") },
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search)
        )
        createError?.let {
            Text(it, modifier = Modifier.padding(top = 7.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(14.dp))
        if (visible.isEmpty()) {
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
                Text("No groups found. Try another search or create a group.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(visible, key = { it.id }) { group ->
                    Card(onClick = { onOpenGroup(group.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Group, "Group", tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(group.name, style = MaterialTheme.typography.titleMedium)
                                Text("${group.members.size} members • ${group.visibility.name.lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(3.dp))
                                Text(group.description, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        FynxCreateGroupDialog({ showCreateDialog = false }) { name, description, visibility ->
            val group = FynxGroup(UUID.randomUUID().toString(), name.trim(), description.trim(), visibility, currentUsername, listOf(FynxGroupMember(currentUsername, FynxGroupRole.ADMIN)))
            scope.launch {
                createError = null
                val remote = FynxGroupRemoteClient.syncGroup(context, group)
                remote.onSuccess {
                    FynxGroupsStore.add(context, group)
                    groups = FynxGroupsStore.load(context)
                    showCreateDialog = false
                }.onFailure {
                    createError = it.message ?: "The group could not be created on FYNX."
                }
            }
        }
    }
}

@Composable
private fun FynxCreateGroupDialog(onDismiss: () -> Unit, onCreate: (String, String, FynxGroupVisibility) -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf(FynxGroupVisibility.PRIVATE) }
    val canCreate = name.trim().length >= 2 && description.trim().length >= 2
    FynxPlainDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create group", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Group name") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next))
                OutlinedTextField(description, { description = it }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(), label = { Text("Description") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Default))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(visibility == FynxGroupVisibility.PRIVATE, { visibility = FynxGroupVisibility.PRIVATE }); Text("Private")
                    Spacer(Modifier.width(8.dp))
                    RadioButton(visibility == FynxGroupVisibility.PUBLIC, { visibility = FynxGroupVisibility.PUBLIC }); Text("Public")
                }
            }
        },
        confirmButton = { TextButton(enabled = canCreate, onClick = { onCreate(name, description, visibility) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun FynxGroupConversationPanel(groupId: String, currentUsername: String = "@preview", onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    val group = remember(groupId) { FynxGroupsStore.load(context).firstOrNull { it.id == groupId } }
    var currentGroup by remember(groupId) { mutableStateOf(group) }
    val groupTitle = currentGroup?.name ?: "Group"
    var messages by remember(groupId) { mutableStateOf(FynxChatStore.load(context, "group_$groupId", null)) }
    var showMembers by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showEmojiPanel by remember { mutableStateOf(false) }
    var reactionMessageId by remember { mutableStateOf<String?>(null) }
    var replyToId by remember { mutableStateOf<String?>(null) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val selectedGroup = currentGroup
    val myRole = selectedGroup?.members?.firstOrNull { it.username.equals(currentUsername, true) }?.role ?: FynxGroupRole.MEMBER
    val isAdmin = myRole == FynxGroupRole.ADMIN
    val prefs = remember(groupId) { FynxConversationPreferences.group(context, groupId) }
    val canSendMessages = isAdmin || prefs.getBoolean("send_messages", true)
    val canSendMedia = isAdmin || prefs.getBoolean("send_media", true)
    val canAddMembers = isAdmin || prefs.getBoolean("add_members", true)
    LaunchedEffect(groupId, currentGroup?.id) {
        val selected = currentGroup ?: return@LaunchedEffect
        FynxGroupRemoteClient.syncGroup(context, selected).onFailure { if (FynxBackendClient.hasAccessToken(context)) syncMessage = it.message }
        FynxGroupRemoteClient.loadMessages(context, groupId).onSuccess { remote ->
            messages = remote.map { FynxGroupRemoteClient.toChatMessage(it, currentUsername, FynxBackendClient.baseUrl(context)) }
            FynxChatStore.save(context, "group_$groupId", messages)
            syncMessage = null
        }.onFailure { if (FynxBackendClient.hasAccessToken(context)) syncMessage = it.message ?: "Unable to sync group messages." }
    }
    if (showSettings && selectedGroup != null) { FynxGroupSettingsPanel(groupId = selectedGroup.id, groupName = selectedGroup.name, isAdmin = isAdmin, onBack = { showSettings = false }); return }
    FynxChatWallpaperBackground(modifier = Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        Surface(color = Color(0xFF1E1E1E), contentColor = Color.White, tonalElevation = 0.dp, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.ArrowBack, "Back", modifier = Modifier.size(24.dp)) }
                Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Default.Group, "Group", tint = MaterialTheme.colorScheme.primary) }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(groupTitle, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color.White, maxLines = 1)
                    Text("${selectedGroup?.members?.size ?: 0} members", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.62f), maxLines = 1)
                }
                IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" }, modifier = Modifier.size(40.dp)) { Icon(if (searchOpen) Icons.Default.Close else Icons.Default.Search, "Search", Modifier.size(24.dp)) }
                Box {
                    IconButton(onClick = { showMore = true }, enabled = selectedGroup != null, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, "More", Modifier.size(24.dp)) }
                    DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                        DropdownMenuItem(text = { Text("Members") }, onClick = { showMore = false; showMembers = true }, leadingIcon = { Icon(Icons.Default.Group, null) })
                        DropdownMenuItem(text = { Text("Group tools") }, onClick = { showMore = false; showTools = true }, leadingIcon = { Icon(Icons.Default.Build, null) })
                        DropdownMenuItem(text = { Text("Group settings") }, onClick = { showMore = false; showSettings = true }, leadingIcon = { Icon(Icons.Default.Settings, null) })
                    }
                }
            }
        }
        if (searchOpen) OutlinedTextField(searchQuery, { searchQuery = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), singleLine = true, placeholder = { Text("Search messages…") })
        syncMessage?.let { Text(it, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (!canSendMessages) Text("Only admins can send messages in this group.", Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val visibleMessages = if (searchQuery.isBlank()) messages else messages.filter { it.text.contains(searchQuery, true) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            if (visibleMessages.isEmpty() && searchQuery.isBlank()) {
                item(key = "fynx-empty-group") {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Surface(color = Color(0xFF242424).copy(alpha = 0.94f), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp)) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Default.Group, "Group", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) }
                                Spacer(Modifier.height(14.dp))
                                Text("No messages here yet…", style = MaterialTheme.typography.titleMedium, color = Color.White)
                                Spacer(Modifier.height(5.dp))
                                Text("Start the conversation in " + groupTitle + ".", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.68f))
                            }
                        }
                    }
                }
            } else {
                items(visibleMessages, key = { it.id }) { message ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
                        if (!message.fromMe) {
                            FynxAvatar(message.senderUsername ?: "", null, Modifier.size(32.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Column(horizontalAlignment = if (message.fromMe) Alignment.End else Alignment.Start) {
                            if (!message.fromMe && !message.senderUsername.isNullOrBlank()) {
                                Text(message.senderUsername!!, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.72f), modifier = Modifier.padding(bottom = 2.dp))
                            }
                            Surface(color = if (message.fromMe) MaterialTheme.colorScheme.primary else Color(0xFF303030), contentColor = Color.White, shape = RoundedCornerShape(18.dp), tonalElevation = 0.dp, modifier = Modifier.widthIn(max = 300.dp).combinedClickable(onClick = { replyToId = message.id }, onLongClick = { reactionMessageId = message.id })) {
                                Column(Modifier.padding(horizontal = 9.dp, vertical = 5.dp)) {
                                    if (message.replyToId != null) Text("Reply", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.82f), modifier = Modifier.padding(bottom = 5.dp))
                                if (message.attachmentUri != null) {
                                    if (message.attachmentType == "audio") FynxRemoteAudio(message.attachmentUri, Modifier.fillMaxWidth())
                                    else FynxRemoteMedia(message.attachmentUri, message.attachmentType ?: "image", Modifier.fillMaxWidth().heightIn(max = 220.dp).padding(bottom = if (message.text.isBlank()) 0.dp else 5.dp))
                                }
                                if (message.text.isNotBlank() && message.attachmentType != "audio") Text(message.text, color = Color.White)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                    Text(formatMessageClock(message.timestamp), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.62f))
                                    if (message.fromMe) { Spacer(Modifier.width(4.dp)); Text(if (message.read) "✓✓" else if (message.delivered) "✓✓" else "✓", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.68f)) }
                                }
                                }
                            }
                        }
                    }
                    if (reactionMessageId == message.id) {
                        Surface(color = Color(0xFF2A2A2A), contentColor = Color.White, shape = RoundedCornerShape(18.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Column(Modifier.padding(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    listOf("❤️","😂","👍","🙏","🔥","😮","😢","👏").forEach { emoji ->
                                        TextButton(onClick = {
                                            reactionMessageId = null
                                            scope.launch {
                                                FynxGroupRemoteClient.reactToMessage(context, groupId, message.id, if (message.reaction == emoji) null else emoji)
                                                    .onSuccess { remote -> val mapped = FynxGroupRemoteClient.toChatMessage(remote, currentUsername, FynxBackendClient.baseUrl(context)); messages = messages.map { if (it.id == mapped.id) mapped else it }; FynxChatStore.save(context, "group_$groupId", messages) }
                                                    .onFailure { syncMessage = it.message ?: "Reaction could not be saved" }
                                            }
                                        }, modifier = Modifier.size(38.dp), contentPadding = PaddingValues(0.dp)) { Text(emoji, style = MaterialTheme.typography.titleMedium) }
                                    }
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    TextButton(onClick = { replyToId = message.id; reactionMessageId = null }, modifier = Modifier.weight(1f)) { Text("Reply") }
                                    TextButton(onClick = {
                                        val clip = android.content.ClipData.newPlainText("FYNX message", message.text)
                                        (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                                        reactionMessageId = null
                                    }, modifier = Modifier.weight(1f), enabled = message.text.isNotBlank()) { Text("Copy") }
                                    TextButton(onClick = { reactionMessageId = null }, modifier = Modifier.weight(1f), enabled = false) { Text("Forward") }
                                    TextButton(onClick = {
                                        scope.launch {
                                            FynxGroupRemoteClient.setPinned(context, groupId, message.id, !message.pinned)
                                                .onSuccess { remote -> val mapped = FynxGroupRemoteClient.toChatMessage(remote, currentUsername, FynxBackendClient.baseUrl(context)); messages = messages.map { if (it.id == mapped.id) mapped else it }; FynxChatStore.save(context, "group_$groupId", messages); reactionMessageId = null }
                                                .onFailure { syncMessage = it.message ?: "Pin could not be saved" }
                                        }
                                    }, modifier = Modifier.weight(1f)) { Text(if (message.pinned) "Unpin" else "Pin") }
                                    TextButton(onClick = {
                                        scope.launch {
                                            FynxGroupRemoteClient.deleteMessage(context, groupId, message.id)
                                                .onSuccess { remote -> val mapped = FynxGroupRemoteClient.toChatMessage(remote, currentUsername, FynxBackendClient.baseUrl(context)); messages = messages.map { if (it.id == mapped.id) mapped else it }; FynxChatStore.save(context, "group_$groupId", messages); reactionMessageId = null }
                                                .onFailure { syncMessage = it.message ?: "Message could not be deleted" }
                                        }
                                    }, modifier = Modifier.weight(1f)) { Text("Delete") }
                                }
                            }
                        }
                    }                }
            }
        }
        if (showEmojiPanel && canSendMessages) { FynxChatEmojiPanel(onEmojiSelected = { emoji -> text += emoji; showEmojiPanel = false }) }
        Surface(color = Color(0xFF1E1E1E), contentColor = Color.White, tonalElevation = 0.dp, modifier = Modifier.fillMaxWidth().height(64.dp).navigationBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(enabled = canSendMessages, onClick = { showEmojiPanel = !showEmojiPanel }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.EmojiEmotions, "Emoji", Modifier.size(24.dp))
                }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    enabled = canSendMessages,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (canSendMessages) "Message..." else "Messaging is restricted") },
                    maxLines = 1,
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (text.isNotBlank() && !sending && selectedGroup != null) {
                            val optimistic = ChatMessage(text.trim(), true, UUID.randomUUID().toString(), delivered = true, read = true, replyToId = replyToId)
                            messages = messages + optimistic
                            FynxChatStore.save(context, "group_$groupId", messages)
                            text = ""; replyToId = null; sending = true
                            scope.launch {
                                FynxGroupRemoteClient.sendMessage(context, groupId, optimistic)
                                    .onSuccess { remote ->
                                        val serverMessage = FynxGroupRemoteClient.toChatMessage(remote, currentUsername, FynxBackendClient.baseUrl(context))
                                        messages = messages.map { if (it.id == optimistic.id) serverMessage else it }
                                        FynxChatStore.save(context, "group_$groupId", messages)
                                        syncMessage = null
                                    }
                                    .onFailure { error ->
                                        messages = messages.filterNot { it.id == optimistic.id }
                                        FynxChatStore.save(context, "group_$groupId", messages)
                                        syncMessage = error.message ?: "Message could not be sent."
                                    }
                                sending = false
                            }
                        }
                    })
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    enabled = canSendMessages,
                    onClick = {
                        if (text.isNotBlank() && !sending && selectedGroup != null) {
                            val optimistic = ChatMessage(text.trim(), true, UUID.randomUUID().toString(), delivered = true, read = true, replyToId = replyToId)
                            messages = messages + optimistic
                            FynxChatStore.save(context, "group_$groupId", messages)
                            text = ""; replyToId = null; sending = true
                            scope.launch {
                                FynxGroupRemoteClient.sendMessage(context, groupId, optimistic)
                                    .onSuccess { remote ->
                                        val serverMessage = FynxGroupRemoteClient.toChatMessage(remote, currentUsername, FynxBackendClient.baseUrl(context))
                                        messages = messages.map { if (it.id == optimistic.id) serverMessage else it }
                                        FynxChatStore.save(context, "group_$groupId", messages)
                                        syncMessage = null
                                    }
                                    .onFailure { error ->
                                        messages = messages.filterNot { it.id == optimistic.id }
                                        FynxChatStore.save(context, "group_$groupId", messages)
                                        syncMessage = error.message ?: "Message could not be sent."
                                    }
                                sending = false
                            }
                        }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Mic, "Microphone", Modifier.size(24.dp))
                }
            }
        }
    }

    }
    selectedGroup?.let { groupForDialogs ->
        if (showMembers) FynxGroupMembersDialog(groupForDialogs, currentUsername, { showMembers = false }) { updated -> if (FynxGroupsStore.updateGroup(context, updated)) { currentGroup = updated; scope.launch { FynxGroupRemoteClient.syncGroup(context, updated).onFailure { syncMessage = it.message } } } }
        if (showTools) FynxGroupSocialDialog(groupForDialogs, { showTools = false }, onInvite = { username ->
            if (!canAddMembers) syncMessage = "Adding members is disabled in Group Settings." else {
                val updated = if (groupForDialogs.members.any { it.username.equals(username, true) }) groupForDialogs else groupForDialogs.copy(members = groupForDialogs.members + FynxGroupMember(username))
                if (FynxGroupsBatch1.validate(updated).isEmpty()) { FynxGroupsStore.updateGroup(context, updated); currentGroup = updated; scope.launch { FynxGroupRemoteClient.syncGroup(context, updated).onFailure { syncMessage = it.message } } }
            }
        }, onMedia = { uri ->
            if (!canSendMedia) syncMessage = "Sending media and files is disabled in Group Settings." else { val next = messages + createGroupMediaMessage(uri); messages = next; FynxChatStore.save(context, "group_$groupId", next); scope.launch { FynxGroupRemoteClient.sendMessage(context, groupId, next.last()).onFailure { syncMessage = it.message } } }
        }, onStoryShare = {
            if (!canSendMedia) syncMessage = "Media sharing is disabled in Group Settings." else { val next = messages + ChatMessage("Story shared to ${groupForDialogs.name}", true, UUID.randomUUID().toString(), delivered = true, read = true); messages = next; FynxChatStore.save(context, "group_$groupId", next); scope.launch { FynxGroupRemoteClient.sendMessage(context, groupId, next.last()).onFailure { syncMessage = it.message } } }
        })
    }
}

private fun formatMessageClock(timestamp: Long): String {
    if (timestamp <= 0L) return "Now"
    return java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.getDefault()))
}

@Composable
private fun FynxGroupMembersDialog(group: FynxGroup, currentUsername: String, onDismiss: () -> Unit, onGroupChanged: (FynxGroup) -> Unit) {
    val context = LocalContext.current
    var current by remember(group.id) { mutableStateOf(group) }
    val management = remember(group.id) { mutableStateOf(FynxGroupManagementStore.load(context, group.id)) }
    var username by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val myRole = current.members.firstOrNull { it.username.equals(currentUsername, true) }?.role ?: FynxGroupRole.MEMBER
    val canManage = FynxGroupsBatch3.canManage(myRole)
    val isAdmin = myRole == FynxGroupRole.ADMIN
    val prefs = remember(current.id) { FynxConversationPreferences.group(context, current.id) }
    val canAddMembers = isAdmin || prefs.getBoolean("add_members", true)
    FynxPlainDialog(onDismissRequest = onDismiss, title = { Text("Members • ${current.members.size}", style = MaterialTheme.typography.headlineSmall) }, text = {
        Column {
            if (canManage && canAddMembers) Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = username, onValueChange = { username = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("Username") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done))
                IconButton(onClick = {
                    val clean = username.trim()
                    if (clean.isBlank() || current.members.any { it.username.equals(clean, true) }) message = "Enter a new username" else {
                        val updated = current.copy(members = current.members + FynxGroupMember(clean))
                        if (FynxGroupsBatch1.validate(updated).isEmpty()) { current = updated; onGroupChanged(updated); username = ""; message = "Member added" } else message = "Could not add member"
                    }
                }) { Icon(Icons.Default.Add, "Add member") }
            } else if (!canAddMembers) Text("Adding members is disabled in Group Settings.", style = MaterialTheme.typography.bodySmall)
            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(current.members, key = { it.username }) { member ->
                    val blocked = management.value.state.blockedUsernames.contains(member.username)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(member.username, style = MaterialTheme.typography.titleSmall); Text(if (blocked) "Blocked" else member.role.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (canManage && member.username != current.ownerUsername) {
                            TextButton(onClick = {
                                val action = if (member.role == FynxGroupRole.MODERATOR) FynxGroupMemberAction.DEMOTE_MODERATOR else FynxGroupMemberAction.PROMOTE_MODERATOR
                                val nextState = FynxGroupsBatch3.applyAction(management.value.state, member.username, action)
                                if (nextState != null) {
                                    management.value = management.value.copy(state = nextState); FynxGroupManagementStore.save(context, management.value)
                                    val role = if (member.role == FynxGroupRole.MODERATOR) FynxGroupRole.MEMBER else FynxGroupRole.MODERATOR
                                    val updated = current.copy(members = current.members.map { if (it.username == member.username) it.copy(role = role) else it }); current = updated; onGroupChanged(updated)
                                }
                            }) { Text(if (member.role == FynxGroupRole.MODERATOR) "Demote" else "Mod") }
                            TextButton(onClick = {
                                val action = if (blocked) FynxGroupMemberAction.UNBLOCK else FynxGroupMemberAction.BLOCK
                                val nextState = FynxGroupsBatch3.applyAction(management.value.state, member.username, action)
                                if (nextState != null) { management.value = management.value.copy(state = nextState); FynxGroupManagementStore.save(context, management.value) }
                            }) { Text(if (blocked) "Unblock" else "Block") }
                            TextButton(onClick = {
                                val updated = current.copy(members = current.members.filterNot { it.username == member.username })
                                if (FynxGroupsBatch1.validate(updated).isEmpty()) { current = updated; onGroupChanged(updated) } else message = "The group must keep one admin"
                            }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
private fun FynxGroupSettingsDialog(groupId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var data by remember(groupId) { mutableStateOf(FynxGroupManagementStore.load(context, groupId)) }
    fun save(next: FynxGroupManagementStore.GroupManagementData) { data = next; FynxGroupManagementStore.save(context, next) }
    FynxPlainDialog(onDismissRequest = onDismiss, title = { Text("Group settings", style = MaterialTheme.typography.headlineSmall) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Permissions", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Member posts", Modifier.weight(1f)); Switch(data.settings.allowMemberPosts, { save(data.copy(settings = data.settings.copy(allowMemberPosts = it))) }) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Member invites", Modifier.weight(1f)); Switch(data.settings.allowMemberInvites, { save(data.copy(settings = data.settings.copy(allowMemberInvites = it))) }) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Marketplace shares", Modifier.weight(1f)); Switch(data.settings.allowMarketplaceShares, { save(data.copy(settings = data.settings.copy(allowMarketplaceShares = it))) }) }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Group notifications", Modifier.weight(1f)); Switch(data.settings.notificationsEnabled, { save(data.copy(settings = data.settings.copy(notificationsEnabled = it))) }) }
            if (data.state.moderatorUsernames.isNotEmpty()) Text("Moderators: ${data.state.moderatorUsernames.joinToString()}", style = MaterialTheme.typography.bodySmall)
            if (data.state.blockedUsernames.isNotEmpty()) Text("Blocked: ${data.state.blockedUsernames.joinToString()}", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
