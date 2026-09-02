package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HomePanel(
    onOpenChats: () -> Unit = {},
    onOpenStories: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Stories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onOpenStories) { Text("See all") }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StoryCircle("＋", "Add story", true, onOpenStories)
            StoryCircle("You", "Your story", false, onOpenStories)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Chats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onOpenChats) { Text("See all") }
        }

        if (sampleChats.isEmpty()) {
            EmptyHomeCard("No conversations yet", "Start a chat with a friend and your conversations will appear here.", "Open Chats", onOpenChats)
        } else {
            Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.55f))) {
                Column(Modifier.fillMaxWidth()) {
                    sampleChats.take(4).forEachIndexed { index, chat ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            FynxAvatar(chat.name, Modifier.size(48.dp).clip(CircleShape))
                            Column(Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(chat.name, fontWeight = FontWeight.SemiBold)
                                    Text(chat.time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(chat.lastMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                        if (index < sampleChats.take(4).lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 74.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    }
                }
            }
        }

        Card(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.45f))) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                FynxAvatar("You", Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Your profile", fontWeight = FontWeight.SemiBold)
                    Text("Photo, bio and account details", style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary)
                }
                Text("›", style = MaterialTheme.typography.titleLarge, color = FynxDesign.TextSecondary)
            }
        }
    }
}

@Composable
private fun EmptyHomeCard(title: String, description: String, action: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.55f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, color = FynxDesign.TextSecondary)
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun StoryCircle(name: String, label: String, addStory: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(72.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(66.dp)) {
            FynxAvatar(name, Modifier.size(62.dp).border(BorderStroke(2.dp, if (addStory) MaterialTheme.colorScheme.primary else FynxDesign.Outline), CircleShape).clip(CircleShape))
        }
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}