package com.fynx.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit

@Composable
fun HomePanel(
    currentUsername: String = "preview",
    onOpenChats: () -> Unit = {},
    onOpenStories: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenMarketplace: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenFindPeople: () -> Unit = {},
    onOpenAi: () -> Unit = {},
    onCreatePost: () -> Unit = {}
) {
    val context = LocalContext.current
    val displayUsername = currentUsername.trim().removePrefix("@").ifBlank { "preview" }
    val profilePhoto = FynxPreferencesStore.loadProfilePhoto(context)
    val notifications = remember { FynxNotificationStore.load(context) }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 18.dp)
    ) {
        item { FynxVisibleUpdatesPanel(currentUsername = currentUsername, onOpenStories = onOpenStories, onOpenAi = onOpenAi) }
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = FynxDesign.LargeCardShape,
                colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface, contentColor = FynxDesign.TextPrimary),
                border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FynxProfileImage(displayUsername, profilePhoto, Modifier.size(48.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Welcome to FYNX", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Your people. Your moments. Your world.", color = FynxDesign.TextSecondary)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PulseStat("✨", "AI", "Assistant", onOpenAi, Modifier.weight(1f))
                        PulseStat("🔔", notifications.unreadNotificationCount().toString(), "Updates", onOpenNotifications, Modifier.weight(1f))
                    }
                    HomeAiVoiceCard()
                }
            }
        }
        item {
            Card(
                onClick = onCreatePost,
                modifier = Modifier.fillMaxWidth(),
                shape = FynxDesign.LargeCardShape,
                colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface, contentColor = FynxDesign.TextPrimary),
                border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    FynxProfileImage(displayUsername, profilePhoto, Modifier.size(42.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("What's on your mind?", Modifier.weight(1f), color = FynxDesign.TextSecondary)
                    Icon(Icons.Default.AddAPhoto, "Create post", tint = MaterialTheme.colorScheme.primary)
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
        item { FynxRemoteHomeSocialPanel(currentUsername = displayUsername, onOpenFindPeople = onOpenFindPeople) }
        item {
            Card(
                onClick = onOpenProfile,
                Modifier.fillMaxWidth(),
                shape = FynxDesign.CardShape,
                colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface, contentColor = FynxDesign.TextPrimary),
                border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .45f))
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    FynxProfileImage(displayUsername, profilePhoto, Modifier.size(44.dp))
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

@Composable private fun HomePostCard(post: FynxPost, onLike: () -> Unit, onComment: () -> Unit, onSave: () -> Unit, onMenu: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface, contentColor = FynxDesign.TextPrimary), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                FynxAvatar(post.authorUsername, Modifier.size(46.dp).clip(CircleShape))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(post.authorUsername.removePrefix("@"), fontWeight = FontWeight.SemiBold)
                    Text("${relativeTime(post.timestamp)} • ${if (post.visibility == FynxPostVisibility.PUBLIC) "Public" else "Friends"}", style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary)
                }
                IconButton(onClick = onMenu) { Icon(Icons.Default.MoreHoriz, "Post options") }
            }
            if (post.text.isNotBlank()) Text(post.text, Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onLike) { Text("Like ${post.likeCount}") }
                TextButton(onClick = onComment) { Text("Comment ${post.commentCount}") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSave) { Text(if (post.savedByCurrentUser) "Saved" else "Save") }
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

@Composable private fun SectionHeader(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable private fun PulseStat(icon: String, value: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(FynxDesign.SurfaceRaised), shape = FynxDesign.ControlShape) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon)
            Spacer(Modifier.width(7.dp))
            Column { Text(value, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary) }
        }
    }
}

@Composable private fun StoryCircle(name: String, label: String, addStory: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.IconButton(onClick = onClick, modifier = Modifier.size(66.dp)) {
            FynxAvatar(name, Modifier.size(62.dp).clip(CircleShape))
        }
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
