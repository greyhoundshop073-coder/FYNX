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
import androidx.compose.ui.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    onCreatePost: () -> Unit = {},
    onOpenAuthorProfile: (String) -> Unit = {}
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
    var authorPhotos by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var interactionStates by remember { mutableStateOf<Map<String, FynxRemoteSocialClient.SocialInteractionState>>(emptyMap()) }
    var interactionBusy by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun resolveAuthorPhotos(items: List<FynxRemoteSocialClient.RemotePost>) {
        val names = items.map { it.authorUsername.removePrefix("@").trim() }.filter { it.isNotBlank() }.distinct()
        val missing = names.filterNot { authorPhotos.containsKey(it.lowercase()) }
        if (missing.isEmpty()) return
        scope.launch {
            val resolved = mutableMapOf<String, String?>()
            missing.forEach { username ->
                FynxSocialClient.searchUsers(context, username).getOrNull()?.firstOrNull { it.username.removePrefix("@").equals(username, true) }?.let { resolved[username.lowercase()] = it.profilePhotoMediaId }
                    ?: run { resolved[username.lowercase()] = null }
            }
            authorPhotos = authorPhotos + resolved
        }
    }

    fun hydrateInteractionStates(items: List<FynxRemoteSocialClient.RemotePost>) {
        val ids = items.map { it.id }.filter { it.isNotBlank() && !interactionStates.containsKey(it) }.distinct()
        if (ids.isEmpty()) return
        scope.launch {
            val resolved = ids.map { id -> async(Dispatchers.IO) { id to FynxRemoteSocialClient.interactionState(context, id).getOrNull() } }
                .awaitAll().mapNotNull { (id, state) -> state?.let { id to it } }.toMap()
            if (resolved.isNotEmpty()) interactionStates = interactionStates + resolved
        }
    }

    fun reload(forceRefresh: Boolean = false) {
        val now = System.currentTimeMillis()
        if (feedRequestInFlight) return
        if (forceRefresh && now - lastFeedRequestAt < FEED_REFRESH_DEBOUNCE_MS) return
        feedRequestInFlight = true
        lastFeedRequestAt = now
        scope.launch {
            loading = true
            FynxRemoteSocialClient.feedPage(context, limit = 20, offset = 0, useCache = !forceRefresh)
                .onSuccess { page ->
                    posts = page.posts
                    hasMore = page.hasMore
                    error = null
                    interactionStates = emptyMap()
                    resolveAuthorPhotos(page.posts)
                    hydrateInteractionStates(page.posts)
                }
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
                    val additions = page.posts.filterNot { it.id in existing }
                    posts = posts + additions
                    hasMore = page.hasMore
                    error = null
                    resolveAuthorPhotos(additions)
                    hydrateInteractionStates(additions)
                }
                .onFailure { error = it.message ?: "Unable to load more posts." }
            loadingMore = false
            feedRequestInFlight = false
        }
    }

    fun runInteraction(id: String, action: suspend () -> Result<Pair<Boolean, Int>>, update: (FynxRemoteSocialClient.SocialInteractionState, Boolean, Int) -> FynxRemoteSocialClient.SocialInteractionState) {
        if (id in interactionBusy) return
        interactionBusy = interactionBusy + id
        scope.launch {
            action().onSuccess { result ->
                val current = interactionStates[id] ?: FynxRemoteSocialClient.SocialInteractionState(false, false, 0, 0)
                interactionStates = interactionStates + (id to update(current, result.first, result.second))
            }.onFailure { error = it.message ?: "Unable to update this post." }
            interactionBusy = interactionBusy - id
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
            val photoId = authorPhotos[post.authorUsername.removePrefix("@").trim().lowercase()]
            val state = interactionStates[post.id] ?: FynxRemoteSocialClient.SocialInteractionState(false, false, 0, 0)
            RemotePostCard(post = post, currentUsername = currentUsername, profilePhotoMediaId = photoId, interactionState = state, interactionBusy = post.id in interactionBusy,
                onOpenProfile = { onOpenAuthorProfile(post.authorUsername.removePrefix("@").trim()) },
                onLike = { id -> scope.launch { FynxRemoteSocialClient.like(context, id).onSuccess { result -> val (liked, count) = result; posts = posts.map { if (it.id == id) it.copy(likedByCurrentUser = liked, likeCount = count) else it } }.onFailure { error = it.message } } },
                onComment = { commentsPost = post },
                onFollow = { following -> scope.launch { FynxRemoteSocialClient.follow(context, post.authorUsername, following).onSuccess { now -> posts = posts.map { if (it.authorUsername.equals(post.authorUsername, true)) it.copy(followedByCurrentUser = now) else it } }.onFailure { error = it.message } } },
                onDelete = { scope.launch { FynxRemoteSocialClient.deletePost(context, post.id).onSuccess { posts = posts.filterNot { it.id == post.id }; interactionStates = interactionStates - post.id }.onFailure { error = it.message } } },
                onSave = { id, saved -> runInteraction(id, { FynxRemoteSocialClient.save(context, id, saved) }) { current, value, count -> current.copy(saved = value, savedCount = count) } },
                onRepost = { id, reposted -> runInteraction(id, { FynxRemoteSocialClient.repost(context, id, reposted) }) { current, value, count -> current.copy(reposted = value, repostCount = count) } },
                onShare = { scope.launch { FynxDiscoveryClient.recordEngagement(context, "SHARE", post.id) }; sharePost(context, post) },
                onOpenMarketplace = onOpenMarketplace)
        }
        if (!loading && hasMore) item(key = "feed_load_more") { OutlinedButton(onClick = { loadMore() }, enabled = !loadingMore && !feedRequestInFlight, modifier = Modifier.fillMaxWidth()) { Text(if (loadingMore) "Loading more posts…" else "Load more posts") } }
    }
    commentsPost?.let { post: FynxRemoteSocialClient.RemotePost ->
        FynxHomeCommentsPanel(
            post = post,
            onClose = { commentsPost = null },
            onCommentCountChanged = { newCount -> posts = posts.map { if (it.id == post.id) it.copy(commentCount = newCount) else it } }
        )
    }
}

@Composable
private fun RemotePostCard(post: FynxRemoteSocialClient.RemotePost, currentUsername: String, profilePhotoMediaId: String?, interactionState: FynxRemoteSocialClient.SocialInteractionState, interactionBusy: Boolean, onOpenProfile: () -> Unit, onLike: (String) -> Unit, onComment: () -> Unit, onFollow: (Boolean) -> Unit, onDelete: () -> Unit, onSave: (String, Boolean) -> Unit, onRepost: (String, Boolean) -> Unit, onShare: () -> Unit, onOpenMarketplace: () -> Unit) {
    val mine = post.authorUsername.equals(currentUsername.removePrefix("@"), true)
    val marketplaceAd = post.text.startsWith(MARKETPLACE_AD_MARKER)
    val displayText = if (marketplaceAd) post.text.removePrefix(MARKETPLACE_AD_MARKER).trim() else post.text
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenProfile, modifier = Modifier.size(50.dp)) { FynxRemoteProfileAvatar(profilePhotoMediaId, post.authorDisplayName.ifBlank { post.authorUsername }, Modifier.size(46.dp).clip(CircleShape)) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) { Text(post.authorDisplayName.ifBlank { post.authorUsername }, style = MaterialTheme.typography.titleSmall); Text("${post.authorUsername.removePrefix("@")} • ${relative(post.timestamp)}", style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary) }
            if (mine) IconButton(onClick = onDelete) { Icon(Icons.Default.MoreHoriz, "Post options") } else TextButton(onClick = { onFollow(post.followedByCurrentUser) }) { Text(if (post.followedByCurrentUser) "Following" else "Follow") }
        }
        if (marketplaceAd) Text("MARKETPLACE", Modifier.padding(horizontal = 12.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (displayText.isNotBlank()) Text(displayText, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.bodyLarge)
        post.mediaUrl?.let { RemoteSocialMedia(it, post.mediaType) }
        if (marketplaceAd) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End) { OutlinedButton(onClick = onOpenMarketplace) { Icon(Icons.Default.ShoppingBag, null); Spacer(Modifier.width(5.dp)); Text("View in Marketplace") } }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onLike(post.id) }, enabled = !interactionBusy, modifier = Modifier.size(50.dp)) { Icon(if (post.likedByCurrentUser) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Like", tint = if (post.likedByCurrentUser) MaterialTheme.colorScheme.error else FynxDesign.TextPrimary, modifier = Modifier.size(30.dp)) }
            Text("${post.likeCount}", style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = onComment, enabled = !interactionBusy, modifier = Modifier.size(50.dp)) { Icon(Icons.Default.ChatBubbleOutline, "Comment", modifier = Modifier.size(30.dp)) }
            Text("${post.commentCount}", style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = { onSave(post.id, !interactionState.saved) }, enabled = !interactionBusy, modifier = Modifier.size(50.dp)) { Icon(if (interactionState.saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "${if (interactionState.saved) "Unsave" else "Save"} post", tint = if (interactionState.saved) MaterialTheme.colorScheme.primary else FynxDesign.TextPrimary, modifier = Modifier.size(30.dp)) }
            Text("${interactionState.savedCount}", style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = { onRepost(post.id, !interactionState.reposted) }, enabled = !interactionBusy, modifier = Modifier.size(50.dp)) { Icon(Icons.Default.Repeat, "${if (interactionState.reposted) "Undo repost" else "Repost"}", tint = if (interactionState.reposted) MaterialTheme.colorScheme.primary else FynxDesign.TextPrimary, modifier = Modifier.size(30.dp)) }
            Text("${interactionState.repostCount}", style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = onShare, enabled = !interactionBusy, modifier = Modifier.size(50.dp)) { Icon(Icons.Default.Share, "Share", modifier = Modifier.size(30.dp)) }
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
    LaunchedEffect(file, type) { if (file != null && type == "video") videoAspectRatio = withContext(Dispatchers.IO) { runCatching { MediaMetadataRetriever().run { setDataSource(file!!.absolutePath); val width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 16f; val height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 9f; val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0; release(); if (rotation == 90 || rotation == 270) height / width else width / height }.coerceIn(0.56f, 1.91f) }.getOrDefault(16f / 9f) } }
    if (file == null) Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    else if (type == "audio") AudioPostPlayer(file!!)
    else if (type == "video") AndroidView(factory = { ctx -> VideoView(ctx).apply { layoutParams = ViewGroup.LayoutParams(-1, -1); setMediaController(MediaController(ctx)); setVideoURI(Uri.fromFile(file)); setOnPreparedListener { it.isLooping = true; start() } } }, modifier = Modifier.fillMaxWidth().aspectRatio(videoAspectRatio))
    else { var bitmap by remember(file) { mutableStateOf<android.graphics.Bitmap?>(null) }; LaunchedEffect(file) { bitmap = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(file!!.absolutePath) }.getOrNull() } }; bitmap?.let { Image(it.asImageBitmap(), "Post media", Modifier.fillMaxWidth().aspectRatio((it.width.toFloat() / it.height.toFloat()).coerceIn(0.62f, 1.9f)), contentScale = ContentScale.Crop) } }
}

private fun relative(timestamp: Long): String { val minutes = TimeUnit.MILLISECONDS.toMinutes((System.currentTimeMillis() - timestamp).coerceAtLeast(0L)); return when { minutes < 1 -> "now"; minutes < 60 -> "${minutes}m"; minutes < 1440 -> "${minutes / 60}h"; else -> "${minutes / 1440}d" } }

@Composable
private fun AudioPostPlayer(file: File) {
    val player = remember(file) { MediaPlayer().apply { setDataSource(file.absolutePath); prepare() } }
    var playing by remember(file) { mutableStateOf(false) }
    DisposableEffect(player) { onDispose { player.release() } }
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Button(onClick = { if (player.isPlaying) { player.pause(); playing = false } else { player.start(); playing = true } }) { Text(if (playing) "Pause" else "Play voice") }; Spacer(Modifier.width(10.dp)); Text("${(player.duration / 1000).coerceAtLeast(0)}s") }
}
