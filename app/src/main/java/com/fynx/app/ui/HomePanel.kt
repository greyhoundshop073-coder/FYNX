package com.fynx.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit

@Composable
fun HomePanel(
    currentUsername: String = "preview",
    onOpenChats: () -> Unit = {},
    onOpenStories: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenMarketplace: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenFindPeople: () -> Unit = {}
) {
    val context = LocalContext.current
    var chatPreviews by remember { mutableStateOf(FynxChatStore.loadPreviews(context)) }
    var posts by remember { mutableStateOf(FynxHomePostStore.load(context)) }
    val notifications = remember { FynxNotificationStore.load(context) }
    var composerText by remember { mutableStateOf("") }
    var postVisibility by remember { mutableStateOf(FynxPostVisibility.PUBLIC) }
    var showComposer by remember { mutableStateOf(false) }
    var postMenuId by remember { mutableStateOf<String?>(null) }
    val displayUsername = currentUsername.trim().removePrefix("@").ifBlank { "preview" }

    LaunchedEffect(Unit) {
        chatPreviews = FynxChatStore.loadPreviews(context)
        posts = FynxHomePostStore.load(context)
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
                        FynxAvatar(displayUsername, Modifier.size(48.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Welcome to FYNX", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Your people. Your moments. Your world.", color = FynxDesign.TextSecondary)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PulseStat("💬", chatPreviews.size.toString(), "Chats", onOpenChats, Modifier.weight(1f))
                        PulseStat("🔔", notifications.unreadNotificationCount().toString(), "Updates", onOpenNotifications, Modifier.weight(1f))
                    }
                }
            }
        }

        item {
            Card(onClick = { showComposer = true }, modifier = Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.55f))) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    FynxAvatar(displayUsername, Modifier.size(42.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("What's on your mind?", modifier = Modifier.weight(1f), color = FynxDesign.TextSecondary)
                    Text("Post", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
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
            SectionHeader("Your feed", "Find people", onOpenFindPeople)
        }

        if (posts.isEmpty()) {
            item {
                EmptyHomeCard(
                    "Your feed is ready",
                    "Posts from people you connect with will appear here. Create your first post or find people to build your FYNX circle.",
                    "Find People",
                    onOpenFindPeople
                )
            }
        } else {
            items(posts, key = { it.id }) { post ->
                HomePostCard(
                    post = post,
                    currentUsername = displayUsername,
                    onLike = { FynxHomePostStore.toggleLike(context, post.id); posts = FynxHomePostStore.load(context) },
                    onSave = { FynxHomePostStore.toggleSave(context, post.id); posts = FynxHomePostStore.load(context) },
                    onMenu = { postMenuId = post.id }
                )
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

    if (showComposer) {
        AlertDialog(
            onDismissRequest = { showComposer = false },
            title = { Text("Create a post") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = composerText, onValueChange = { composerText = it }, modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 8, placeholder = { Text("Share something with your FYNX circle…") })
                    Text("Who can see this?", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = postVisibility == FynxPostVisibility.PUBLIC, onClick = { postVisibility = FynxPostVisibility.PUBLIC }, label = { Text("Public") })
                        FilterChip(selected = postVisibility == FynxPostVisibility.FRIENDS_ONLY, onClick = { postVisibility = FynxPostVisibility.FRIENDS_ONLY }, label = { Text("Friends") })
                    }
                    Text("Posts are saved to this signed-in account on this device until FYNX social sync is connected.", style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary)
                }
            },
            confirmButton = {
                Button(onClick = { if (FynxHomePostStore.create(context, composerText, postVisibility) != null) { posts = FynxHomePostStore.load(context); composerText = ""; showComposer = false } }) { Text("Post") }
            },
            dismissButton = { TextButton(onClick = { showComposer = false }) { Text("Cancel") } }
        )
    }

    postMenuId?.let { id ->
        val post = posts.firstOrNull { it.id == id }
        if (post != null) {
            AlertDialog(
                onDismissRequest = { postMenuId = null },
                title = { Text("Post options") },
                text = { Text(if (post.authorUsername.equals("@$displayUsername", ignoreCase = true)) "Manage your post." else "Post options will expand when social sync is connected.") },
                confirmButton = {
                    if (post.authorUsername.equals("@$displayUsername", ignoreCase = true)) {
                        TextButton(onClick = { FynxHomePostStore.delete(context, id); posts = FynxHomePostStore.load(context); postMenuId = null }) { Icon(Icons.Default.DeleteOutline, null); Spacer(Modifier.width(5.dp)); Text("Delete") }
                    } else TextButton(onClick = { postMenuId = null }) { Text("Done") }
                },
                dismissButton = { TextButton(onClick = { postMenuId = null }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun HomePostCard(post: FynxPost, currentUsername: String, onLike: () -> Unit, onSave: () -> Unit, onMenu: () -> Unit) {
    val isOwn = post.authorUsername.equals("@$currentUsername", ignoreCase = true)
    Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = 0.55f))) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FynxAvatar(post.authorUsername, Modifier.size(44.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(post.authorUsername.removePrefix("@"), fontWeight = FontWeight.SemiBold)
                    Text("${relativeTime(post.timestamp)} • ${if (post.visibility == FynxPostVisibility.PUBLIC) "Public" else "Friends"}", style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary)
                }
                IconButton(onClick = onMenu) { Icon(Icons.Default.MoreHoriz, contentDescription = "Post options") }
            }
            Text(post.text, style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onLike) {
                    Icon(if (post.likedByCurrentUser) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
                    Spacer(Modifier.width(5.dp)); Text(if (post.likedByCurrentUser) "Liked" else "Like")
                }
                TextButton(onClick = {}) { Icon(Icons.Default.ChatBubbleOutline, null); Spacer(Modifier.width(5.dp)); Text("Comment") }
                TextButton(onClick = {}) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(5.dp)); Text("Share") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSave) { Icon(Icons.Default.StarBorder, contentDescription = if (post.savedByCurrentUser) "Saved" else "Save", tint = if (post.savedByCurrentUser) MaterialTheme.colorScheme.primary else FynxDesign.TextSecondary) }
            }
        }
    }
}

private fun relativeTime(timestamp: Long): String {
    val elapsed = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 1440 -> "${TimeUnit.MINUTES.toHours(minutes)}h"
        else -> "${TimeUnit.MINUTES.toDays(minutes)}d"
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
