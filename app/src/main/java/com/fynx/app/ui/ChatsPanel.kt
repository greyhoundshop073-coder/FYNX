package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

@Composable
fun ChatsPanel(onOpenChat: (ChatPreview) -> Unit, onOpenGroup: (String) -> Unit = {}, onCreateGroup: () -> Unit = {}) {
    var section by remember { mutableStateOf("Chats") }
    val context = LocalContext.current
    var chats by remember { mutableStateOf(FynxChatStore.loadPreviews(context)) }
    var groups by remember { mutableStateOf(FynxGroupsStore.load(context)) }
    var showNewChat by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(emptyList<FynxSocialClient.User>()) }
    var selectedUser by remember { mutableStateOf<FynxSocialClient.User?>(null) }
    var searchBusy by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var selfUsername by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        selfUsername = (FynxAuthStore.load(context).username ?: "").removePrefix("@").trim().lowercase()
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
                Text("No conversations yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("Start a chat with a real FYNX username. Messages are synchronized through the FYNX account service.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                    items(chats.filterNot { chat ->
                        val candidate = chat.username.removePrefix("@").trim().lowercase()
                        selfUsername.isNotBlank() && candidate == selfUsername
                    }, key = { it.username }) { chat ->
                        Card(onClick = { onOpenChat(chat) }, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                            ListItem(
                                headlineContent = { Text(chat.name) },
                                leadingContent = { FynxAvatar(chat.name) },
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
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                    items(groups, key = { it.id }) { group ->
                        Card(onClick = { onOpenGroup(group.id) }, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                            ListItem(
                                headlineContent = { Text(group.name) },
                                leadingContent = { FynxAvatar(group.name) },
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
        AlertDialog(
            onDismissRequest = { showNewChat = false },
            title = { Text("New chat") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(username, { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search username") }, singleLine = true)
                    if (searchBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    searchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    searchResults.forEach { person ->
                        val personUsername = person.username ?: ""
                        ListItem(
                            headlineContent = { Text(person.displayName.ifBlank { personUsername }) },
                            supportingContent = { Text("@$personUsername") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingContent = { FynxAvatar(person.displayName.ifBlank { personUsername }) },
                            trailingContent = { if (selectedUser?.username == person.username) Text("✓") },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        )
                        HorizontalDivider()
                        TextButton(onClick = { selectedUser = person }) { Text("Select") }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = selectedUser != null, onClick = {
                    val person = selectedUser ?: return@TextButton
                    val personUsername = person.username ?: return@TextButton
                    onOpenChat(ChatPreview(person.displayName.ifBlank { personUsername }, personUsername, "", ""))
                    showNewChat = false
                }) { Text("Open chat") }
            },
            dismissButton = { TextButton(onClick = { showNewChat = false }) { Text("Cancel") } },
            properties = DialogProperties(usePlatformDefaultWidth = true)
        )
    }
}
