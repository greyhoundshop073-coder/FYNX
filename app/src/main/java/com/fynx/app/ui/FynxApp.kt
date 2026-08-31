package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
                else -> ProfilePanel()
            }
        }
    }
}

@Composable private fun FriendsPanel() {
    Text("Friends", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(value = "", onValueChange = {}, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Search by username") })
    Spacer(Modifier.height(16.dp))
    listOf("Add friends by username", "Send messages", "Start a group").forEach {
        Text("•  $it", Modifier.padding(vertical = 8.dp))
    }
}

@Composable private fun StoriesPanel() {
    Text("Stories", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(12.dp))
    Button(onClick = {}) { Text("＋ Add story") }
    Spacer(Modifier.height(16.dp))
    LazyColumn {
        items(listOf("Your story", "Friends' stories", "Story privacy")) {
            Text(it, Modifier.fillMaxWidth().padding(14.dp))
        }
    }
}

@Composable private fun ProfilePanel() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Surface(Modifier.size(88.dp), CircleShape, tonalElevation = 2.dp) {
            Box(contentAlignment = Alignment.Center) { Text("👤", style = MaterialTheme.typography.headlineMedium) }
        }
        Spacer(Modifier.height(12.dp))
        Text("Your FYNX profile", style = MaterialTheme.typography.titleLarge)
        Text("Username • Profile photo • Bio")
        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = {}) { Text("Settings") }
    }
}
