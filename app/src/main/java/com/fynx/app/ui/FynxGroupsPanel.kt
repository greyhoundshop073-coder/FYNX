package com.fynx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun FynxGroupsPanel(currentUsername: String = "@preview", onOpenGroup: (String) -> Unit = {}) {
    val context = LocalContext.current
    val glassThemeId = FynxGlassThemeId.entries.firstOrNull { it.label == FynxPreferencesStore.loadChatWallpaper(context) } ?: FynxGlassThemeId.PURE_BLACK
    val glassPalette = fynxGlassPalette(glassThemeId)
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
                            Box(Modifier.size(48.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                                if (group.groupPhotoMediaId.isNullOrBlank()) {
                                    Box(
                                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Group, "Group", tint = MaterialTheme.colorScheme.primary)
                                    }
                                } else {
                                    FynxRemoteProfileAvatar(
                                        mediaId = group.groupPhotoMediaId,
                                        contentDescription = group.name,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FynxGroupPulseSheet(
    group: FynxGroup,
    messages: List<ChatMessage>,
    onDismiss: () -> Unit,
    onOpenPinned: () -> Unit
) {
    val latest = messages.lastOrNull()
    val pinned = messages.lastOrNull { it.pinned }
    val activePeople = messages.mapNotNull { it.senderUsername?.trim()?.takeIf { name -> name.isNotBlank() } }.distinct().size
    val sharedMedia = messages.count { it.attachmentUri != null || it.attachmentType in setOf("image", "video", "video_note", "audio", "document") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Group, "Group Pulse", tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Group Pulse", style = MaterialTheme.typography.titleLarge)
                    Text("\${group.name} • live from this conversation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FynxPulseStat("Members", group.members.size.toString(), Modifier.weight(1f))
                FynxPulseStat("Active", activePeople.toString(), Modifier.weight(1f))
                FynxPulseStat("Media", sharedMedia.toString(), Modifier.weight(1f))
            }
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Latest activity", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        latest?.text?.takeIf { it.isNotBlank() } ?: when (latest?.attachmentType) {
                            "video_note" -> "Video note"
                            "video" -> "Video"
                            "image" -> "Photo"
                            "audio" -> "Voice message"
                            "document" -> "Document"
                            else -> "No messages yet"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 3
                    )
                    latest?.senderUsername?.let {
                        Text("@${it.removePrefix("@")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (pinned != null) {
                Surface(onClick = onOpenPinned, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PushPin, "Pinned", tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Pinned message", style = MaterialTheme.typography.labelLarge)
                            Text(pinned.text.ifBlank { "Media message" }, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                        }
                        Icon(Icons.Default.ChevronRight, "Open pinned message", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Text("Nothing is pinned yet. Pin an important message so the group can find it quickly.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Pulse uses the group's real members and conversation activity; it does not create sample content.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FynxPulseStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FynxGroupConversationPanel(groupId: String, currentUsername: String = "@preview", onBack: () -> Unit) {
    val context = LocalContext.current
    val glassThemeId = FynxGlassThemeId.entries.firstOrNull { it.label == FynxPreferencesStore.loadChatWallpaper(context) } ?: FynxGlassThemeId.PURE_BLACK
    val glassPalette = fynxGlassPalette(glassThemeId)
    val messageTextSizeSp = FynxConversationPreferences.chatTextSizeSp(context, "group_$groupId")
    val bubbleTransparency = FynxConversationPreferences.chatBubbleTransparency(context, "group_$groupId")
    val bubbleLighting = FynxConversationPreferences.chatBubbleLighting(context, "group_$groupId")
    val bubbleGradient = FynxConversationPreferences.chatBubbleGradient(context, "group_$groupId")
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var mentionQuery by remember { mutableStateOf<String?>(null) }
    val group = remember(groupId) { FynxGroupsStore.load(context).firstOrNull { it.id == groupId } }
    var currentGroup by remember(groupId) { mutableStateOf(group) }
    val groupTitle = currentGroup?.name ?: "Group"
    var messages by remember(groupId) { mutableStateOf(FynxChatStore.load(context, "group_$groupId", null)) }
    var showMembers by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showPulse by remember { mutableStateOf(false) }
    var showCatchMeUp by remember { mutableStateOf(false) }
    var groupNotificationsEnabled by remember(groupId) { mutableStateOf(FynxConversationPreferences.groupNotifications(context, groupId)) }
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
    val senderAvatarUris = remember(groupId) { mutableStateMapOf<String, String?>() }
    val messageListState = rememberLazyListState()
    val senderUsernames = remember(messages) { messages.mapNotNull { it.senderUsername?.trim()?.takeIf { name -> name.isNotBlank() } }.distinct() }
    LaunchedEffect(groupId, senderUsernames) {
        senderUsernames.forEach { username ->
            if (!senderAvatarUris.containsKey(username)) {
                FynxProfileRemoteClient.get(context, username).onSuccess { profile ->
                    senderAvatarUris[username] = profile.profilePhotoMediaId?.takeIf { it.isNotBlank() }?.let { "/api/media/$it" }
                }.onFailure {
                    senderAvatarUris[username] = null
                }
            }
        }
    }
    LaunchedEffect(groupId, currentGroup?.id) {
        val selected = currentGroup ?: return@LaunchedEffect
        FynxGroupRemoteClient.syncGroup(context, selected).onFailure { if (FynxBackendClient.hasAccessToken(context)) syncMessage = it.message }
        FynxGroupRemoteClient.loadMessages(context, groupId).onSuccess { remote ->
            messages = remote.map { FynxGroupRemoteClient.toChatMessage(it, currentUsername, FynxBackendClient.baseUrl(context)) }
            FynxChatStore.save(context, "group_$groupId", messages)
            syncMessage = null
        }.onFailure { if (FynxBackendClient.hasAccessToken(context)) syncMessage = it.message ?: "Unable to sync group messages." }
    }
    LaunchedEffect(messages.size, searchQuery) {
        if (messages.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(60L)
        val lastIndex = messageListState.layoutInfo.totalItemsCount - 1
        val lastVisible = messageListState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
        if (searchQuery.isNotBlank()) {
            messageListState.scrollToItem(0)
        } else if (lastIndex >= 0 && (lastVisible < 0 || lastVisible >= lastIndex - 2)) {
            messageListState.animateScrollToItem(lastIndex)
        }
    }

    if (showPulse && selectedGroup != null) {
        FynxGroupPulseSheet(group = selectedGroup, messages = messages, onDismiss = { showPulse = false }) {
            showPulse = false
            val pinned = messages.lastOrNull { it.pinned }
            val index = pinned?.let { p -> messages.indexOfFirst { it.id == p.id } } ?: -1
            if (index >= 0) scope.launch { messageListState.animateScrollToItem(index) }
        }
    }

    if (showSettings && selectedGroup != null) { FynxGroupSettingsPanel(groupId = selectedGroup.id, groupName = selectedGroup.name, isAdmin = isAdmin, onBack = { currentGroup = FynxGroupsStore.load(context).firstOrNull { it.id == groupId }; showSettings = false }); return }
    FynxChatWallpaperBackground(modifier = Modifier.fillMaxSize(), settingsKey = "group_$groupId") {
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) { Icon(Icons.Default.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp)) }
                Box(Modifier.size(40.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    if (selectedGroup?.groupPhotoMediaId.isNullOrBlank()) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Default.Group, "Group", tint = MaterialTheme.colorScheme.primary) }
                    } else {
                        FynxRemoteProfileAvatar(selectedGroup?.groupPhotoMediaId, groupTitle, Modifier.fillMaxSize())
                    }
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(groupTitle, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    Text("${selectedGroup?.members?.size ?: 0} members", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" }, modifier = Modifier.size(40.dp)) { Icon(if (searchOpen) Icons.Default.Close else Icons.Default.Search, "Search", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp)) }
                Box {
                    IconButton(onClick = { showMore = true }, enabled = selectedGroup != null, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.MoreVert, "More", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp)) }
                    DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                        DropdownMenuItem(text = { Text("Catch Me Up") }, onClick = { showMore = false; showCatchMeUp = true }, leadingIcon = { Icon(Icons.Default.AutoAwesome, null) })
                        DropdownMenuItem(text = { Text("Group Pulse") }, onClick = { showMore = false; showPulse = true }, leadingIcon = { Icon(Icons.Default.Group, null) })
                        DropdownMenuItem(text = { Text("Members") }, onClick = { showMore = false; showMembers = true }, leadingIcon = { Icon(Icons.Default.Group, null) })
                        DropdownMenuItem(text = { Text("Group tools") }, onClick = { showMore = false; showTools = true }, leadingIcon = { Icon(Icons.Default.Build, null) })
                        DropdownMenuItem(text = { Text("Group settings") }, onClick = { showMore = false; showSettings = true }, leadingIcon = { Icon(Icons.Default.Settings, null) })
                        DropdownMenuItem(text = { Text(if (groupNotificationsEnabled) "Mute notifications" else "Turn on notifications") }, onClick = { groupNotificationsEnabled = !groupNotificationsEnabled; FynxConversationPreferences.setGroupNotifications(context, groupId, groupNotificationsEnabled); showMore = false }, leadingIcon = { Icon(Icons.Default.Notifications, null) })
                    }
                }
            }
        }
        val visibleMessages = if (searchQuery.isBlank()) messages else messages.filter { it.text.contains(searchQuery, true) }
        val pinnedMessage = messages.lastOrNull { it.pinned }
        if (searchOpen) OutlinedTextField(searchQuery, { searchQuery = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), singleLine = true, placeholder = { Text("Search messages…") })
        pinnedMessage?.let { pinned ->
            Surface(onClick = { searchQuery = ""; val index = messages.indexOfFirst { it.id == pinned.id }; if (index >= 0) scope.launch { messageListState.animateScrollToItem(index) } }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PushPin, "Pinned message", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) { Text("Pinned message", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary); Text(pinned.text.ifBlank { "Media message" }, maxLines = 1, style = MaterialTheme.typography.bodySmall) }
                    Icon(Icons.Default.ChevronRight, "Open pinned message", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        syncMessage?.let { Text(it, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        if (!canSendMessages) Text("Only admins can send messages in this group.", Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(state = messageListState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
            if (visibleMessages.isEmpty() && searchQuery.isBlank()) {
                item(key = "fynx-empty-group") {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f), contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().widthIn(max = 340.dp)) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Default.Group, "Group", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) }
                                Spacer(Modifier.height(14.dp))
                                Text("No messages here yet…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(5.dp))
                                Text("Start the conversation in " + groupTitle + ".", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                items(visibleMessages, key = { it.id }) { message ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
                        Column(horizontalAlignment = if (message.fromMe) Alignment.End else Alignment.Start) {
                            if (!message.fromMe && !message.senderUsername.isNullOrBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 3.dp)) {
                                    FynxRemoteProfileAvatar(
                                        mediaId = senderAvatarUris[message.senderUsername],
                                        contentDescription = message.senderUsername,
                                        modifier = Modifier.size(28.dp),
                                        ownerUsername = message.senderUsername
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(message.senderUsername!!, style = MaterialTheme.typography.labelMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = glassPalette.messageMuted)
                                }
                            }
                            Box {
                            val mediaOnly = message.attachmentUri != null && message.text.isBlank() && message.attachmentType in setOf("image", "video", "video_note")
                            val bubbleShape = RoundedCornerShape(15.dp)
                            val bubbleBrush = if (message.fromMe) {
                                Brush.horizontalGradient(listOf(glassPalette.outgoingStart.copy(alpha = bubbleTransparency), glassPalette.outgoingEnd.copy(alpha = bubbleGradient)))
                            } else {
                                Brush.linearGradient(listOf(glassPalette.incomingGlass.copy(alpha = bubbleTransparency), glassPalette.backgroundMid.copy(alpha = (0.55f + bubbleGradient * 0.4f).coerceIn(0.55f, 0.95f))))
                            }
                            Surface(
                                color = Color.Transparent,
                                contentColor = glassPalette.messageText,
                                shape = if (mediaOnly) RoundedCornerShape(0.dp) else bubbleShape,
                                border = if (mediaOnly) null else BorderStroke(0.7.dp, glassPalette.bubbleRim.copy(alpha = (bubbleLighting * bubbleTransparency).coerceIn(0f, 1f))),
                                tonalElevation = 0.dp,
                                modifier = Modifier.widthIn(max = 300.dp)
                                    .then(if (mediaOnly) Modifier else Modifier.background(bubbleBrush, bubbleShape))
                                    .combinedClickable(onClick = { reactionMessageId = message.id }, onLongClick = { reactionMessageId = message.id })
                            ) {
                                Column(Modifier.padding(horizontal = if (mediaOnly) 0.dp else 9.dp, vertical = if (mediaOnly) 0.dp else 5.dp)) {
                                    if (message.replyToId != null) Text("Reply", style = MaterialTheme.typography.labelSmall, color = glassPalette.messageMuted, modifier = Modifier.padding(bottom = 5.dp))
                                if (message.attachmentUri != null) {
                                    if (message.attachmentType == "audio") FynxRemoteAudio(message.attachmentUri, Modifier.fillMaxWidth())
                                    else if (message.attachmentType == "video_note") {
                                        Box(Modifier.size(170.dp).clip(CircleShape)) {
                                            FynxRemoteMedia(message.attachmentUri, "video", Modifier.fillMaxSize(), rounded = false, loopVideo = true)
                                            Surface(color = Color.Black.copy(alpha = 0.46f), shape = CircleShape, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)) { Text("Video note", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
                                        }
                                    } else FynxRemoteMedia(message.attachmentUri, message.attachmentType ?: "image", Modifier.fillMaxWidth().heightIn(max = 220.dp).padding(bottom = if (message.text.isBlank()) 0.dp else 5.dp))
                                }
                                if (message.text.isNotBlank() && message.attachmentType != "audio") Text(message.text, color = glassPalette.messageText, fontSize = messageTextSizeSp.sp)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                    Text(formatMessageClock(message.timestamp), style = MaterialTheme.typography.labelSmall, color = glassPalette.messageMuted)
                                    if (message.fromMe) { Spacer(Modifier.width(4.dp)); Text(if (message.read) "✓✓" else if (message.delivered) "✓✓" else "✓", style = MaterialTheme.typography.labelSmall, color = glassPalette.messageMuted) }
                                }
                                }
                            }
                            }
                            DropdownMenu(expanded = reactionMessageId == message.id, onDismissRequest = { reactionMessageId = null }) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    listOf("❤️","😂","👍","🙏","🔥","😮","😢","👏").forEach { emoji ->
                                        TextButton(onClick = {
                                            reactionMessageId = null
                                            scope.launch {
                                                FynxGroupRemoteClient.reactToMessage(context, groupId, message.id, if (message.reaction == emoji) null else emoji)
                                                    .onSuccess { remote -> val mapped = FynxGroupRemoteClient.toChatMessage(remote, currentUsername, FynxBackendClient.baseUrl(context)); messages = messages.map { if (it.id == mapped.id) mapped else it }; FynxChatStore.save(context, "group_$groupId", messages) }
                                                    .onFailure { syncMessage = it.message ?: "Reaction could not be saved" }
                                            }
                                        }, modifier = Modifier.size(34.dp), contentPadding = PaddingValues(0.dp)) { Text(emoji, style = MaterialTheme.typography.titleMedium) }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                DropdownMenuItem(text = { Text("Reply") }, onClick = { replyToId = message.id; reactionMessageId = null }, leadingIcon = { Icon(Icons.Default.Reply, null) })
                                DropdownMenuItem(text = { Text("Copy") }, enabled = message.text.isNotBlank(), onClick = {
                                    val clip = android.content.ClipData.newPlainText("FYNX message", message.text)
                                    (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                                    reactionMessageId = null
                                }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) })
                                
                                DropdownMenuItem(text = { Text(if (message.pinned) "Unpin" else "Pin") }, onClick = {
                                    scope.launch {
                                        FynxGroupRemoteClient.setPinned(context, groupId, message.id, !message.pinned)
                                            .onSuccess { remote -> val mapped = FynxGroupRemoteClient.toChatMessage(remote, currentUsername, FynxBackendClient.baseUrl(context)); messages = messages.map { if (it.id == mapped.id) mapped else it }; FynxChatStore.save(context, "group_$groupId", messages); reactionMessageId = null }
                                            .onFailure { syncMessage = it.message ?: "Pin could not be saved" }
                                    }
                                }, leadingIcon = { Icon(Icons.Default.PushPin, null) })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = {
                                    scope.launch {
                                        FynxGroupRemoteClient.deleteMessage(context, groupId, message.id)
                                            .onSuccess { remote -> val mapped = FynxGroupRemoteClient.toChatMessage(remote, currentUsername, FynxBackendClient.baseUrl(context)); messages = messages.map { if (it.id == mapped.id) mapped else it }; FynxChatStore.save(context, "group_$groupId", messages); reactionMessageId = null }
                                            .onFailure { syncMessage = it.message ?: "Message could not be deleted" }
                                    }
                                }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                            }
                        }
                    }                }
            }
        }
        if (showEmojiPanel && canSendMessages) { FynxChatEmojiPanel(onEmojiSelected = { emoji -> text += emoji; showEmojiPanel = false }) }
        val mentionSuggestions = remember(text, selectedGroup?.members) {
            val match = Regex("""(?:^|\s)@([A-Za-z0-9_.-]*)$""").find(text)
            val query = match?.groupValues?.getOrNull(1)?.lowercase()
            mentionQuery = query
            if (query == null || selectedGroup == null) emptyList()
            else selectedGroup.members
                .map { it.username.removePrefix("@").trim() }
                .filter { it.isNotBlank() && it.lowercase().contains(query) }
                .distinct()
                .take(6)
        }
        if (canSendMessages && mentionSuggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                color = Color(0xFF08090D),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("Mention a member", modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp), color = Color(0xFF9C90F0), style = MaterialTheme.typography.labelMedium)
                    mentionSuggestions.forEach { username ->
                        TextButton(
                            onClick = {
                                val current = text
                                val match = Regex("""(?:^|\s)@[A-Za-z0-9_.-]*$""").find(current)
                                text = if (match != null) current.removeRange(match.range).trimEnd() + " @$username " else current + " @$username "
                                mentionQuery = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                FynxAvatar(username, senderAvatarUris[username], Modifier.size(30.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("@$username", color = Color.White, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        Surface(color = Color(0xFF08090D).copy(alpha = 0.98f), contentColor = Color.White, tonalElevation = 0.dp, modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
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
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF101217),
                        unfocusedContainerColor = Color(0xFF101217),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedPlaceholderColor = Color(0xFF9A9DA8),
                        unfocusedPlaceholderColor = Color(0xFF9A9DA8)
                    ),
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
        if (showCatchMeUp) FynxCatchMeUpSheet(messages = messages, title = groupForDialogs.name, onDismiss = { showCatchMeUp = false })
        if (showTools) FynxGroupSocialDialog(groupForDialogs, { showTools = false }, onInvite = { username ->
            if (!canAddMembers) syncMessage = "Adding members is disabled in Group Settings." else {
                val updated = if (groupForDialogs.members.any { it.username.equals(username, true) }) groupForDialogs else groupForDialogs.copy(members = groupForDialogs.members + FynxGroupMember(username))
                if (FynxGroupsBatch1.validate(updated).isEmpty()) { FynxGroupsStore.updateGroup(context, updated); currentGroup = updated; scope.launch { FynxGroupRemoteClient.syncGroup(context, updated).onFailure { syncMessage = it.message } } }
            }
        }, onMedia = { uri, type ->
            if (!canSendMedia) syncMessage = "Sending media and files is disabled in Group Settings." else { val next = messages + createGroupMediaMessage(uri, type); messages = next; FynxChatStore.save(context, "group_$groupId", next); scope.launch { FynxGroupRemoteClient.sendMessage(context, groupId, next.last()).onFailure { syncMessage = it.message } } }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FynxCatchMeUpSheet(
    messages: List<ChatMessage>,
    title: String,
    onDismiss: () -> Unit
) {
    val recent = messages.sortedByDescending { it.timestamp }.take(4)
    val mediaCount = messages.count { it.attachmentUri != null || it.attachmentType in setOf("image", "video", "video_note", "audio", "document") }
    val questionCount = messages.count { it.text.trim().endsWith("?") }
    val latestIncoming = messages.asReversed().firstOrNull { !it.fromMe && it.text.isNotBlank() }
    var waitingForReply = false
    messages.sortedBy { it.timestamp }.forEach { if (it.fromMe) waitingForReply = false else if (it.text.isNotBlank()) waitingForReply = true }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Catch Me Up", style = MaterialTheme.typography.titleLarge)
            Text("A quick view of the real conversation with $title", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FynxPulseStat("Messages", messages.size.toString(), Modifier.weight(1f))
                FynxPulseStat("Media", mediaCount.toString(), Modifier.weight(1f))
                FynxPulseStat("Questions", questionCount.toString(), Modifier.weight(1f))
            }
            if (waitingForReply && latestIncoming != null) {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("May need your reply", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(latestIncoming.text, style = MaterialTheme.typography.bodyLarge, maxLines = 4)
                    }
                }
            }
            Text("Recent activity", style = MaterialTheme.typography.titleSmall)
            if (recent.isEmpty()) Text("No messages yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else recent.forEach { message ->
                Text(
                    (if (message.fromMe) "You: " else "${message.senderName ?: message.senderUsername ?: "Member"}: ") +
                        (message.text.takeIf { it.isNotBlank() } ?: when (message.attachmentType) {
                            "video_note" -> "Video note"
                            "video" -> "Video"
                            "image" -> "Photo"
                            "audio" -> "Voice message"
                            "document" -> "Document"
                            else -> "Message"
                        }),
                    maxLines = 2,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text("This summary uses only messages already in this conversation; it does not create sample content.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
        }
    }
}
