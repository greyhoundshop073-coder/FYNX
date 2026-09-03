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

    fun refreshChats() { chats = FynxChatStore.loadPreviews(context) }

    LaunchedEffect(showNewChat, username) {
        if (!showNewChat || username.trim().length < 2) {
            searchResults = emptyList()
            searchBusy = false
            return@LaunchedEffect
        }
        delay(250L)
        searchBusy = true
        searchError = null
        FynxSocialClient.searchUsers(context, username.trim())
            .onSuccess { searchResults = it }
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
                    items(chats, key = { it.username }) { chat ->
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
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Your groups", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onCreateGroup) { Text("Create group") }
            }
            Spacer(Modifier.height(8.dp))
            if (groups.isEmpty()) {
                Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No groups yet", style = MaterialTheme.typography.titleMedium)
                        Text("Groups will appear here after you create or join one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(groups, key = { it.id }) { group ->
                        Card(onClick = { onOpenGroup(group.id) }, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                            ListItem(
                                headlineContent = { Text(group.name) },
                                leadingContent = { FynxAvatar(group.name) },
                                supportingContent = { Text(group.members.size.toString() + " members · " + group.description, color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true, usePlatformDefaultWidth = true),
            title = { Text("New chat") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Search for an existing FYNX account. You cannot open a production conversation for an account that does not exist.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = username, onValueChange = { username = it.take(50); selectedUser = null }, label = { Text("Search username") }, prefix = { Text("@") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    searchError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    if (searchBusy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else if (username.trim().length >= 2) {
                        if (searchResults.isEmpty()) Text("No matching FYNX accounts.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        else LazyColumn(Modifier.heightIn(max = 220.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(searchResults, key = { it.id.ifBlank { it.username } }) { person ->
                                val selected = selectedUser?.username.equals(person.username, true)
                                OutlinedButton(onClick = { selectedUser = person }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(person.displayName.ifBlank { person.username }, style = MaterialTheme.typography.titleSmall)
                                        Text("@${person.username.removePrefix("@")}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    selectedUser?.let { person ->
                        Text("Selected: @${person.username.removePrefix("@")}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = selectedUser != null, onClick = {
                    val person = selectedUser ?: return@TextButton
                    val normalized = person.username.removePrefix("@").trim()
                    val chat = ChatPreview(person.displayName.ifBlank { normalized }, "@$normalized", "", "New")
                    FynxChatStore.savePreview(context, chat)
                    refreshChats()
                    showNewChat = false
                    onOpenChat(chat)
                }) { Text("Open chat") }
            },
            dismissButton = { TextButton(onClick = { showNewChat = false }) { Text("Cancel") } }
        )
    }
}
