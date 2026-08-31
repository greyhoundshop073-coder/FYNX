package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ConversationPanel(chat: ChatPreview, onBack: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf(listOf(ChatMessage(chat.lastMessage, false, id = "initial", delivered = true, read = true))) }
    var replyToId by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Column(Modifier.padding(start = 4.dp)) {
                Text(chat.name, style = MaterialTheme.typography.titleMedium)
                Text(if (chat.online) "Online" else chat.username, style = MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.fromMe) Arrangement.End else Arrangement.Start) {
                    Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            message.replyToId?.let { Text("Replying to a message", style = MaterialTheme.typography.labelSmall) }
                            Text(message.text)
                            if (message.fromMe) {
                                Text(if (message.read) "Read" else if (message.delivered) "Delivered" else "Sent", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(onClick = { replyToId = message.id }) { Text("Reply") }
                        }
                    }
                }
            }
        }
        replyToId?.let {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Replying", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { replyToId = null }) { Text("Cancel") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {}) { Text("＋") }
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("Message…") })
            TextButton(onClick = {
                val value = text.trim()
                if (value.isNotEmpty()) {
                    messages = messages + ChatMessage(
                        text = value,
                        fromMe = true,
                        id = System.currentTimeMillis().toString(),
                        delivered = true,
                        read = false,
                        replyToId = replyToId
                    )
                    text = ""
                    replyToId = null
                }
            }) { Text("Send") }
        }
    }
}
