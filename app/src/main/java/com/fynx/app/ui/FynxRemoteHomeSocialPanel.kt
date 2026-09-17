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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.semantics.Role
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
fun FynxRemoteHomeSocialPanel(modifier: Modifier = Modifier, currentUsername: String, onOpenFindPeople: () -> Unit, onOpenMarketplace: () -> Unit = {}, onCreatePost: () -> Unit = {}, onOpenAuthorProfile: (String) -> Unit = {}, header: (@Composable () -> Unit)? = null) {
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
    var reactionStates by remember { mutableStateOf<Map<String, FynxHomePostReactionsClient.ReactionState>>(emptyMap()) }
    var reactionPickerPostId by remember { mutableStateOf<String?>(null) }
    var interactionBusy by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deletePost by remember { mutableStateOf<FynxRemoteSocialClient.RemotePost?>(null) }

    fun resolveAuthorPhotos(items: List<FynxRemoteSocialClient.RemotePost>) {
        val names = items.map { it.authorUsername.removePrefix("@").trim() }.filter { it.isNotBlank() }.distinct()
        val missing = names.filterNot { authorPhotos.containsKey(it.lowercase()) }
        if (missing.isEmpty()) return
        scope.launch {
            val resolved = missing.map { username -> async(Dispatchers.IO) { username.lowercase() to (FynxSocialClient.searchUsers(context, username).getOrNull()?.firstOrNull { it.username.removePrefix("@").equals(username, true) }?.profilePhotoMediaId) } }.awaitAll().toMap()
            authorPhotos = authorPhotos + resolved
        }
    }
    fun hydrateInteractionStates(items: List<FynxRemoteSocialClient.RemotePost>) {
        val ids = items.map { it.id }.filter { it.isNotBlank() && !interactionStates.containsKey(it) }.distinct(); if (ids.isEmpty()) return
        scope.launch { val resolved = ids.map { id -> async(Dispatchers.IO) { id to FynxRemoteSocialClient.interactionState(context, id).getOrNull() } }.awaitAll().mapNotNull { (id, state) -> state?.let { id to it } }.toMap(); if (resolved.isNotEmpty()) interactionStates = interactionStates + resolved }
    }
    fun hydrateReactionStates(items: List<FynxRemoteSocialClient.RemotePost>) {
        val ids = items.map { it.id }.filter { it.isNotBlank() && !reactionStates.containsKey(it) }.distinct(); if (ids.isEmpty()) return
        scope.launch { val resolved = ids.map { id -> async(Dispatchers.IO) { id to FynxHomePostReactionsClient.state(context, id).getOrNull() } }.awaitAll().mapNotNull { (id, state) -> state?.let { id to it } }.toMap(); if (resolved.isNotEmpty()) reactionStates = reactionStates + resolved }
    }
    fun reload(forceRefresh: Boolean = false) {
        val now = System.currentTimeMillis(); if (feedRequestInFlight) return; if (forceRefresh && now - lastFeedRequestAt < FEED_REFRESH_DEBOUNCE_MS) return
        feedRequestInFlight = true; lastFeedRequestAt = now
        scope.launch { loading = true; FynxRemoteSocialClient.feedPage(context, limit = 20, offset = 0, useCache = !forceRefresh).onSuccess { page -> posts = page.posts; hasMore = page.hasMore; error = null; interactionStates = emptyMap(); reactionStates = emptyMap(); reactionPickerPostId = null; resolveAuthorPhotos(page.posts); hydrateInteractionStates(page.posts); hydrateReactionStates(page.posts) }.onFailure { error = if (it.message?.contains("HTTP 404", true) == true) "Your FYNX feed service is temporarily unavailable." else it.message ?: "Unable to load your feed." }; loading = false; feedRequestInFlight = false }
    }
    fun loadMore() {
        if (loading || loadingMore || !hasMore || feedRequestInFlight) return
        feedRequestInFlight = true
        scope.launch { loadingMore = true; FynxRemoteSocialClient.feedPage(context, limit = 20, offset = posts.size, useCache = false).onSuccess { page -> val existing = posts.map { it.id }.toSet(); val additions = page.posts.filterNot { it.id in existing }; posts = posts + additions; hasMore = page.hasMore; error = null; resolveAuthorPhotos(additions); hydrateInteractionStates(additions); hydrateReactionStates(additions) }.onFailure { error = it.message ?: "Unable to load more posts." }; loadingMore = false; feedRequestInFlight = false }
    }
    fun runInteraction(id: String, desired: Boolean, isActive: (FynxRemoteSocialClient.SocialInteractionState) -> Boolean, count: (FynxRemoteSocialClient.SocialInteractionState) -> Int, action: suspend () -> Result<Pair<Boolean, Int>>, update: (FynxRemoteSocialClient.SocialInteractionState, Boolean, Int) -> FynxRemoteSocialClient.SocialInteractionState) {
        if (id in interactionBusy) return
        val previous = interactionStates[id] ?: FynxRemoteSocialClient.SocialInteractionState(false, false, 0, 0); val currentCount = count(previous)
        val optimisticCount = when { desired && !isActive(previous) -> currentCount + 1; !desired && isActive(previous) -> (currentCount - 1).coerceAtLeast(0); else -> currentCount }
        interactionStates = interactionStates + (id to update(previous, desired, optimisticCount)); interactionBusy = interactionBusy + id
        scope.launch { action().onSuccess { result -> interactionStates = interactionStates + (id to update(previous, result.first, result.second.coerceAtLeast(0))) }.onFailure { interactionStates = interactionStates + (id to previous); error = it.message ?: "Unable to update this post." }; interactionBusy = interactionBusy - id }
    }
    fun runLike(id: String) {
        if (id in interactionBusy) return; val previous = posts.firstOrNull { it.id == id } ?: return; val optimisticLiked = !previous.likedByCurrentUser; val optimisticCount = (previous.likeCount + if (optimisticLiked) 1 else -1).coerceAtLeast(0)
        posts = posts.map { if (it.id == id) it.copy(likedByCurrentUser = optimisticLiked, likeCount = optimisticCount) else it }; interactionBusy = interactionBusy + id
        scope.launch { FynxRemoteSocialClient.like(context, id).onSuccess { result -> val (liked, count) = result; posts = posts.map { if (it.id == id) it.copy(likedByCurrentUser = liked, likeCount = count.coerceAtLeast(0)) else it } }.onFailure { posts = posts.map { if (it.id == id) it.copy(likedByCurrentUser = previous.likedByCurrentUser, likeCount = previous.likeCount) else it }; error = it.message ?: "Unable to update this like." }; interactionBusy = interactionBusy - id }
    }
    fun runReaction(id: String, reaction: String) {
        if (id in interactionBusy) return; val previous = reactionStates[id] ?: FynxHomePostReactionsClient.ReactionState(); val same = previous.currentReaction == reaction; val optimisticCounts = previous.counts.toMutableMap(); previous.currentReaction?.let { current -> optimisticCounts[current] = ((optimisticCounts[current] ?: 0) - 1).coerceAtLeast(0) }; if (!same) optimisticCounts[reaction] = (optimisticCounts[reaction] ?: 0) + 1
        reactionStates = reactionStates + (id to FynxHomePostReactionsClient.ReactionState(optimisticCounts.filterValues { it > 0 }, if (same) null else reaction)); interactionBusy = interactionBusy + id; reactionPickerPostId = null
        scope.launch { val result = if (same) FynxHomePostReactionsClient.clear(context, id) else FynxHomePostReactionsClient.set(context, id, reaction); result.onSuccess { reactionStates = reactionStates + (id to it) }.onFailure { reactionStates = reactionStates + (id to previous); error = it.message ?: "Unable to update this reaction." }; interactionBusy = interactionBusy - id }
    }
    fun runFollow(username: String, following: Boolean) {
        val key = "follow:${username.removePrefix("@").trim().lowercase()}"; if (key in interactionBusy) return; interactionBusy = interactionBusy + key
        scope.launch { FynxRemoteSocialClient.follow(context, username, following).onSuccess { now -> posts = posts.map { if (it.authorUsername.equals(username, true)) it.copy(followedByCurrentUser = now) else it } }.onFailure { error = it.message ?: "Unable to update this follow." }; interactionBusy = interactionBusy - key }
    }
    fun runDelete(id: String) {
        if (id in interactionBusy) return; interactionBusy = interactionBusy + id
        scope.launch { FynxRemoteSocialClient.deletePost(context, id).onSuccess { posts = posts.filterNot { it.id == id }; interactionStates = interactionStates - id; reactionStates = reactionStates - id; deletePost = null }.onFailure { error = it.message ?: "Unable to delete this post." }; interactionBusy = interactionBusy - id }
    }
    fun runShare(post: FynxRemoteSocialClient.RemotePost) {
        if (post.id in interactionBusy) return; interactionBusy = interactionBusy + post.id
        scope.launch { sharePost(context, post).onSuccess { runCatching { FynxDiscoveryClient.recordEngagement(context, "SHARE", post.id) } }.onFailure { error = it.message ?: "No app is available to share this post." }; interactionBusy = interactionBusy - post.id }
    }
    LaunchedEffect(Unit) { reload() }

    LazyColumn(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        header?.let { content -> item(key = "home_ai_status") { content() } }
        item(key = "feed_header") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Your feed", style = MaterialTheme.typography.titleMedium); Text("Real posts from your FYNX network", style = MaterialTheme.typography.bodySmall) }
                IconButton(onClick = { reload(true) }, enabled = !feedRequestInFlight) { Icon(Icons.Default.Refresh, "Refresh feed") }
            }
        }
        if (loading) item(key = "feed_loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        error?.let { message -> item(key = "feed_error") { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer); TextButton(onClick = { reload(true) }, enabled = !feedRequestInFlight) { Text("Retry") } } } } }
        if (!loading && posts.isEmpty() && error == null) item(key = "feed_empty") { Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Your feed is ready", style = MaterialTheme.typography.titleMedium); Text("There are no visible posts yet. Create a post or find real people to build your FYNX circle."); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onCreatePost) { Text("Create Post") }; OutlinedButton(onClick = onOpenFindPeople) { Text("Find People") } } } } }
        items(items = posts, key = { it.id }) { post ->
            val photoId = authorPhotos[post.authorUsername.removePrefix("@").trim().lowercase()]; val state = interactionStates[post.id] ?: FynxRemoteSocialClient.SocialInteractionState(false, false, 0, 0); val reaction = reactionStates[post.id] ?: FynxHomePostReactionsClient.ReactionState(); val busy = post.id in interactionBusy || "follow:${post.authorUsername.removePrefix("@").trim().lowercase()}" in interactionBusy
            RemotePostCard(post, currentUsername, photoId, state, reaction, busy, onOpenProfile = { onOpenAuthorProfile(post.authorUsername.removePrefix("@").trim()) }, onLike = { runLike(it) }, onComment = { commentsPost = post }, onFollow = { runFollow(post.authorUsername, it) }, onDelete = { deletePost = post }, onSave = { id, saved -> runInteraction(id, saved, { it.saved }, { it.savedCount }, { FynxRemoteSocialClient.save(context, id, saved) }) { current, value, count -> current.copy(saved = value, savedCount = count) } }, onRepost = { id, reposted -> runInteraction(id, reposted, { it.reposted }, { it.repostCount }, { FynxRemoteSocialClient.repost(context, id, reposted) }) { current, value, count -> current.copy(reposted = value, repostCount = count) } }, onShare = { runShare(post) }, onOpenReactionPicker = { reactionPickerPostId = if (reactionPickerPostId == post.id) null else post.id }, onReact = { id, selected -> runReaction(id, selected) }, reactionPickerOpen = reactionPickerPostId == post.id, onOpenMarketplace = onOpenMarketplace)
        }
        if (!loading && hasMore) item(key = "feed_load_more") { OutlinedButton(onClick = { loadMore() }, enabled = !loadingMore && !feedRequestInFlight, modifier = Modifier.fillMaxWidth()) { Text(if (loadingMore) "Loading more posts…" else "Load more posts") }
        }
    }
    commentsPost?.let { post -> FynxHomeCommentsPanel(post = post, onClose = { commentsPost = null }, onCommentCountChanged = { newCount -> posts = posts.map { if (it.id == post.id) it.copy(commentCount = newCount) else it } }) }
    deletePost?.let { post -> AlertDialog(onDismissRequest = { if (post.id !in interactionBusy) deletePost = null }, title = { Text("Delete post?") }, text = { Text("This will permanently remove your post from FYNX. This action cannot be undone.") }, confirmButton = { TextButton(onClick = { runDelete(post.id) }, enabled = post.id !in interactionBusy) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deletePost = null }, enabled = post.id !in interactionBusy) { Text("Cancel") } }) }
}

@Composable
private fun RemotePostCard(post: FynxRemoteSocialClient.RemotePost, currentUsername: String, profilePhotoMediaId: String?, interactionState: FynxRemoteSocialClient.SocialInteractionState, reactionState: FynxHomePostReactionsClient.ReactionState, interactionBusy: Boolean, onOpenProfile: () -> Unit, onLike: (String) -> Unit, onComment: () -> Unit, onFollow: (Boolean) -> Unit, onDelete: () -> Unit, onSave: (String, Boolean) -> Unit, onRepost: (String, Boolean) -> Unit, onShare: () -> Unit, onOpenReactionPicker: () -> Unit, onReact: (String, String) -> Unit, reactionPickerOpen: Boolean, onOpenMarketplace: () -> Unit) {
    val context = LocalContext.current
    val marketplaceListingId = Regex("""(?m)^Listing ID:\s*(\d+)\s*$""").find(post.text)?.groupValues?.getOrNull(1)
    val mine = post.authorUsername.equals(currentUsername.removePrefix("@"), true); val marketplaceAd = post.text.startsWith(MARKETPLACE_AD_MARKER); val displayText = if (marketplaceAd) post.text.removePrefix(MARKETPLACE_AD_MARKER).trim() else post.text
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenProfile, modifier = Modifier.size(50.dp)) { FynxRemoteProfileAvatar(profilePhotoMediaId, post.authorDisplayName.ifBlank { post.authorUsername }, Modifier.size(46.dp).clip(CircleShape)) }
            Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(post.authorDisplayName.ifBlank { post.authorUsername }, style = MaterialTheme.typography.titleSmall, maxLines = 1); Text("${post.authorUsername.removePrefix("@")} • ${relative(post.timestamp)}", style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary, maxLines = 1) }
            if (mine) IconButton(onClick = onDelete, enabled = !interactionBusy) { Icon(Icons.Default.MoreHoriz, "Post options") } else TextButton(onClick = { onFollow(post.followedByCurrentUser) }, enabled = !interactionBusy) { Text(if (post.followedByCurrentUser) "Following" else "Follow") }
        }
        if (marketplaceAd) Text("MARKETPLACE", Modifier.padding(horizontal = 12.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (displayText.isNotBlank()) Text(displayText, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.bodyLarge)
        post.mediaUrl?.let { RemoteSocialMedia(it, post.mediaType) }
        if (marketplaceAd) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End) { OutlinedButton(onClick = { if (marketplaceListingId.isNullOrBlank()) onOpenMarketplace() else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FynxDeepLinkParser.marketplaceAppLink(marketplaceListingId)))) }) { Icon(Icons.Default.ShoppingBag, null); Spacer(Modifier.width(5.dp)); Text("View in Marketplace") } }
        if (reactionPickerOpen) Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), shape = MaterialTheme.shapes.large, tonalElevation = 2.dp) { Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { ReactionChoice("👍", "LIKE", reactionState.currentReaction == "LIKE", onReact = { onReact(post.id, it) }); ReactionChoice("❤️", "LOVE", reactionState.currentReaction == "LOVE", onReact = { onReact(post.id, it) }); ReactionChoice("😂", "LAUGH", reactionState.currentReaction == "LAUGH", onReact = { onReact(post.id, it) }); ReactionChoice("😮", "WOW", reactionState.currentReaction == "WOW", onReact = { onReact(post.id, it) }); ReactionChoice("😢", "SAD", reactionState.currentReaction == "SAD", onReact = { onReact(post.id, it) }) } }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            FeedActionButton(onClick = { onLike(post.id) }, onLongClick = onOpenReactionPicker, enabled = !interactionBusy, icon = if (post.likedByCurrentUser) Icons.Default.Favorite else Icons.Default.FavoriteBorder, label = "Like", longClickLabel = "Open post reactions", count = post.likeCount, active = post.likedByCurrentUser)
            FeedActionButton(onClick = onComment, enabled = !interactionBusy, icon = Icons.Default.ChatBubbleOutline, label = "Comment", count = post.commentCount)
            FeedActionButton(onClick = onShare, enabled = !interactionBusy, icon = Icons.Default.Share, label = "Share")
            FeedActionButton(onClick = { onSave(post.id, !interactionState.saved) }, enabled = !interactionBusy, icon = if (interactionState.saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, label = if (interactionState.saved) "Saved" else "Save", count = interactionState.savedCount, active = interactionState.saved)
            FeedActionButton(onClick = { onRepost(post.id, !interactionState.reposted) }, enabled = !interactionBusy, icon = Icons.Default.Repeat, label = if (interactionState.reposted) "Reposted" else "Repost", count = interactionState.repostCount, active = interactionState.reposted)
        }
        if (reactionState.total > 0) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) { Text(reactionSummary(reactionState), style = MaterialTheme.typography.labelMedium, color = FynxDesign.TextSecondary, modifier = Modifier.combinedClickable(role = Role.Button, onClickLabel = "Open post reactions", onClick = onOpenReactionPicker)) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ReactionChoice(emoji: String, reaction: String, active: Boolean, onReact: (String) -> Unit) { TextButton(onClick = { onReact(reaction) }, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = if (active) MaterialTheme.colorScheme.primary else FynxDesign.TextPrimary)) { Text(emoji, style = MaterialTheme.typography.titleLarge) } }
private fun reactionSummary(state: FynxHomePostReactionsClient.ReactionState): String { val order = listOf("LIKE" to "👍", "LOVE" to "❤️", "LAUGH" to "😂", "WOW" to "😮", "SAD" to "😢"); val visible = order.filter { (key, _) -> (state.counts[key] ?: 0) > 0 }.take(5); return "${visible.joinToString(" ") { it.second }}  ${state.total} reaction${if (state.total == 1) "" else "s"}" }

@Composable
private fun RowScope.FeedActionButton(onClick: () -> Unit, onLongClick: (() -> Unit)? = null, enabled: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, longClickLabel: String? = null, count: Int? = null, active: Boolean = false) {
    Box(Modifier.heightIn(min = 50.dp).weight(1f).combinedClickable(enabled = enabled, role = Role.Button, onClickLabel = label, onLongClickLabel = longClickLabel, onLongClick = onLongClick, onClick = onClick), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) { Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp), tint = if (active) MaterialTheme.colorScheme.primary else FynxDesign.TextPrimary); Spacer(Modifier.width(3.dp)); Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1); if (count != null) { Spacer(Modifier.width(2.dp)); Text("$count", style = MaterialTheme.typography.labelMedium, color = FynxDesign.TextSecondary, maxLines = 1) } }
    }
}

private fun sharePost(context: Context, post: FynxRemoteSocialClient.RemotePost): Result<Unit> = runCatching { val text = if (post.text.startsWith(MARKETPLACE_AD_MARKER)) "${post.text.removePrefix(MARKETPLACE_AD_MARKER).trim()}\n\nSee this product on FYNX Marketplace." else "${post.authorDisplayName.ifBlank { post.authorUsername }} on FYNX:\n${post.text}".trim(); val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); putExtra(Intent.EXTRA_TITLE, "Share from FYNX") }; context.startActivity(Intent.createChooser(intent, "Share with…")) }

@Composable
private fun RemoteSocialMedia(path: String, type: String?) {
    val context = LocalContext.current; var file by remember(path) { mutableStateOf<File?>(null) }; var videoAspectRatio by remember(path) { mutableFloatStateOf(16f / 9f) }; var videoView by remember(path) { mutableStateOf<VideoView?>(null) }
    LaunchedEffect(path) { file = withContext(Dispatchers.IO) { FynxMediaCache.getOrDownload(context, path, type) } }
    LaunchedEffect(file, type) { if (file != null && type == "video") videoAspectRatio = withContext(Dispatchers.IO) { runCatching { MediaMetadataRetriever().run { setDataSource(file!!.absolutePath); val width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 16f; val height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 9f; val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0; release(); if (rotation == 90 || rotation == 270) height / width else width / height }.coerceIn(0.56f, 1.91f) }.getOrDefault(16f / 9f) } }
    DisposableEffect(videoView) { onDispose { videoView?.stopPlayback() } }
    if (file == null) Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    else if (type == "audio") AudioPostPlayer(file!!)
    else if (type == "video") AndroidView(factory = { ctx -> VideoView(ctx).apply { videoView = this; layoutParams = ViewGroup.LayoutParams(-1, -1); setMediaController(MediaController(ctx)); setVideoURI(Uri.fromFile(file)); setOnPreparedListener { it.isLooping = true; start() } } }, modifier = Modifier.fillMaxWidth().aspectRatio(videoAspectRatio))
    else { var bitmap by remember(file) { mutableStateOf<android.graphics.Bitmap?>(null) }; LaunchedEffect(file) { bitmap = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(file!!.absolutePath) }.getOrNull() } }; bitmap?.let { Image(it.asImageBitmap(), "Post media", Modifier.fillMaxWidth().aspectRatio((it.width.toFloat() / it.height.toFloat()).coerceIn(0.62f, 1.9f)), contentScale = ContentScale.Fit) } }
}

private fun relative(timestamp: Long): String { val minutes = TimeUnit.MILLISECONDS.toMinutes((System.currentTimeMillis() - timestamp).coerceAtLeast(0L)); return when { minutes < 1 -> "now"; minutes < 60 -> "${minutes}m"; minutes < 1440 -> "${minutes / 60}h"; else -> "${minutes / 1440}d" } }

@Composable
private fun AudioPostPlayer(file: File) { val player = remember(file) { MediaPlayer().apply { setDataSource(file.absolutePath); prepare() } }; var playing by remember(file) { mutableStateOf(false) }; DisposableEffect(player) { onDispose { player.release() } }; Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Button(onClick = { if (player.isPlaying) { player.pause(); playing = false } else { player.start(); playing = true } }) { Text(if (playing) "Pause" else "Play voice") }; Spacer(Modifier.width(10.dp)); Text("${(player.duration / 1000).coerceAtLeast(0)}s") } }