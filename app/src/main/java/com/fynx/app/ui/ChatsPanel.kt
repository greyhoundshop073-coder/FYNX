package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun ChatsPanel(
    onOpenChat: (ChatPreview) -> Unit,
    onOpenGroup: (String) -> Unit = {},
    onCreateGroup: () -> Unit = {},
    onOpenContacts: () -> Unit = {}
) {
    var section by remember { mutableStateOf("Chats") }
    val context = LocalContext.current
    var chats by remember { mutableStateOf(FynxChatStore.loadPreviews(context)) }
    var groups by remember { mutableStateOf(FynxGroupsStore.load(context)) }
    var selfUsername by remember { mutableStateOf("") }
    var listView by remember { mutableStateOf(FynxPreferencesStore.loadChatListView(context)) }
    var listFilter by remember { mutableStateOf("All") }
    var openMenuFor by remember { mutableStateOf<String?>(null) }
    var chatSearch by remember { mutableStateOf("") }

    fun refreshChats() {
        chats = FynxChatStore.loadPreviews(context).map { preview ->
            val unread = FynxChatStore.load(context, preview.username).count { !it.fromMe && !it.read }
            preview.copy(unreadCount = unread)
        }
        chats.forEach { FynxChatStore.savePreview(context, it) }
    }

    LaunchedEffect(Unit) {
        listView = FynxPreferencesStore.loadChatListView(context)
        selfUsername = (FynxAuthStore.load(context).username ?: "").removePrefix("@").trim().lowercase()
        refreshChats()
        val stored = FynxChatStore.loadPreviews(context)
        val refreshed = stored.map { chat ->
            val normalized = chat.username.removePrefix("@").trim()
            if (normalized.isBlank()) chat else FynxProfileRemoteClient.get(context, normalized).getOrNull()?.let { profile ->
                val mediaId = profile.profilePhotoMediaId
                if (!mediaId.isNullOrBlank()) chat.copy(avatarUri = "/api/media/${mediaId.trim()}") else chat.copy(avatarUri = null)
            } ?: chat
        }
        if (refreshed != stored) {
            refreshed.forEach { FynxChatStore.savePreview(context, it) }
            chats = refreshed
        }
    }

    LaunchedEffect(Unit) {
        FynxChatStore.previewUpdates.collect { refreshChats() }
    }

    val rowSpacing = when (listView) { "Compact" -> 2.dp; "Large" -> 10.dp; else -> 6.dp }
    val avatarSize = when (listView) { "Compact" -> 38.dp; "Large" -> 54.dp; else -> 46.dp }
    val normalizedChatSearch = chatSearch.trim()

    val baseChats = chats.filterNot { chat ->
        val candidate = chat.username.removePrefix("@").trim().lowercase()
        selfUsername.isNotBlank() && candidate == selfUsername
    }.filter { chat ->
        normalizedChatSearch.isBlank() ||
            chat.name.contains(normalizedChatSearch, ignoreCase = true) ||
            chat.username.contains(normalizedChatSearch, ignoreCase = true) ||
            chat.lastMessage.contains(normalizedChatSearch, ignoreCase = true)
    }

    val visibleChats = baseChats.filter { chat ->
        val archived = FynxPreferencesStore.isChatArchived(context, chat.username)
        val pinned = FynxPreferencesStore.isChatPinned(context, chat.username)
        val unread = FynxChatStore.load(context, chat.username).any { !it.fromMe && !it.read }
        when (listFilter) {
            "Unread" -> !archived && unread
            "Pinned" -> !archived && pinned
            "Archived" -> archived
            else -> !archived
        }
    }.sortedWith(compareByDescending<ChatPreview> { FynxPreferencesStore.isChatPinned(context, it.username) })

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            // The app-level Chats title remains untouched. This panel now starts directly with search.
            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = chatSearch,
                    onValueChange = { chatSearch = it },
                    modifier = Modifier.weight(1f).height(50.dp),
                    singleLine = true,
                    shape = FynxDesign.ControlShape,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search chats") },
                    placeholder = { Text("Search chats") }
                )
                TextButton(
                    onClick = { section = if (section == "Chats") "Groups" else "Chats" },
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text(if (section == "Chats") "Groups" else "Chats")
                }
            }

            if (section == "Chats") {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Unread", "Pinned", "Archived").forEach { filter ->
                        val selected = listFilter == filter
                        Surface(
                            onClick = { listFilter = filter },
                            shape = FynxDesign.ControlShape,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                filter,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (visibleChats.isEmpty() && groups.isEmpty()) {
                    Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape) {
                        Column(
                            Modifier.fillMaxWidth().padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                when {
                                    listFilter == "Archived" -> "No archived chats"
                                    normalizedChatSearch.isNotBlank() -> "No matching chats"
                                    else -> "Messages"
                                },
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                when {
                                    listFilter == "Archived" -> "Chats you archive will stay here until you restore them."
                                    normalizedChatSearch.isNotBlank() -> "Try another name, username or message."
                                    else -> "Your private conversations will appear here. Start one with a real FYNX user."
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(rowSpacing),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        if (visibleChats.isNotEmpty()) {
                            item {
                                Text(
                                    if (listFilter == "Pinned") "Pinned" else "Messages",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                                )
                            }
                        }

                        items(visibleChats, key = { it.username }) { chat ->
                            val pinned = FynxPreferencesStore.isChatPinned(context, chat.username)
                            val muted = FynxPreferencesStore.isChatMuted(context, chat.username)
                            val unread = FynxChatStore.load(context, chat.username).count { !it.fromMe && !it.read }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = FynxDesign.CardShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenChat(chat) }
                                        .semantics { contentDescription = "Open chat with ${chat.name}" }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FynxRemoteProfileAvatar(
                                        mediaId = chat.avatarUri?.substringAfterLast("/api/media/")?.takeIf { it != chat.avatarUri },
                                        contentDescription = chat.name,
                                        modifier = Modifier.size(avatarSize),
                                        ownerUsername = chat.username
                                    )

                                    Spacer(Modifier.width(12.dp))

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                chat.name,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Text(
                                                chat.time,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                buildString {
                                                    if (pinned) append("Pinned • ")
                                                    if (muted) append("Muted • ")
                                                    append(chat.lastMessage.ifBlank { "No messages yet" })
                                                },
                                                modifier = Modifier.weight(1f),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            if (unread > 0) {
                                                Spacer(Modifier.width(8.dp))
                                                Surface(
                                                    modifier = Modifier.size(22.dp),
                                                    shape = CircleShape,
                                                    color = Color(0xFF25D366),
                                                    contentColor = Color.White
                                                ) {
                                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                        Text(if (unread > 99) "99+" else unread.toString(), style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Box {
                                        IconButton(
                                            onClick = { openMenuFor = chat.username },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "Chat options")
                                        }
                                        DropdownMenu(
                                            expanded = openMenuFor == chat.username,
                                            onDismissRequest = { openMenuFor = null }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(if (pinned) "Unpin" else "Pin") },
                                                onClick = {
                                                    FynxPreferencesStore.setChatPinned(context, chat.username, !pinned)
                                                    openMenuFor = null
                                                    refreshChats()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(if (muted) "Unmute" else "Mute") },
                                                onClick = {
                                                    FynxPreferencesStore.setChatMuted(context, chat.username, !muted)
                                                    openMenuFor = null
                                                    refreshChats()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(if (listFilter == "Archived") "Unarchive" else "Archive") },
                                                onClick = {
                                                    FynxPreferencesStore.setChatArchived(context, chat.username, listFilter == "Archived")
                                                    openMenuFor = null
                                                    refreshChats()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (listFilter == "All" && normalizedChatSearch.isBlank() && groups.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Groups",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                                )
                            }
                            items(groups, key = { "group:" + it.id }) { group ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenGroup(group.id) },
                                    shape = FynxDesign.CardShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (group.groupPhotoMediaId.isNullOrBlank()) {
                                            FynxAvatar(group.name, modifier = Modifier.size(avatarSize))
                                        } else {
                                            FynxRemoteProfileAvatar(group.groupPhotoMediaId, group.name, modifier = Modifier.size(avatarSize))
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "${group.members.size} members" + (group.description.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        Icon(Icons.Default.MoreVert, contentDescription = "Group options")
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onCreateGroup,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Create group" }
                ) { Text("＋ Create group") }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Groups", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onCreateGroup) { Text("＋ New group") }
                }
                Spacer(Modifier.height(6.dp))
                if (groups.isEmpty()) {
                    Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape) {
                        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("No groups yet", style = MaterialTheme.typography.titleMedium)
                            Text("Create a group to start a shared conversation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(rowSpacing),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(groups, key = { it.id }) { group ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().clickable { onOpenGroup(group.id) },
                                shape = FynxDesign.CardShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                            ) {
                                ListItem(
                                    headlineContent = { Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingContent = {
                                        if (group.groupPhotoMediaId.isNullOrBlank()) FynxAvatar(group.name, modifier = Modifier.size(avatarSize))
                                        else FynxRemoteProfileAvatar(group.groupPhotoMediaId, group.name, modifier = Modifier.size(avatarSize))
                                    },
                                    supportingContent = {
                                        Text(
                                            "${group.members.size} members" + (group.description.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Existing bottom navigation is intentionally untouched; this FAB remains the contacts action.
        FloatingActionButton(
            onClick = onOpenContacts,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 22.dp),
            containerColor = Color(0xFF25D366),
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = "Phone contacts")
        }
    }
}
