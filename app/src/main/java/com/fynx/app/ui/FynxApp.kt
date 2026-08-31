package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FynxApp() {
    var selected by remember { mutableStateOf("Home") }
    var openChat by remember { mutableStateOf<ChatPreview?>(null) }
    var showNotifications by remember { mutableStateOf(false) }
    var authSession by remember { mutableStateOf(AuthSession(state = AuthState.SIGNED_IN, username = "username")) }

    if (openChat != null) {
        ConversationPanel(chat = openChat!!, onBack = { openChat = null })
        return
    }
    if (showNotifications) {
        NotificationPanel(notifications = emptyList(), onBack = { showNotifications = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("FYNX", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            FynxVerifiedBadge()
                        }
                        Text("Connect • Create • Discover", style = MaterialTheme.typography.labelSmall)
                    }
                },
                actions = {
                    TextButton(onClick = { showNotifications = true }) { Text("🔔") }
                    TextButton(onClick = {}) { Text("＋") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                listOf("Home", "Chats", "Friends", "Stories", "Studio", "Profile").forEach { item ->
                    NavigationBarItem(selected = selected == item, onClick = { selected = item }, icon = { Text(item.take(1)) }, label = { Text(item) })
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
                "Studio" -> AiStudioPanel()
                "Profile" -> ProfilePanel(session = authSession, onSignOut = { authSession = AuthSession() })
            }
        }
    }
}
