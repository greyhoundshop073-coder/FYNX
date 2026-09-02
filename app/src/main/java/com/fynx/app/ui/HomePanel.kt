package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class HomeMarketplacePick(
    val name: String,
    val price: String,
    val category: String
)

@Composable
fun HomePanel(
    onOpenChats: () -> Unit = {},
    onOpenStories: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenMarketplace: () -> Unit = {}
) {
    val context = LocalContext.current
    var chatPreviews by remember { mutableStateOf(FynxChatStore.loadPreviews(context)) }
    val notifications = remember { FynxNotificationStore.load(context) }

    LaunchedEffect(Unit) {
        chatPreviews = FynxChatStore.loadPreviews(context)
    }

    val marketplacePicks = remember {
        listOf(
            HomeMarketplacePick("Wireless Headphones", "₦45,000", "Electronics"),
            HomeMarketplacePick("Classic Sneakers", "₦32,000", "Fashion"),
            HomeMarketplacePick("Travel Backpack", "₦28,000", "Fashion")
        )
    }

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
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("✦", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("FYNX Pulse", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Your world at a glance", color = FynxDesign.TextSecondary)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PulseStat("💬", chatPreviews.size.toString(), "Chats", onOpenChats, Modifier.weight(1f))
                        PulseStat("🔔", notifications.unreadNotificationCount().toString(), "Updates", { }, Modifier.weight(1f))
                    }
                    Text(
                        "FYNX Pulse will grow with your real friends, conversations, stories, groups and activity. No artificial activity is added.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FynxDesign.TextSecondary
                    )
                }
            }
        }

        item {
            SectionHeader("Moments", "See all", onOpenStories)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StoryCircle("＋", "Add story", true, onOpenStories)
                StoryCircle("You", "Your story", false, onOpenStories)
            }
        }

        item {
            SectionHeader("Your conversations", "Open Chats", onOpenChats)
            Spacer(Modifier.height(8.dp))
            if (chatPreviews.isEmpty()) {
                EmptyHomeCard(
                    "Your FYNX circle starts here",
                    "When you connect with real people and start conversations, they will appear here.",
                    "Open Chats",
                    onOpenChats
                )
            } else {
                val visibleChats = chatPreviews.take(3)
                Card(
                    Modifier.fillMaxWidth(),
                    shape = FynxDesign.LargeCardShape,
                    colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
                    border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.55f))
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        visibleChats.forEachIndexed { index, chat ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
            Card(
                Modifier.fillMaxWidth(),
                shape = FynxDesign.LargeCardShape,
                colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
                border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.55f))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = FynxDesign.ControlShape, color = FynxDesign.SelectedContainer) {
                            Icon(Icons.Default.ShoppingBag, contentDescription = "Marketplace", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(9.dp).size(22.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Discover while you scroll", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Marketplace photos and videos can appear here as real listings are published.", style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary)
                        }
                    }
                    marketplacePicks.forEach { product ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(58.dp).background(FynxDesign.SurfaceRaised, FynxDesign.ControlShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.ShoppingBag, contentDescription = "Product", tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(product.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Text(product.category, style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary)
                            }
                            Text(product.price, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    OutlinedButton(onClick = onOpenMarketplace, modifier = Modifier.fillMaxWidth()) {
                        Text("Explore Marketplace")
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        item {
            Card(
                onClick = onOpenProfile,
                modifier = Modifier.fillMaxWidth(),
                shape = FynxDesign.CardShape,
                colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
                border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.45f))
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
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
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = FynxDesign.SurfaceRaised),
        shape = FynxDesign.ControlShape
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon)
            Spacer(Modifier.width(7.dp))
            Column {
                Text(value, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary)
            }
        }
    }
}

@Composable
private fun EmptyHomeCard(title: String, description: String, action: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = FynxDesign.LargeCardShape,
        colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface),
        border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.55f))
    ) {
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
            FynxAvatar(
                name,
                Modifier.size(62.dp).border(
                    BorderStroke(2.dp, if (addStory) MaterialTheme.colorScheme.primary else FynxDesign.Outline),
                    CircleShape
                ).clip(CircleShape)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
