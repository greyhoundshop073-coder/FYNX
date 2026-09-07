package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatsPanel(onOpenChat: (ChatPreview) -> Unit, onOpenGroup: (String) -> Unit = {}, onCreateGroup: () -> Unit = {}) {
    var section by remember { mutableStateOf("Chats") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var chats by remember { mutableStateOf(FynxChatStore.loadPreviews(context)) }
    var groups by remember { mutableStateOf(FynxGroupsStore.load(context)) }
    var showNewChat by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(emptyList<FynxSocialClient.User>()) }
    var selectedUser by remember { mutableStateOf<FynxSocialClient.User?>(null) }
    var searchBusy by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var selfUsername by remember { mutableStateOf("") }
    var listView by remember { mutableStateOf(FynxPreferencesStore.loadChatListView(context)) }

    LaunchedEffect(Unit) {
        listView = FynxPreferencesStore.loadChatListView(context)
        selfUsername = (FynxAuthStore.load(context).username ?: "").removePrefix("@").trim().lowercase()
        val stored = FynxChatStore.loadPreviews(context)
        val refreshed = stored.map { chat ->
            val normalized = chat.username.removePrefix("@").trim()
            if (normalized.isBlank()) chat else FynxProfileRemoteClient.get(context, normalized).getOrNull()?.let { profile ->
                val mediaId = profile.profilePhotoMediaId
                if (!mediaId.isNullOrBlank()) chat.copy(avatarUri = "/api/media/${mediaId.trim()}") else chat
            } ?: chat
        }
        if (refreshed != stored) {
            refreshed.forEach { FynxChatStore.savePreview(context, it) }
            chats = refreshed
        }
    }

    LaunchedEffect(showNewChat, username, selfUsername) {
        if (!showNewChat || username.trim().length < 2) {
            searchResults = emptyList()
            searchBusy = false
            return@LaunchedEffect
        }
        delay(250L)
        searchBusy = true
        searchError = null
        FynxSocialClient.searchUsers(context, username.trim())
            .onSuccess {
                searchResults = it.filterNot { person ->
                    val candidate = (person.username ?: "").removePrefix("@").trim().lowercase()
                    selfUsername.isNotBlank() && candidate == selfUsername
                }
            }
            .onFailure { searchResults = emptyList(); searchError = it.message ?: "Could not search FYNX accounts." }
        searchBusy = false
    }

    val rowSpacing = when (listView) { "Compact" -> 2.dp; "Large" -> 14.dp; else -> 8.dp }
    val avatarSize = when (listView) { "Compact" -> 38.dp; "Large" -> 54.dp; else -> 42.dp }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Chats", style = MaterialTheme.typography.headlineSmall)
                Text("Messages and groups in one place", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { section = if (section == "Chats") "Groups" else "Chats" }) {
                Text(if (section == "Chats") "Groups" else "Chats")
            }
        }
        Spacer(Modifier.height(12.dp))
        if (section == "Chats") {
            OutlinedButton(onClick = { username = ""; selectedUser = null; searchResults = emptyList(); searchError = null; showNewChat = true }, shape = FynxDesign.ControlShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) { Text("＋ New chat") }
            Spacer(Modifier.height(14.dp))
            if (chats.isEmpty()) {
                Card(Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Messages", style = MaterialTheme.typography.titleLarge)
                        Text("Your private conversations will appear here. Start one with a real FYNX user.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { showNewChat = true; username = ""; selectedUser = null }) { Text("Start a conversation") }
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(rowSpacing), contentPadding = PaddingValues(bottom = 12.dp)) {
                    items(chats.filterNot { chat ->
                        val candidate = chat.username.removePrefix("@").trim().lowercase()
                        selfUsername.isNotBlank() && candidate == selfUsername
                    }, key = { it.username }) { chat ->
                        Card(onClick = { onOpenChat(chat) }, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                            ListItem(
                                headlineContent = { Text(chat.name) },
                                leadingContent = {
                                    if (chat.avatarUri.isNullOrBlank()) FynxAvatar(chat.name, null, Modifier.size(avatarSize))
                                    else FynxRemoteProfileAvatar(
                                        mediaId = chat.avatarUri?.substringAfterLast("/api/media/")?.takeIf { it != chat.avatarUri },
                                        contentDescription = chat.name,
                                        modifier = Modifier.size(avatarSize)
                                    )
                                },
                                supportingContent = { Text(chat.lastMessage.ifBlank { "No messages yet" }, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                trailingContent = { Text(chat.time, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onCreateGroup, modifier = Modifier.fillMaxWidth()) { Text("＋ Create group") }
        } else {
            Button(onClick = onCreateGroup) { Text("＋ New group") }
            Spacer(Modifier.height(10.dp))
            if (groups.isEmpty()) {
                Text("No groups yet", style = MaterialTheme.typography.titleMedium)
                Text("Create a group to start a shared conversation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(rowSpacing), contentPadding = PaddingValues(bottom = 12.dp)) {
                    items(groups, key = { it.id }) { group ->
                        Card(onClick = { onOpenGroup(group.id) }, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                            ListItem(
                                headlineContent = { Text(group.name) },
                                leadingContent = { FynxAvatar(group.name, modifier = Modifier.size(avatarSize)) },
                                supportingContent = { Text("${group.members.size} members${group.description.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""}", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                            )
                        }
                    }
                }
            }
        }
    }
    if (showNewChat) {
        FynxPlainDialog(
            onDismissRequest = { showNewChat = false },
            title = { Text("New chat", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Search for a real FYNX username to start a private conversation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = username, onValueChange = { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true, placeholder = { Text("@username") })
                    if (searchBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    searchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    searchResults.forEach { person ->
                        val personUsername = person.username ?: ""
                        ListItem(
                            headlineContent = { Text(person.displayName.ifBlank { personUsername }) },
                            supportingContent = { Text("@$personUsername") },
                            modifier = Modifier.fillMaxWidth().clickable { selectedUser = person },
                            leadingContent = { FynxRemoteProfileAvatar(person.profilePhotoMediaId, person.displayName.ifBlank { personUsername }, Modifier.size(42.dp)) },
                            trailingContent = { if (selectedUser?.username == person.username) Text("✓", color = MaterialTheme.colorScheme.primary) },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        )
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = selectedUser != null, onClick = {
                    val person = selectedUser ?: return@TextButton
                    val personUsername = person.username ?: return@TextButton
                    scope.launch {
                        val avatarUri = person.profilePhotoMediaId?.let { "/api/media/${it.trim()}" }
                            ?: FynxProfileRemoteClient.get(context, personUsername).getOrNull()?.profilePhotoMediaId?.let { "/api/media/${it.trim()}" }
                        val newChat = ChatPreview(person.displayName.ifBlank { personUsername }, personUsername, "", "", avatarUri = avatarUri)
                        FynxChatStore.savePreview(context, newChat)
                        chats = FynxChatStore.loadPreviews(context)
                        onOpenChat(newChat)
                        showNewChat = false
                    }
                }) { Text("Open chat") }
            },
            dismissButton = { TextButton(onClick = { showNewChat = false }) { Text("Cancel") } },
        )
    }
}
