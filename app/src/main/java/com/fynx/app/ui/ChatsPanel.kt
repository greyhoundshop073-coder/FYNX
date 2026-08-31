package com.fynx.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChatsPanel(onOpenChat: (ChatPreview) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text("Chats", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = {}) { Text("＋ New chat") }
        Spacer(Modifier.height(12.dp))
        LazyColumn {
            items(sampleChats) { chat ->
                ListItem(
                    headlineContent = { Text(chat.name) },
                    leadingContent = { FynxAvatar(chat.name) },
                    supportingContent = { Text("${chat.username} · ${chat.lastMessage}") },
                    trailingContent = { Text(chat.time) },
                    modifier = Modifier.fillMaxWidth().clickable { onOpenChat(chat) }
                )
                HorizontalDivider()
            }
        }
    }
}
