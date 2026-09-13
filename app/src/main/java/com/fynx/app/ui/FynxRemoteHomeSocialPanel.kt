package com.fynx.app.ui

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import java.util.concurrent.TimeUnit

private const val MARKETPLACE_AD_MARKER = "[FYNX_MARKETPLACE_AD]"
private const val FEED_REFRESH_DEBOUNCE_MS = 1000L

@Composable
fun FynxRemoteHomeSocialPanel(
    modifier: Modifier = Modifier,
    currentUsername: String,
    onOpenFindPeople: () -> Unit,
    onOpenMarketplace: () -> Unit = {},
    onCreatePost: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var posts by remember { mutableStateOf<List<FynxRemoteSocialClient.RemotePost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var feedRequestInFlight by remember { mutableStateOf(false) }
    var lastFeedRequestAt by remember { mutableLongStateOf(0L) }
    var commentsPost by remember { mutableStateOf<FynxRemoteSocialClient.RemotePost?>(null) }

    fun reload(forceRefresh: Boolean = false) {
        val now = System.currentTimeMillis()
        if (feedRequestInFlight) return
        if (forceRefresh && now - lastFeedRequestAt < FEED_REFRESH_DEBOUNCE_MS) return
        feedRequestInFlight = true
        lastFeedRequestAt = now
        scope.launch {
            loading = true
            FynxRemoteSocialClient.feedPage(context, limit = 20, offset = 0, useCache = !forceRefresh)
                .onSuccess { page -> posts = page.posts; hasMore = page.hasMore; error = null }
                .onFailure { error = when { it.message?.contains("HTTP 404", true) == true -> "Your FYNX feed service is temporarily unavailable." else -> it.message ?: "Unable to load your feed." } }
            loading = false
            feedRequestInFlight = false
        }
    }

    fun loadMore() {
        if (loading || loadingMore || !hasMore || feedRequestInFlight) return
        feedRequestInFlight = true
        scope.launch {
            loadingMore = true
            FynxRemoteSocialClient.feedPage(context, limit = 20, offset = posts.size, useCache = false)
                .onSuccess { page ->
                    val existing = posts.map { it.id }.toSet()
                    posts = posts + page.posts.filterNot { it.id in existing }
                    hasMore = page.hasMore
                    error = null
                }
                .onFailure { error = it.message ?: "Unable to load more posts." }
            loadingMore = false
            feedRequestInFlight = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    LazyColumn(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "feed_header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("Your feed", style = MaterialTheme.typography.titleMedium); Text("Real posts from your FYNX network", style = MaterialTheme.typography.bodySmall) }
                Row {
                    IconButton(onClick = { reload(true) }, enabled = !feedRequestInFlight) { Icon(Icons.Default.Refresh, "Refresh feed") }
                    IconButton(onClick = onCreatePost) { Icon(Icons.Default.Add, "Create post") }
                }
            }
        }
        if (loading) item(key = "feed_loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        error?.let { message -> item(key = "feed_error") { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer); TextButton(onClick = { reload(true) }, enabled = !feedRequestInFlight) { Text("Retry") } } } } }
        if (!loading && posts.isEmpty() && error == null) item(key = "feed_empty") { Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Your feed is ready", style = MaterialTheme.typography.titleMedium); Text("There are no visible posts yet. Create a post or find real people to build your FYNX circle."); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onCreatePost) { Text("Create Post") }; OutlinedButton(onClick = onOpenFindPeople) { Text("Find People") } } } } }
        items(items = posts, key = { it.id }) { post ->
            RemotePostCard(post = post, currentUsername = currentUsername,
                onLike = { id -> scope.launch { FynxRemoteSocialClient.like(context, id).onSuccess { result -> val (liked, count) = result; posts = posts.map { if (it.id == id) it.copy(likedByCurrentUser = liked, likeCount = count) else it } }.onFailure { error = it.message } } },
                onComment = { commentsPost = post },
                onFollow = { following -> scope.launch { FynxRemoteSocialClient.follow(context, post.authorUsername, following).onSuccess { now -> posts = posts.map { if (it.authorUsername.equals(post.authorUsername, true)) it.copy(followedByCurrentUser = now) else it } }.onFailure { error = it.message } } },
                onDelete = { scope.launch { FynxRemoteSocialClient.deletePost(context, post.id).onSuccess { posts = posts.filterNot { it.id == post.id } }.onFailure { error = it.message } } },
                onShare = { scope.launch { FynxDiscoveryClient.recordEngagement(context, "SHARE", post.id) }; sharePost(context, post) },
                onOpenMarketplace = onOpenMarketplace)
        }
        if (!loading && hasMore) item(key = "feed_load_more") { OutlinedButton(onClick = { loadMore() }, enabled = !loadingMore && !feedRequestInFlight, modifier = Modifier.fillMaxWidth()) { Text(if (loadingMore) "Loading more posts…" else "Load more posts") } }
    }
    commentsPost?.let { post: FynxRemoteSocialClient.RemotePost -> CommentsDialog(post) { commentsPost = null } }
}

@Composable
private fun RemotePostCard(post: FynxRemoteSocialClient.RemotePost, currentUsername: String, onLike: (String) -> Unit, onComment: () -> Unit, onFollow: (Boolean) -> Unit, onDelete: () -> Unit, onShare: () -> Unit, onOpenMarketplace: () -> Unit) {
    val mine = post.authorUsername.equals(currentUsername.removePrefix("@"), true)
    val marketplaceAd = post.text.startsWith(MARKETPLACE_AD_MARKER)
    val displayText = if (marketplaceAd) post.text.removePrefix(MARKETPLACE_AD_MARKER).trim() else post.text
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            FynxAvatar(post.authorUsername, Modifier.size(46.dp).clip(CircleShape)); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text(post.authorDisplayName.ifBlank { post.authorUsername }, style = MaterialTheme.typography.titleSmall); Text("${post.authorUsername.removePrefix("@")} • ${relative(post.timestamp)}", style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary) }
            if (mine) IconButton(onClick = onDelete) { Icon(Icons.Default.MoreHoriz, "Post options") } else TextButton(onClick = { onFollow(post.followedByCurrentUser) }) { Text(if (post.followedByCurrentUser) "Following" else "Follow") }
        }
        if (marketplaceAd) Text("MARKETPLACE", Modifier.padding(horizontal = 12.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (displayText.isNotBlank()) Text(displayText, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.bodyLarge)
        post.mediaUrl?.let { RemoteSocialMedia(it, post.mediaType) }
        if (marketplaceAd) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End) { OutlinedButton(onClick = onOpenMarketplace) { Icon(Icons.Default.ShoppingBag, null); Spacer(Modifier.width(5.dp)); Text("View in Marketplace") } }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onLike(post.id) }, modifier = Modifier.size(50.dp)) { Icon(if (post.likedByCurrentUser) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Like", tint = if (post.likedByCurrentUser) MaterialTheme.colorScheme.error else FynxDesign.TextPrimary, modifier = Modifier.size(30.dp)) }
            Text("${post.likeCount}", style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = onComment, modifier = Modifier.size(50.dp)) { Icon(Icons.Default.ChatBubbleOutline, "Comment", modifier = Modifier.size(30.dp)) }
            Text("${post.commentCount}", style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = onShare, modifier = Modifier.size(50.dp)) { Icon(Icons.Default.Share, "Share", modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
    }
}

private fun sharePost(context: Context, post: FynxRemoteSocialClient.RemotePost) { val text = if (post.text.startsWith(MARKETPLACE_AD_MARKER)) "${post.text.removePrefix(MARKETPLACE_AD_MARKER).trim()}\n\nSee this product on FYNX Marketplace." else "${post.authorDisplayName.ifBlank { post.authorUsername }} on FYNX:\n${post.text}".trim(); val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); putExtra(Intent.EXTRA_TITLE, "Share from FYNX") }; context.startActivity(Intent.createChooser(intent, "Share with…")) }

@Composable
private fun RemoteSocialMedia(path: String, type: String?) {
    val context = LocalContext.current
    var file by remember(path) { mutableStateOf<File?>(null) }
    var videoAspectRatio by remember(path) { mutableFloatStateOf(16f / 9f) }
    LaunchedEffect(path) { file = withContext(Dispatchers.IO) { FynxMediaCache.getOrDownload(context, path, type) } }
    LaunchedEffect(file, type) { if (file != null && type == "video") videoAspectRatio = withContext(Dispatchers.IO) { runCatching { MediaMetadataRetriever().run { setDataSource(file!!.absolutePath); val width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 16f; val height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 9f; val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0; release(); if (rotation == 90 || rotation == 270) height / width else width / height }.coerceIn(0.56f, 1.91f) }.getOrDefault(16f / 9f) }
    }
    if (file == null) Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    else if (type == "audio") AudioPostPlayer(file!!)
    else if (type == "video") AndroidView(factory = { ctx -> VideoView(ctx).apply { layoutParams = ViewGroup.LayoutParams(-1, -1); setMediaController(MediaController(ctx)); setVideoURI(Uri.fromFile(file)); setOnPreparedListener { it.isLooping = true; start() } } }, modifier = Modifier.fillMaxWidth().aspectRatio(videoAspectRatio))
    else { var bitmap by remember(file) { mutableStateOf<android.graphics.Bitmap?>(null) }; LaunchedEffect(file) { bitmap = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(file!!.absolutePath) }.getOrNull() } }; bitmap?.let { Image(it.asImageBitmap(), "Post media", Modifier.fillMaxWidth().aspectRatio((it.width.toFloat() / it.height.toFloat()).coerceIn(0.62f, 1.9f)), contentScale = ContentScale.Crop) } }
}

@Composable
private fun CommentsDialog(post: FynxRemoteSocialClient.RemotePost, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember(post.id) { mutableStateOf<List<FynxRemoteSocialClient.RemoteComment>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(post.id) { FynxRemoteSocialClient.comments(context, post.id).onSuccess { list = it } }
    AlertDialog(onDismissRequest = onClose, title = { Text("Comments") }, text = { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) { list.forEach { comment -> Column { Text(comment.authorDisplayName.ifBlank { comment.authorUsername }); Text(comment.text); Text(relative(comment.timestamp), style = MaterialTheme.typography.labelSmall) } }; if (list.isEmpty()) Text("No comments yet."); OutlinedTextField(text, { text = it.take(1000) }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Write a comment…") }) } }, confirmButton = { TextButton(onClick = { if (text.isNotBlank()) scope.launch { FynxRemoteSocialClient.addComment(context, post.id, text).onSuccess { list = list + it; text = "" } } }) { Text("Comment") } }, dismissButton = { TextButton(onClick = onClose) { Text("Close") } })
}

private fun relative(timestamp: Long): String { val minutes = TimeUnit.MILLISECONDS.toMinutes((System.currentTimeMillis() - timestamp).coerceAtLeast(0L)); return when { minutes < 1 -> "now"; minutes < 60 -> "${minutes}m"; minutes < 1440 -> "${minutes / 60}h"; else -> "${minutes / 1440}d" } }

@Composable
private fun AudioPostPlayer(file: File) {
    val player = remember(file) { MediaPlayer().apply { setDataSource(file.absolutePath); prepare() } }
    var playing by remember(file) { mutableStateOf(false) }
    DisposableEffect(player) { onDispose { player.release() } }
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Button(onClick = { if (player.isPlaying) { player.pause(); playing = false } else { player.start(); playing = true } }) { Text(if (playing) "Pause" else "Play voice") }; Spacer(Modifier.width(10.dp)); Text("${(player.duration / 1000).coerceAtLeast(0)}s") }
}
