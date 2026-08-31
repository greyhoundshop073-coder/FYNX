package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FynxApp() {
    var selected by remember { mutableStateOf("Home") }
    var openChat by remember { mutableStateOf<ChatPreview?>(null) }
    var message by remember { mutableStateOf("") }

    if (openChat != null) {
        ConversationPanel(chat = openChat!!, onBack = { openChat = null })
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("FYNX") }, actions = { TextButton(onClick = {}) { Text("＋") } }) },
        bottomBar = {
            NavigationBar {
                listOf("Home", "Chats", "Friends", "Stories", "Profile").forEach { item ->
                    NavigationBarItem(
                        selected = selected == item,
                        onClick = { selected = item },
                        icon = { Text(item.take(1)) },
                        label = { Text(item) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when (selected) {
                "Home" -> {
                    Text("What can I help you with?", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Message FYNX…") },
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {}, Modifier.fillMaxWidth()) { Text("Send") }
                }
                "Chats" -> ChatsPanel(onOpenChat = { openChat = it })
                "Friends" -> FriendsPanel()
                "Stories" -> StoriesPanel()
                "Profile" -> ProfilePanel()
            }
        }
    }
}
