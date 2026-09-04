package com.fynx.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@Composable
fun HomePanel(currentUsername: String = "preview", onOpenChats: () -> Unit = {}, onOpenStories: () -> Unit = {}, onOpenProfile: () -> Unit = {}, onOpenMarketplace: () -> Unit = {}, onOpenNotifications: () -> Unit = {}, onOpenFindPeople: () -> Unit = {}, onOpenAi: () -> Unit = {}) {
    val context = LocalContext.current
    val displayUsername = currentUsername.trim().removePrefix("@").ifBlank { "preview" }
    var chatPreviews by remember { mutableStateOf(FynxChatStore.loadPreviews(context)) }
    var posts by remember { mutableStateOf(FynxHomePostStore.load(context)) }
    var composerText by remember { mutableStateOf("") }
    var postVisibility by remember { mutableStateOf(FynxPostVisibility.PUBLIC) }
    var showComposer by remember { mutableStateOf(false) }
    var postMenuId by remember { mutableStateOf<String?>(null) }
    var commentPostId by remember { mutableStateOf<String?>(null) }
    var pickedPostPhoto by remember { mutableStateOf<Uri?>(null) }
    val profilePhoto = FynxPreferencesStore.loadProfilePhoto(context)
    val notifications = remember { FynxNotificationStore.load(context) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) pickedPostPhoto = uri }
    LaunchedEffect(Unit) { chatPreviews = FynxChatStore.loadPreviews(context); posts = FynxHomePostStore.load(context) }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
        item { FynxVisibleUpdatesPanel(currentUsername = currentUsername, onOpenStories = onOpenStories, onOpenAi = onOpenAi) }
        item { Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface, contentColor = FynxDesign.TextPrimary), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { FynxProfileImage(displayUsername, profilePhoto, Modifier.size(48.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Welcome to FYNX", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Your people. Your moments. Your world.", color = FynxDesign.TextSecondary) } }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { PulseStat("✨", "AI", "Assistant", onOpenAi, Modifier.weight(1f)); PulseStat("🔔", notifications.unreadNotificationCount().toString(), "Updates", onOpenNotifications, Modifier.weight(1f)) } } } }
        item { Card(onClick = { showComposer = true }, Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface, contentColor = FynxDesign.TextPrimary), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { FynxProfileImage(displayUsername, profilePhoto, Modifier.size(42.dp)); Spacer(Modifier.width(12.dp)); Text("What's on your mind?", Modifier.weight(1f), color = FynxDesign.TextSecondary); Icon(Icons.Default.AddAPhoto, "Add photo", tint = MaterialTheme.colorScheme.primary) } } }
        item { SectionHeader("Moments", "See all", onOpenStories); Spacer(Modifier.height(8.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { StoryCircle("＋", "Add story", true, onOpenStories); StoryCircle(displayUsername, "Your story", false, onOpenStories) } }
        item { FynxRemoteHomeSocialPanel(currentUsername = displayUsername, onOpenFindPeople = onOpenFindPeople) }
        item { Card(onClick = onOpenProfile, Modifier.fillMaxWidth(), shape = FynxDesign.CardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface, contentColor = FynxDesign.TextPrimary), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .45f))) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { FynxProfileImage(displayUsername, profilePhoto, Modifier.size(44.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("Your profile", fontWeight = FontWeight.SemiBold); Text("Photo, bio and account details", style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary) }; Text("›", style = MaterialTheme.typography.titleLarge, color = FynxDesign.TextSecondary) } } }
    }

    if (showComposer) FynxPlainDialog(onDismissRequest = { showComposer = false; pickedPostPhoto = null }, title = { Text("Create a post") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(composerText, { composerText = it }, Modifier.fillMaxWidth(), minLines = 3, maxLines = 7, placeholder = { Text("Share something with your FYNX circle…") }); OutlinedButton(onClick = { picker.launch("image/*") }, Modifier.fillMaxWidth()) { Icon(Icons.Default.AddAPhoto, null); Spacer(Modifier.width(6.dp)); Text(if (pickedPostPhoto == null) "Add photo" else "Change photo") }; pickedPostPhoto?.toString()?.let { PostImage(it, Modifier.fillMaxWidth().heightIn(max = 240.dp)) }; Text("Who can see this?", style = MaterialTheme.typography.labelLarge); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(postVisibility == FynxPostVisibility.PUBLIC, { postVisibility = FynxPostVisibility.PUBLIC }, label = { Text("Public") }); FilterChip(postVisibility == FynxPostVisibility.FRIENDS_ONLY, { postVisibility = FynxPostVisibility.FRIENDS_ONLY }, label = { Text("Friends") }) }; Text("Saved to this account on this device until secure FYNX social sync is connected.", style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary) } }, confirmButton = { Button(onClick = { if (FynxHomePostStore.create(context, composerText, postVisibility, pickedPostPhoto?.toString()) != null) { posts = FynxHomePostStore.load(context); composerText = ""; pickedPostPhoto = null; showComposer = false } }) { Text("Post") } }, dismissButton = { TextButton(onClick = { showComposer = false; pickedPostPhoto = null }) { Text("Cancel") } })
    postMenuId?.let { id -> posts.firstOrNull { it.id == id }?.let { post -> AlertDialog(onDismissRequest = { postMenuId = null }, title = { Text("Post options") }, text = { Text(if (post.authorUsername.equals("@$displayUsername", true)) "Manage your post." else "Post options will expand when social sync is connected.") }, confirmButton = { TextButton(onClick = { if (post.authorUsername.equals("@$displayUsername", true)) { FynxHomePostStore.delete(context, id); posts = FynxHomePostStore.load(context) }; postMenuId = null }) { Text(if (post.authorUsername.equals("@$displayUsername", true)) "Delete" else "Done") } }, dismissButton = { TextButton(onClick = { postMenuId = null }) { Text("Cancel") } }) } }
    commentPostId?.let { id -> posts.firstOrNull { it.id == id }?.let { post -> var comment by remember(id) { mutableStateOf("") }; AlertDialog(onDismissRequest = { commentPostId = null }, title = { Text("Comments") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("${post.commentCount} comment${if (post.commentCount == 1) "" else "s"}", color = FynxDesign.TextSecondary); OutlinedTextField(comment, { comment = it }, Modifier.fillMaxWidth(), placeholder = { Text("Write a comment…") }, singleLine = true) } }, confirmButton = { TextButton(onClick = { if (comment.isNotBlank()) { FynxHomePostStore.addComment(context, id); posts = FynxHomePostStore.load(context); commentPostId = null } }) { Text("Comment") } }, dismissButton = { TextButton(onClick = { commentPostId = null }) { Text("Close") } }) } }
}

@Composable private fun HomePostCard(post: FynxPost, onLike: () -> Unit, onComment: () -> Unit, onSave: () -> Unit, onMenu: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface, contentColor = FynxDesign.TextPrimary), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) { Column(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { FynxAvatar(post.authorUsername, Modifier.size(46.dp).clip(CircleShape)); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(post.authorUsername.removePrefix("@"), fontWeight = FontWeight.SemiBold); Text("${relativeTime(post.timestamp)} • ${if (post.visibility == FynxPostVisibility.PUBLIC) "Public" else "Friends"}", style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary) }; IconButton(onClick = onMenu) { Icon(Icons.Default.MoreHoriz, "Post options") } }; if (post.text.isNotBlank()) Text(post.text, Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.bodyLarge); post.mediaUri?.let { PostImage(it, Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 520.dp).padding(top = 8.dp)) }; Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onLike) { Icon(if (post.likedByCurrentUser) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null); Spacer(Modifier.width(4.dp)); Text(post.likeCount.toString()) }; TextButton(onClick = onComment) { Icon(Icons.Default.ChatBubbleOutline, null); Spacer(Modifier.width(4.dp)); Text(post.commentCount.toString()) }; TextButton(onClick = {}) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(4.dp)); Text("Share") }; Spacer(Modifier.weight(1f)); IconButton(onClick = onSave) { Icon(if (post.savedByCurrentUser) Icons.Default.Star else Icons.Default.StarBorder, "Save", tint = if (post.savedByCurrentUser) MaterialTheme.colorScheme.primary else FynxDesign.TextSecondary) } } } }
}

@Composable fun FynxProfileImage(name: String, uriString: String?, modifier: Modifier = Modifier) { val context = LocalContext.current; var bitmap by remember(uriString) { mutableStateOf<Bitmap?>(null) }; LaunchedEffect(uriString) { bitmap = withContext(Dispatchers.IO) { uriString?.let { runCatching { context.contentResolver.openInputStream(Uri.parse(it)).use { input -> BitmapFactory.decodeStream(input) } }.getOrNull() } } }; if (bitmap != null) Image(bitmap!!.asImageBitmap(), name, modifier.clip(CircleShape), contentScale = ContentScale.Crop) else FynxAvatar(name, modifier.clip(CircleShape)) }
@Composable private fun PostImage(uriString: String, modifier: Modifier = Modifier) { val context = LocalContext.current; var bitmap by remember(uriString) { mutableStateOf<Bitmap?>(null) }; LaunchedEffect(uriString) { bitmap = withContext(Dispatchers.IO) { runCatching { context.contentResolver.openInputStream(Uri.parse(uriString)).use { BitmapFactory.decodeStream(it) } }.getOrNull() } }; if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Post photo", modifier, contentScale = ContentScale.Crop) }
private fun relativeTime(timestamp: Long): String { val elapsed = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L); val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed); return when { minutes < 1 -> "now"; minutes < 60 -> "${minutes}m"; minutes < 1440 -> "${TimeUnit.MINUTES.toHours(minutes)}h"; else -> "${TimeUnit.MINUTES.toDays(minutes)}d" } }
@Composable private fun SectionHeader(title: String, action: String, onClick: () -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); TextButton(onClick = onClick) { Text(action) } } }
@Composable private fun PulseStat(icon: String, value: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) { Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(FynxDesign.SurfaceRaised), shape = FynxDesign.ControlShape) { Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Text(icon); Spacer(Modifier.width(7.dp)); Column { Text(value, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary) } } } }
@Composable private fun EmptyHomeCard(title: String, description: String, action: String, onClick: () -> Unit) { Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(containerColor = FynxDesign.Surface, contentColor = FynxDesign.TextPrimary), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(description, color = FynxDesign.TextSecondary); TextButton(onClick = onClick) { Text(action) } } } }
@Composable private fun StoryCircle(name: String, label: String, addStory: Boolean, onClick: () -> Unit) { Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally) { IconButton(onClick = onClick, modifier = Modifier.size(66.dp)) { FynxAvatar(name, Modifier.size(62.dp).border(BorderStroke(2.dp, if (addStory) MaterialTheme.colorScheme.primary else FynxDesign.Outline), CircleShape).clip(CircleShape)) }; Spacer(Modifier.height(3.dp)); Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) } }
