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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
fun ChatsPanel(onOpenChat: (ChatPreview) -> Unit, onOpenGroup: (String) -> Unit = {}, onCreateGroup: () -> Unit = {}) {
    var section by remember { mutableStateOf("Chats") }
    val context = LocalContext.current
    var chats by remember { mutableStateOf(FynxChatStore.loadPreviews(context)) }
    var groups by remember { mutableStateOf(FynxGroupsStore.load(context)) }
    var showNewChat by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }

    fun refreshChats() { chats = FynxChatStore.loadPreviews(context) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Chats", style = MaterialTheme.typography.headlineSmall)
                Text("Messages and groups in one place", color = FynxDesign.TextSecondary)
            }
            TextButton(onClick = { section = if (section == "Chats") "Groups" else "Chats" }) {
                Text(if (section == "Chats") "Groups" else "Chats")
            }
        }
        Spacer(Modifier.height(12.dp))

        if (section == "Chats") {
            OutlinedButton(onClick = { name = ""; username = ""; showNewChat = true }, shape = FynxDesign.ControlShape, border = BorderStroke(1.dp, FynxDesign.Outline)) { Text("＋ New chat") }
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
                                supportingContent = { Text(chat.lastMessage.ifBlank { "No messages yet" }, color = FynxDesign.TextSecondary) },
                                trailingContent = { Text(chat.time, color = FynxDesign.TextSecondary) },
                                colors = ListItemDefaults.colors(containerColor = FynxDesign.Surface)
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
                        Text("Groups will appear here after you create or join one.", color = FynxDesign.TextSecondary)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(groups, key = { it.id }) { group ->
                        Card(onClick = { onOpenGroup(group.id) }, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline)) {
                            ListItem(
                                headlineContent = { Text(group.name) },
                                leadingContent = { FynxAvatar(group.name) },
                                supportingContent = { Text(group.members.size.toString() + " members · " + group.description, color = FynxDesign.TextSecondary) },
                                colors = ListItemDefaults.colors(containerColor = FynxDesign.Surface)
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
                val dialogView = LocalView.current
                SideEffect { (dialogView.parent as? DialogWindowProvider)?.window?.setDimAmount(0f) }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Enter the person’s real FYNX identity. Messages use the production FYNX service and remain available across supported devices.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(value = username, onValueChange = { username = it.take(50) }, label = { Text("Username") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && username.isNotBlank(), onClick = {
                    val normalized = if (username.trim().startsWith("@")) username.trim() else "@${username.trim()}"
                    val chat = ChatPreview(name.trim(), normalized, "", "New")
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
