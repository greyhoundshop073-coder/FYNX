package com.fynx.app.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

@Composable
fun FynxRemoteHomeSocialPanel(currentUsername: String, onOpenFindPeople: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var posts by remember { mutableStateOf<List<FynxRemoteSocialClient.RemotePost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var composerOpen by remember { mutableStateOf(false) }
    var selectedMedia by remember { mutableStateOf<Uri?>(null) }
    var composerText by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf(FynxPostVisibility.PUBLIC) }
    var busy by remember { mutableStateOf(false) }
    var commentsPost by remember { mutableStateOf<FynxRemoteSocialClient.RemotePost?>(null) }
    var likesPost by remember { mutableStateOf<FynxRemoteSocialClient.RemotePost?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selectedMedia = it }

    fun reload() {
        scope.launch {
            loading = true
            FynxRemoteSocialClient.feed(context).onSuccess { posts = it; error = null }.onFailure { error = it.message ?: "Unable to load your feed." }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Your feed", style = MaterialTheme.typography.titleMedium)
                Text("Real posts from your FYNX network", style = MaterialTheme.typography.bodySmall)
            }
            Row {
                IconButton(onClick = { reload() }) { Icon(Icons.Default.Refresh, "Refresh feed") }
                IconButton(onClick = { composerOpen = true }) { Icon(Icons.Default.Add, "Create post") }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!loading && posts.isEmpty() && error == null) {
            Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your feed is ready", style = MaterialTheme.typography.titleMedium)
                    Text("There are no visible posts yet. Create a post or find real people to build your FYNX circle.")
                    OutlinedButton(onClick = onOpenFindPeople) { Text("Find People") }
                }
            }
        }
        posts.forEach { post ->
            RemotePostCard(
                post = post,
                currentUsername = currentUsername,
                onLike = { id -> scope.launch { FynxRemoteSocialClient.like(context, id).onSuccess { result -> val (liked, count) = result; posts = posts.map { if (it.id == id) it.copy(likedByCurrentUser = liked, likeCount = count) else it } }.onFailure { error = it.message } } },
                onComment = { commentsPost = post },
                onLikes = { likesPost = post },
                onFollow = { following -> scope.launch { FynxRemoteSocialClient.follow(context, post.authorUsername, following).onSuccess { now -> posts = posts.map { if (it.authorUsername.equals(post.authorUsername, true)) it.copy(followedByCurrentUser = now) else it } }.onFailure { error = it.message } } },
                onDelete = { scope.launch { FynxRemoteSocialClient.deletePost(context, post.id).onSuccess { posts = posts.filterNot { it.id == post.id } }.onFailure { error = it.message } } }
            )
        }
    }

    if (composerOpen) {
        AlertDialog(
            onDismissRequest = { if (!busy) { composerOpen = false; selectedMedia = null } },
            title = { Text("Create a post") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(composerText, { composerText = it.take(4000) }, Modifier.fillMaxWidth(), minLines = 3, maxLines = 7, placeholder = { Text("Share something with your FYNX circle…") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { picker.launch("image/*") }, Modifier.weight(1f)) { Text("Photo") }
                        OutlinedButton(onClick = { picker.launch("video/*") }, Modifier.weight(1f)) { Text("Video") }
                    }
                    if (selectedMedia != null) Text("Media selected", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(visibility == FynxPostVisibility.PUBLIC, { visibility = FynxPostVisibility.PUBLIC }, label = { Text("Public") })
                        FilterChip(visibility == FynxPostVisibility.FRIENDS_ONLY, { visibility = FynxPostVisibility.FRIENDS_ONLY }, label = { Text("Friends") })
                    }
                }
            },
            confirmButton = {
                Button(enabled = !busy, onClick = {
                    scope.launch {
                        busy = true
                        FynxRemoteSocialClient.createPost(context, composerText, visibility, selectedMedia).onSuccess { composerOpen = false; composerText = ""; selectedMedia = null; reload() }.onFailure { error = it.message ?: "Post failed." }
                        busy = false
                    }
                }) { Text(if (busy) "Publishing…" else "Post") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { composerOpen = false; selectedMedia = null }) { Text("Cancel") } }
        )
    }
    commentsPost?.let { post -> CommentsDialog(post) { commentsPost = null } }
    likesPost?.let { post -> LikesDialog(post) { likesPost = null } }
}

@Composable
private fun RemotePostCard(post: FynxRemoteSocialClient.RemotePost, currentUsername: String, onLike: (String) -> Unit, onComment: () -> Unit, onLikes: () -> Unit, onFollow: (Boolean) -> Unit, onDelete: () -> Unit) {
    val mine = post.authorUsername.equals(currentUsername.removePrefix("@"), true)
    Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) {
        Column {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                FynxAvatar(post.authorUsername, Modifier.size(46.dp).clip(CircleShape))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(post.authorDisplayName.ifBlank { post.authorUsername }, style = MaterialTheme.typography.titleSmall)
                    Text("@${post.authorUsername.removePrefix("@")} • ${relative(post.timestamp)}", style = MaterialTheme.typography.labelSmall)
                }
                if (mine) IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Delete post") }
                else TextButton(onClick = { onFollow(post.followedByCurrentUser) }) { Text(if (post.followedByCurrentUser) "Following" else "Follow") }
            }
            if (post.text.isNotBlank()) Text(post.text, Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            post.mediaUrl?.let { RemoteSocialMedia(it, post.mediaType) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onLike(post.id) }) { Icon(if (post.likedByCurrentUser) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null); Spacer(Modifier.width(4.dp)); Text(post.likeCount.toString()) }
                TextButton(onClick = onLikes) { Text("Likes") }
                TextButton(onClick = onComment) { Icon(Icons.Default.ChatBubbleOutline, null); Spacer(Modifier.width(4.dp)); Text(post.commentCount.toString()) }
            }
        }
    }
}

@Composable
private fun RemoteSocialMedia(path: String, type: String?) {
    val context = LocalContext.current
    var file by remember(path) { mutableStateOf<File?>(null) }
    LaunchedEffect(path) { file = withContext(Dispatchers.IO) { download(context, path, type) } }
    if (file == null) Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    else if (type == "video") AndroidView(factory = { ctx -> VideoView(ctx).apply { layoutParams = ViewGroup.LayoutParams(-1, 640); setMediaController(MediaController(ctx)); setVideoURI(Uri.fromFile(file)); setOnPreparedListener { it.isLooping = true; start() } } }, modifier = Modifier.fillMaxWidth().height(320.dp))
    else {
        var bitmap by remember(file) { mutableStateOf<android.graphics.Bitmap?>(null) }
        LaunchedEffect(file) { bitmap = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(file!!.absolutePath) }.getOrNull() } }
        bitmap?.let { Image(it.asImageBitmap(), "Post media", Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 520.dp), contentScale = ContentScale.Crop) }
    }
}

private fun download(context: Context, path: String, type: String?): File? = runCatching {
    val connection = (URL(FynxBackendClient.baseUrl(context) + path).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10000
        readTimeout = 20000
        setRequestProperty("Authorization", "Bearer ${FynxBackendClient.accessToken(context) ?: ""}")
    }
    try {
        if (connection.responseCode !in 200..299) return null
        val file = File.createTempFile("fynx_social_", if (type == "video") ".mp4" else ".jpg", context.cacheDir)
        connection.inputStream.use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }
        file
    } finally { connection.disconnect() }
}.getOrNull()

@Composable
private fun CommentsDialog(post: FynxRemoteSocialClient.RemotePost, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember(post.id) { mutableStateOf<List<FynxRemoteSocialClient.RemoteComment>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(post.id) { FynxRemoteSocialClient.comments(context, post.id).onSuccess { list = it } }
    AlertDialog(onDismissRequest = onClose, title = { Text("Comments") }, text = { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        list.forEach { comment -> Column { Text(comment.authorDisplayName.ifBlank { comment.authorUsername }); Text(comment.text); Text(relative(comment.timestamp), style = MaterialTheme.typography.labelSmall) } }
        if (list.isEmpty()) Text("No comments yet.")
        OutlinedTextField(text, { text = it.take(1000) }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Write a comment…") })
    } }, confirmButton = { TextButton(onClick = { if (text.isNotBlank()) scope.launch { FynxRemoteSocialClient.addComment(context, post.id, text).onSuccess { list = list + it; text = "" } } }) { Text("Comment") } }, dismissButton = { TextButton(onClick = onClose) { Text("Close") } })
}

@Composable
private fun LikesDialog(post: FynxRemoteSocialClient.RemotePost, onClose: () -> Unit) {
    val context = LocalContext.current
    var list by remember(post.id) { mutableStateOf<List<FynxRemoteSocialClient.RemoteUser>>(emptyList()) }
    LaunchedEffect(post.id) { FynxRemoteSocialClient.likes(context, post.id).onSuccess { list = it } }
    AlertDialog(onDismissRequest = onClose, title = { Text("People who liked this") }, text = { LazyColumn { items(list) { user -> Row(Modifier.fillMaxWidth().padding(7.dp), verticalAlignment = Alignment.CenterVertically) { FynxAvatar(user.username, Modifier.size(38.dp)); Spacer(Modifier.width(10.dp)); Column { Text(user.displayName.ifBlank { user.username }); Text("@${user.username.removePrefix("@")}", style = MaterialTheme.typography.labelSmall) } } } } }, confirmButton = { TextButton(onClick = onClose) { Text("Close") } })
}

private fun relative(timestamp: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes((System.currentTimeMillis() - timestamp).coerceAtLeast(0L))
    return when { minutes < 1 -> "now"; minutes < 60 -> "${minutes}m"; minutes < 1440 -> "${minutes / 60}h"; else -> "${minutes / 1440}d" }
}
