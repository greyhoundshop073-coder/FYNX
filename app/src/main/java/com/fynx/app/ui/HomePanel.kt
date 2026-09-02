package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HomePanel(
    currentUsername: String = "preview",
    onOpenChats: () -> Unit = {},
    onOpenStories: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenMarketplace: () -> Unit = {},
    onOpenNotifications: () -> Unit = {}
) {
    val context = LocalContext.current
    var chatPreviews by remember { mutableStateOf(FynxChatStore.loadPreviews(context)) }
    val notifications = remember { FynxNotificationStore.load(context) }
    val displayUsername = currentUsername.trim().removePrefix("@").ifBlank { "preview" }

    LaunchedEffect(Unit) { chatPreviews = FynxChatStore.loadPreviews(context) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 18.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = FynxDesign.LargeCardShape,
                colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
                border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.55f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                            Box(contentAlignment = Alignment.Center) { Text("✦", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("FYNX Pulse", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Your world at a glance", color = FynxDesign.TextSecondary)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PulseStat("💬", chatPreviews.size.toString(), "Chats", onOpenChats, Modifier.weight(1f))
                        PulseStat("🔔", notifications.unreadNotificationCount().toString(), "Updates", onOpenNotifications, Modifier.weight(1f))
                    }
                    Text("Your Pulse is built from your real FYNX activity. Nothing is invented to make the app look busy.", style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary)
                }
            }
        }

        item {
            SectionHeader("Moments", "See all", onOpenStories)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StoryCircle("＋", "Add story", true, onOpenStories)
                StoryCircle(displayUsername, "Your story", false, onOpenStories)
            }
        }

        item {
            SectionHeader("Your conversations", "Open Chats", onOpenChats)
            Spacer(Modifier.height(8.dp))
            if (chatPreviews.isEmpty()) {
                EmptyHomeCard("Your FYNX circle starts here", "When you connect with real people and start conversations, they will appear here.", "Open Chats", onOpenChats)
            } else {
                val visibleChats = chatPreviews.take(3)
                Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.55f))) {
                    Column(Modifier.fillMaxWidth()) {
                        visibleChats.forEachIndexed { index, chat ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                FynxAvatar(chat.username, Modifier.size(46.dp).clip(CircleShape))
                                Column(Modifier.weight(1f)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(chat.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        Text(chat.time, style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary)
                                    }
                                    Text(chat.lastMessage, style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary, maxLines = 1)
                                }
                                Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Chat", tint = MaterialTheme.colorScheme.primary)
                            }
                            if (index < visibleChats.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }

        item {
            SectionHeader("Marketplace Discover", "Open Market", onOpenMarketplace)
            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.55f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Discover real listings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Products will appear here as real sellers publish listings.", style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary)
                    OutlinedButton(onClick = onOpenMarketplace, modifier = Modifier.fillMaxWidth()) {
                        Text("Explore Marketplace")
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        item {
            Card(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.45f))) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    FynxAvatar(displayUsername, Modifier.size(44.dp))
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
}

@Composable
private fun SectionHeader(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun PulseStat(icon: String, value: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = FynxDesign.SurfaceRaised), shape = FynxDesign.ControlShape) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon); Spacer(Modifier.width(7.dp)); Column { Text(value, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary) }
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
    Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(66.dp)) {
            FynxAvatar(name, Modifier.size(62.dp).border(BorderStroke(2.dp, if (addStory) MaterialTheme.colorScheme.primary else FynxDesign.Outline), CircleShape).clip(CircleShape))
        }
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
