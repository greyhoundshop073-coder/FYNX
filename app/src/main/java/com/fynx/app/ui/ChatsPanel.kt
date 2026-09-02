package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatsPanel(onOpenChat: (ChatPreview) -> Unit, onOpenGroup: (String) -> Unit = {}) {
    var section by remember { mutableStateOf("Chats") }
    val context = androidx.compose.ui.platform.LocalContext.current
    var groups by remember { mutableStateOf(FynxGroupsStore.load(context)) }

    Column(Modifier.fillMaxSize().background(FynxDesign.Background).padding(16.dp)) {
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
            OutlinedButton(onClick = { }, shape = FynxDesign.ControlShape, border = BorderStroke(1.dp, FynxDesign.Outline)) { Text("＋ New chat") }
            Spacer(Modifier.height(14.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(sampleChats, key = { it.username }) { chat ->
                    Card(onClick = { onOpenChat(chat) }, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline)) {
                        ListItem(
                            headlineContent = { Text(chat.name) },
                            leadingContent = { FynxAvatar(chat.name) },
                            supportingContent = { Text(chat.lastMessage, color = FynxDesign.TextSecondary) },
                            trailingContent = { Text(chat.time, color = FynxDesign.TextSecondary) },
                            colors = ListItemDefaults.colors(containerColor = FynxDesign.Surface)
                        )
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Your groups", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { }) { Text("Create group") }
            }
            Spacer(Modifier.height(8.dp))
            if (groups.isEmpty()) {
                Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No groups yet", style = MaterialTheme.typography.titleMedium)
                        Text("Groups will appear here after you create or join one.", color = FynxDesign.TextSecondary)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(groups, key = { it.id }) { group ->
                        Card(onClick = { onOpenGroup(group.name) }, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline)) {
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
}