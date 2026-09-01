package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
    Column(
        Modifier
            .fillMaxSize()
            .background(FynxDesign.Background)
            .padding(16.dp)
    ) {
        Text("Chats", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text("Stay connected with your conversations", color = FynxDesign.TextSecondary)
        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = {},
            shape = FynxDesign.ControlShape,
            border = BorderStroke(1.dp, FynxDesign.Outline)
        ) { Text("＋ New chat") }
        Spacer(Modifier.height(14.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(sampleChats, key = { it.username }) { chat ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenChat(chat) },
                    shape = FynxDesign.CardShape,
                    colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
                    border = BorderStroke(1.dp, FynxDesign.Outline)
                ) {
                    ListItem(
                        headlineContent = { Text(chat.name) },
                        leadingContent = { FynxAvatar(chat.name) },
                        supportingContent = { Text("${chat.username} · ${chat.lastMessage}", color = FynxDesign.TextSecondary) },
                        trailingContent = { Text(chat.time, color = FynxDesign.TextSecondary) },
                        colors = ListItemDefaults.colors(containerColor = FynxDesign.Surface)
                    )
                }
            }
        }
    }
}
