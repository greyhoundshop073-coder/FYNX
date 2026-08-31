package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FynxApp() {
    var selected by remember { mutableStateOf("Home") }
    var openChat by remember { mutableStateOf<ChatPreview?>(null) }

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
        Box(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            when (selected) {
                "Home" -> HomePanel()
                "Chats" -> ChatsPanel(onOpenChat = { openChat = it })
                "Friends" -> FriendsPanel()
                "Stories" -> StoriesPanel()
                "Profile" -> ProfilePanel()
            }
        }
    }
}
