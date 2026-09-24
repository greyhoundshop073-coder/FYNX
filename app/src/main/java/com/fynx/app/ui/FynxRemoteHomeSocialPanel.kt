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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import org.json.JSONObject

private const val MARKETPLACE_AD_MARKER = "[FYNX_MARKETPLACE_AD]"
private const val FEED_REFRESH_DEBOUNCE_MS = 1000L
private data class HomePeopleRecommendation(val username: String, val displayName: String, val verified: Boolean, val mutualFriends: Int, val reason: String, val photoId: String?)

@Composable
fun FynxRemoteHomeSocialPanel(modifier: Modifier = Modifier, currentUsername: String, onOpenFindPeople: () -> Unit, onOpenMarketplace: () -> Unit = {}, onCreatePost: () -> Unit = {}, onOpenAuthorProfile: (String) -> Unit = {}, header: (@Composable () -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val publishRefreshKey = FynxHomeLifecycleRefreshBus.currentVersion()
    var posts by remember { mutableStateOf<List<FynxRemoteSocialClient.RemotePost>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var feedRequestInFlight by remember { mutableStateOf(false) }
    var lastFeedRequestAt by remember { mutableLongStateOf(0L) }
    var commentsPost by remember { mutableStateOf<FynxRemoteSocialClient.RemotePost?>(null) }
    var authorPhotos by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var activeStatusOwners by remember { mutableStateOf<Set<String>>(emptySet()) }
    var peopleRecommendations by remember { mutableStateOf<List<HomePeopleRecommendation>>(emptyList()) }
    var authorStatusViewer by remember { mutableStateOf<List<FynxStatus>?>(null) }
    var interactionStates by remember { mutableStateOf<Map<String, FynxRemoteSocialClient.SocialInteractionState>>(emptyMap()) }
    var reactionStates by remember { mutableStateOf<Map<String, FynxHomePostReactionsClient.ReactionState>>(emptyMap()) }
    var reactionPickerPostId by remember { mutableStateOf<String?>(null) }
    var reactionUsersPostId by remember { mutableStateOf<String?>(null) }
    var interactionBusy by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deletePost by remember { mutableStateOf<FynxRemoteSocialClient.RemotePost?>(null) }
    var reportPost by remember { mutableStateOf<FynxRemoteSocialClient.RemotePost?>(null) }
    var reportBusy by remember { mutableStateOf(false) }
    var reportNotice by remember { mutableStateOf<String?>(null) }
    var videoDiscoveryOpen by remember { mutableStateOf(false) }
    var videoDiscoverySourcePostId by remember { mutableStateOf<String?>(null) }
    var postMedia by remember { mutableStateOf<Map<String, List<FynxHomePostMediaClient.PostMediaItem>>>(emptyMap()) }
    var mediaViewerPost by remember { mutableStateOf<FynxRemoteSocialClient.RemotePost?>(null) }
    var mediaViewerItems by remember { mutableStateOf<List<FynxHomePostMediaClient.PostMediaItem>>(emptyList()) }
    var mediaViewerIndex by remember { mutableIntStateOf(0) }
    val feedListState = rememberLazyListState()
    val focusedVideoPostId by remember(posts) {
        derivedStateOf {
            val layout = feedListState.layoutInfo
            val center = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            val postIds = posts.map { it.id }.toSet()
            layout.visibleItemsInfo.filter { info ->
                val key = info.key?.toString() ?: return@filter false
                if (key !in postIds) return@filter false
                val visibleStart = maxOf(info.offset, layout.viewportStartOffset)
                val visibleEnd = minOf(info.offset + info.size, layout.viewportEndOffset)
                val fraction = (visibleEnd - visibleStart).coerceAtLeast(0).toFloat() / info.size.coerceAtLeast(1).toFloat()
                fraction >= 0.60f
            }.minByOrNull { info -> kotlin.math.abs((info.offset + info.size / 2) - center) }?.key?.toString()
        }
    }

    fun resolveAuthorPhotos(items: List<FynxRemoteSocialClient.RemotePost>) {
        val names = items.map { it.authorUsername.removePrefix("@").trim() }.filter { it.isNotBlank() }.distinct()
        val missing = names.filterNot { authorPhotos.containsKey(it.lowercase()) }
        if (missing.isEmpty()) return
        scope.launch {
            val resolved = missing.map { username -> async(Dispatchers.IO) { username.lowercase() to (FynxSocialClient.searchUsers(context, username).getOrNull()?.firstOrNull { it.username.removePrefix("@").equals(username, true) }?.profilePhotoMediaId ?: FynxProfileRemoteClient.get(context, username).getOrNull()?.profilePhotoMediaId) } }.awaitAll().toMap()
            authorPhotos = authorPhotos + resolved
        }
    }
    fun hydratePeopleRecommendations() {
        scope.launch {
            val body = JSONObject().apply { put("name", "get_people_recommendations"); put("arguments", JSONObject()) }.toString()
            FynxBackendClient.postJson(context, "/api/assistant/tools", body).onSuccess { raw ->
                val people = JSONObject(raw).optJSONObject("result")?.optJSONArray("people")
                if (people != null) {
                    val parsed = buildList {
                        for (i in 0 until people.length()) {
                            val p = people.optJSONObject(i) ?: continue
                            val username = p.optString("username").trim()
                            if (username.isBlank()) continue
                            val photo = FynxProfileRemoteClient.cachedProfilePhotoId(context, username)
                            add(HomePeopleRecommendation(username, p.optString("displayName").ifBlank { username }, p.optBoolean("verified"), p.optInt("mutualFriends"), p.optString("reason"), photo))
                        }
                    }
                    peopleRecommendations = parsed
                    parsed.forEach { person -> scope.launch { FynxProfileRemoteClient.get(context, person.username).onSuccess { profile -> peopleRecommendations = peopleRecommendations.map { if (it.username.equals(person.username, true)) it.copy(photoId = profile.profilePhotoMediaId) else it } } } }
                }
            }
        }
    }
    fun hydrateActiveStatuses() {
        scope.launch {
            FynxStatusClient.list(context).onSuccess { statuses ->
                activeStatusOwners = statuses.filterNot(FynxStatus::isExpired).map { it.ownerUsername.removePrefix("@").trim().lowercase() }.toSet()
            }
        }
    }
    fun openAuthorStatus(username: String) {
        scope.launch {
            FynxStatusClient.list(context).onSuccess { statuses ->
                val owner = username.removePrefix("@").trim()
                val ownerStatuses = statuses.filterNot(FynxStatus::isExpired).filter { it.ownerUsername.equals(owner, true) }.sortedBy { it.createdAtMillis }
                if (ownerStatuses.isNotEmpty()) authorStatusViewer = ownerStatuses else onOpenAuthorProfile(owner)
            }.onFailure { onOpenAuthorProfile(username.removePrefix("@").trim()) }
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
    fun hydratePostMedia(items: List<FynxRemoteSocialClient.RemotePost>) {
        val targets = items.filter { it.id.isNotBlank() && !postMedia.containsKey(it.id) && !it.mediaUrl.isNullOrBlank() }
        if (targets.isEmpty()) return
        scope.launch {
            val resolved = targets.map { post -> async(Dispatchers.IO) { post.id to FynxHomePostMediaClient.list(context, post.id).getOrNull().orEmpty() } }.awaitAll().toMap()
            if (resolved.isNotEmpty()) postMedia = postMedia + resolved
        }
    }
    fun reload(forceRefresh: Boolean = false) {
        val now = System.currentTimeMillis(); if (feedRequestInFlight) return; if (forceRefresh && now - lastFeedRequestAt < FEED_REFRESH_DEBOUNCE_MS) return
        feedRequestInFlight = true; lastFeedRequestAt = now
        scope.launch { loading = true; FynxRemoteSocialClient.feedPage(context, limit = 20, offset = 0, useCache = !forceRefresh).onSuccess { page -> posts = page.posts; hasMore = page.hasMore; error = null; interactionStates = emptyMap(); reactionStates = emptyMap(); reactionPickerPostId = null; reactionUsersPostId = null; resolveAuthorPhotos(page.posts); hydrateInteractionStates(page.posts); hydrateReactionStates(page.posts); postMedia = emptyMap(); hydratePostMedia(page.posts) }.onFailure { error = if (it.message?.contains("HTTP 404", true) == true) "Your FYNX feed service is temporarily unavailable." else it.message ?: "Unable to load your feed." }; loading = false; feedRequestInFlight = false }
    }

    LaunchedEffect(publishRefreshKey) {
        if (publishRefreshKey > 0) reload(true)
    }

    fun loadMore() {
        if (loading || loadingMore || !hasMore || feedRequestInFlight) return
        feedRequestInFlight = true
        scope.launch { loadingMore = true; FynxRemoteSocialClient.feedPage(context, limit = 20, offset = posts.size, useCache = false).onSuccess { page -> val existing = posts.map { it.id }.toSet(); val additions = page.posts.filterNot { it.id in existing }; posts = posts + additions; hasMore = page.hasMore; error = null; resolveAuthorPhotos(additions); hydrateInteractionStates(additions); hydrateReactionStates(additions); hydratePostMedia(additions) }.onFailure { error = it.message ?: "Unable to load more posts." }; loadingMore = false; feedRequestInFlight = false }
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
        scope.launch { FynxRemoteSocialClient.deletePost(context, id).onSuccess { posts = posts.filterNot { it.id == id }; interactionStates = interactionStates - id; reactionStates = reactionStates - id; deletePost = null }.onFailure { error = it.message ?: "Unable to delete your post." }; interactionBusy = interactionBusy - id }
    }
    fun runShare(post: FynxRemoteSocialClient.RemotePost) {
        if (post.id in interactionBusy) return; interactionBusy = interactionBusy + post.id
        scope.launch { sharePost(context, post).onSuccess { runCatching { FynxDiscoveryClient.recordEngagement(context, "SHARE", post.id) } }.onFailure { error = it.message ?: "No app is available to share this post." }; interactionBusy = interactionBusy - post.id }
    }
    fun openVideoDiscovery(postId: String) {
        if (postId.isBlank()) return
        videoDiscoverySourcePostId = postId
        videoDiscoveryOpen = true
        scope.launch { runCatching { FynxDiscoveryClient.recordView(context, postId) } }
    }
    LaunchedEffect(Unit) { reload(); hydrateActiveStatuses(); hydratePeopleRecommendations() }

    LaunchedEffect(feedListState, posts.size, hasMore, loading, loadingMore, feedRequestInFlight) {
        snapshotFlow { feedListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to feedListState.layoutInfo.totalItemsCount }
            .collect { (lastVisibleIndex, totalItems) -> if (hasMore && !loading && !loadingMore && !feedRequestInFlight && lastVisibleIndex != null && lastVisibleIndex >= totalItems - 4) loadMore() }
    }

    LazyColumn(state = feedListState, modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 180.dp)) {
        header?.let { content -> item(key = "home_ai_status") { content() } }
        item(key = "feed_header") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Your feed", style = MaterialTheme.typography.titleMedium); Text("Real posts from your FYNX network", style = MaterialTheme.typography.bodySmall) }
                IconButton(onClick = { reload(true) }, enabled = !feedRequestInFlight) { Icon(Icons.Default.Refresh, "Refresh feed") }
            }
        }
        if (peopleRecommendations.isNotEmpty()) item(key = "people_recommendations") { HomePeopleRecommendationsCard(peopleRecommendations, onOpenProfile = { onOpenAuthorProfile(it) }) }
        if (loading) item(key = "feed_loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        error?.let { message -> item(key = "feed_error") { Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer); TextButton(onClick = { reload(true) }, enabled = !feedRequestInFlight) { Text("Retry") } } } } }
        if (!loading && posts.isEmpty() && error == null) item(key = "feed_empty") { Card(Modifier.fillMaxWidth(), shape = FynxDesign.LargeCardShape, colors = CardDefaults.cardColors(FynxDesign.Surface), border = BorderStroke(1.dp, FynxDesign.Outline.copy(alpha = .55f))) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Your feed is ready", style = MaterialTheme.typography.titleMedium); Text("There are no visible posts yet. Create a post or find real people to build your FYNX circle."); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onCreatePost) { Text("Create Post") }; OutlinedButton(onClick = onOpenFindPeople) { Text("Find People") } } } } }
        items(items = posts, key = { it.id }) { post ->
            val photoId = authorPhotos[post.authorUsername.removePrefix("@").trim().lowercase()]; val state = interactionStates[post.id] ?: FynxRemoteSocialClient.SocialInteractionState(false, false, 0, 0); val reaction = reactionStates[post.id] ?: FynxHomePostReactionsClient.ReactionState(); val busy = post.id in interactionBusy || "follow:${post.authorUsername.removePrefix("@").trim().lowercase()}" in interactionBusy
            RemotePostCard(post, currentUsername, photoId, activeStatusOwners.contains(post.authorUsername.removePrefix("@").trim().lowercase()), state, reaction, busy, postMedia[post.id].orEmpty(), playbackActive = focusedVideoPostId == post.id, onOpenProfile = { onOpenAuthorProfile(post.authorUsername.removePrefix("@").trim()) }, onOpenStatus = { openAuthorStatus(post.authorUsername.removePrefix("@").trim()) }, onLike = { runLike(it) }, onComment = { commentsPost = post }, onFollow = { runFollow(post.authorUsername, it) }, onDelete = { deletePost = post }, onReport = { reportPost = post }, onNotInterested = { id -> scope.launch { FynxDiscoveryClient.recordNotInterested(context, id) } }, onSave = { id, saved -> runInteraction(id, saved, { it.saved }, { it.savedCount }, { FynxRemoteSocialClient.save(context, id, saved) }) { current, value, count -> current.copy(saved = value, savedCount = count) } }, onRepost = { id, reposted -> runInteraction(id, reposted, { it.reposted }, { it.repostCount }, { FynxRemoteSocialClient.repost(context, id, reposted) }) { current, value, count -> current.copy(reposted = value, repostCount = count) } }, onShare = { runShare(post) }, onOpenReactionPicker = { reactionPickerPostId = if (reactionPickerPostId == post.id) null else post.id }, onReact = { id, selected -> runReaction(id, selected) }, reactionPickerOpen = reactionPickerPostId == post.id, onOpenReactionUsers = { reactionUsersPostId = post.id }, onOpenMarketplace = onOpenMarketplace, onOpenVideoDiscovery = { openVideoDiscovery(post.id) }, onOpenMediaViewer = { items, index -> mediaViewerPost = post; mediaViewerItems = items; mediaViewerIndex = index })
        }
        if (!loading && hasMore) item(key = "feed_load_more") { OutlinedButton(onClick = { loadMore() }, enabled = !loadingMore && !feedRequestInFlight, modifier = Modifier.fillMaxWidth()) { Text(if (loadingMore) "Loading more posts…" else "Load more posts") }
        }
    }
    reportPost?.let { post -> AlertDialog(onDismissRequest = { if (!reportBusy) { reportPost = null; reportNotice = null } }, title = { Text("Report post") }, text = { Text(reportNotice ?: "Report this post to FYNX for review.") }, confirmButton = { TextButton(enabled = !reportBusy, onClick = { reportBusy = true; reportNotice = "Sending report…"; scope.launch { FynxDiscoveryClient.recordEvent(context, "REPORT", postId = post.id, metadata = JSONObject().put("reason", "user_report")).onSuccess { reportNotice = "Report submitted."; reportBusy = false; delay(700L); reportPost = null; reportNotice = null }.onFailure { reportNotice = it.message ?: "Report could not be submitted."; reportBusy = false } } }) { Text("Report post") } }, dismissButton = { TextButton(enabled = !reportBusy, onClick = { reportPost = null; reportNotice = null }) { Text("Cancel") } }) }
    commentsPost?.let { post -> FynxHomeCommentsPanel(post = post, onClose = { commentsPost = null }, onCommentCountChanged = { newCount -> posts = posts.map { if (it.id == post.id) it.copy(commentCount = newCount) else it } }) }
    reactionUsersPostId?.let { postId -> ReactionUsersDialog(context = context, postId = postId, onDismiss = { reactionUsersPostId = null }) }
    authorStatusViewer?.let { HomeAuthorStatusDialog(it, onDismiss = { authorStatusViewer = null }) }
    deletePost?.let { post -> AlertDialog(onDismissRequest = { if (post.id !in interactionBusy) deletePost = null }, title = { Text("Delete post?") }, text = { Text("This will permanently remove your post from FYNX. This action cannot be undone.") }, confirmButton = { TextButton(onClick = { runDelete(post.id) }, enabled = post.id !in interactionBusy) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deletePost = null }, enabled = post.id !in interactionBusy) { Text("Cancel") } }) }
    if (mediaViewerPost != null && mediaViewerItems.isNotEmpty()) {
        FynxPostMediaViewer(context = context, post = mediaViewerPost!!, media = mediaViewerItems.map { FynxPostViewerItem(it.id, it.mediaType, it.position, it.mediaUrl) }, initialIndex = mediaViewerIndex, onDismiss = { mediaViewerPost = null; mediaViewerItems = emptyList() })
    }
    if (videoDiscoveryOpen) {
        VideoDiscoveryDialog(context = context, sourcePostId = videoDiscoverySourcePostId, onDismiss = { videoDiscoveryOpen = false; videoDiscoverySourcePostId = null })
    }
}

@Composable
private fun ReactionUsersDialog(context: Context, postId: String, onDismiss: () -> Unit) {
    var users by remember(postId) { mutableStateOf<List<FynxHomePostReactionsClient.ReactionUser>>(emptyList()) }
    var loading by remember(postId) { mutableStateOf(true) }
    var error by remember(postId) { mutableStateOf<String?>(null) }

    LaunchedEffect(postId) {
        FynxHomePostReactionsClient.users(context, postId)
            .onSuccess { users = it; error = null }
            .onFailure { error = it.message ?: "Unable to load reaction users." }
        loading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Reactions", style = MaterialTheme.typography.titleLarge)
                        Text("People who reacted to this post", style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close reactions") }
                }
                when {
                    loading -> Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    error != null -> Text(error ?: "Unable to load reactions.", Modifier.fillMaxWidth().padding(20.dp), color = MaterialTheme.colorScheme.error)
                    users.isEmpty() -> Text("No reactions are available.", Modifier.fillMaxWidth().padding(20.dp))
                    else -> Column(
                        Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())
                    ) {
                        users.forEach { user ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(reactionEmoji(user.reaction), style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(user.displayName.ifBlank { user.username }, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                    Text("@${user.username.removePrefix("@")}", style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary, maxLines = 1)
                                }
                                Text(reactionEmoji(user.reaction), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun reactionEmoji(reaction: String): String = when (reaction.uppercase()) {
    "LOVE" -> "❤️"
    "LAUGH" -> "😂"
    "WOW" -> "😮"
    "SAD" -> "😢"
    else -> "👍"
}

@Composable
private fun VideoDiscoveryDialog(context: Context, sourcePostId: String?, onDismiss: () -> Unit) {
    var videos by remember { mutableStateOf<List<FynxDiscoveryClient.TrendingPost>>(emptyList()) }
    var selectedVideo by remember { mutableStateOf<FynxDiscoveryClient.TrendingPost?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        FynxDiscoveryClient.trending(context, 50).onSuccess { result ->
            videos = result.filter { it.mediaId != null && it.mediaType.equals("video", true) }
            error = null
        }.onFailure { error = it.message ?: "Unable to load video discovery." }
        loading = false
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close video discovery") }
                    Column(Modifier.weight(1f)) {
                        Text("Discover videos", style = MaterialTheme.typography.titleLarge)
                        Text("Real FYNX videos ranked by the existing discovery service", style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary)
                    }
                }
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    error != null -> Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(error ?: "Video discovery unavailable.", color = MaterialTheme.colorScheme.error); Text("Try again after checking your connection.", style = MaterialTheme.typography.bodySmall) }
                    videos.isEmpty() -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) { Text("No discoverable videos are available yet.") }
                    else -> LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                        items(videos, key = { it.id }) { video ->
                            VideoDiscoveryCard(video = video, isSource = video.id == sourcePostId, onOpen = { selectedVideo = video })
                        }
                    }
                }
            }
        }
    }
    selectedVideo?.let { video ->
        val viewerPost = FynxRemoteSocialClient.RemotePost(
            id = video.id,
            authorId = "",
            authorUsername = video.authorUsername,
            authorDisplayName = video.authorDisplayName,
            text = video.text,
            visibility = "PUBLIC",
            mediaId = video.mediaId,
            mediaType = video.mediaType,
            mediaUrl = video.mediaId?.let { "/api/social/media/" + it },
            timestamp = video.timestamp,
            likeCount = video.likeCount,
            commentCount = video.commentCount,
            likedByCurrentUser = false,
            followedByCurrentUser = false
        )
        FynxPostMediaViewer(
            context = context,
            post = viewerPost,
            media = listOfNotNull(video.mediaId?.let { FynxPostViewerItem(it, "video", 0, "/api/social/media/" + it) }),
            onDismiss = { selectedVideo = null }
        )
    }
}

@Composable
private fun VideoDiscoveryCard(video: FynxDiscoveryClient.TrendingPost, isSource: Boolean, onOpen: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaPath = video.mediaId?.let { "/api/social/media/$it" }
    Card(Modifier.fillMaxWidth().padding(horizontal = 10.dp), shape = FynxDesign.LargeCardShape) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(video.authorDisplayName.ifBlank { video.authorUsername }, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text("${video.authorUsername.removePrefix("@")} • ${relative(video.timestamp)}", style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary, maxLines = 1)
                }
                if (isSource) Text("From your feed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (video.text.isNotBlank()) Text(video.text, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 4)
            mediaPath?.let { path ->
                RemoteSocialMedia(path, video.mediaType, onOpenMarketplace = null, onOpenMedia = { scope.launch { runCatching { FynxDiscoveryClient.recordView(context, video.id) } }; onOpen() })
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${video.likeCount} likes", style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary)
                Text("${video.commentCount} comments", style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary)
                Text("${video.shareCount} shares", style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary)
            }
        }
    }
}

@Composable
private fun RemotePostCard(post: FynxRemoteSocialClient.RemotePost, currentUsername: String, profilePhotoMediaId: String?, hasActiveStatus: Boolean, interactionState: FynxRemoteSocialClient.SocialInteractionState, reactionState: FynxHomePostReactionsClient.ReactionState, interactionBusy: Boolean, mediaItems: List<FynxHomePostMediaClient.PostMediaItem>, playbackActive: Boolean, onOpenProfile: () -> Unit, onOpenStatus: () -> Unit, onLike: (String) -> Unit, onComment: () -> Unit, onFollow: (Boolean) -> Unit, onDelete: () -> Unit, onReport: () -> Unit, onNotInterested: (String) -> Unit, onSave: (String, Boolean) -> Unit, onRepost: (String, Boolean) -> Unit, onShare: () -> Unit, onOpenReactionPicker: () -> Unit, onReact: (String, String) -> Unit, reactionPickerOpen: Boolean, onOpenReactionUsers: () -> Unit, onOpenMarketplace: () -> Unit, onOpenVideoDiscovery: () -> Unit, onOpenMediaViewer: (List<FynxHomePostMediaClient.PostMediaItem>, Int) -> Unit) {
    val context = LocalContext.current
    var menuOpen by remember(post.id) { mutableStateOf(false) }
    val marketplaceListingId = Regex("""(?m)^Listing ID:\s*(\d+)\s*$""").find(post.text)?.groupValues?.getOrNull(1)
    val mine = post.authorUsername.equals(currentUsername.removePrefix("@"), true); val marketplaceAd = post.text.startsWith(MARKETPLACE_AD_MARKER); val displayText = if (marketplaceAd) post.text.removePrefix(MARKETPLACE_AD_MARKER).trim() else post.text
    val openMarketplaceTarget: (() -> Unit)? = if (marketplaceAd) { { if (marketplaceListingId.isNullOrBlank()) onOpenMarketplace() else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FynxDeepLinkParser.marketplaceAppLink(marketplaceListingId)))) } } else null
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = if (hasActiveStatus) onOpenStatus else onOpenProfile, modifier = Modifier.size(50.dp)) { Box(Modifier.size(50.dp).border(if (hasActiveStatus) 2.dp else 0.dp, if (hasActiveStatus) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape).padding(if (hasActiveStatus) 2.dp else 0.dp)) { FynxRemoteProfileAvatar(profilePhotoMediaId, post.authorDisplayName.ifBlank { post.authorUsername }, Modifier.size(46.dp).clip(CircleShape)) } }
            Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(post.authorDisplayName.ifBlank { post.authorUsername }, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.clickable { onOpenProfile() }); Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Text("${post.authorUsername.removePrefix("@")} • ${relative(post.timestamp)}", style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary, maxLines = 1); val audienceIcon = when (post.visibility.uppercase()) { "PUBLIC" -> Icons.Default.Public; "FRIENDS_ONLY" -> Icons.Default.Group; "SELECTED_PEOPLE" -> Icons.Default.Person; "ONLY_ME" -> Icons.Default.Lock; else -> null }; audienceIcon?.let { Icon(it, contentDescription = when (post.visibility.uppercase()) { "PUBLIC" -> "Public post"; "FRIENDS_ONLY" -> "Friends post"; "SELECTED_PEOPLE" -> "Selected people post"; else -> "Only me post" }, modifier = Modifier.size(14.dp), tint = FynxDesign.TextSecondary) } }; if (!post.location.isNullOrBlank()) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) { Icon(Icons.Default.LocationOn, contentDescription = "Post location", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary); Text(post.location, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1) } }; if (!post.feelingActivity.isNullOrBlank()) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) { Icon(Icons.Default.SentimentSatisfied, contentDescription = if (post.feelingActivityType == "ACTIVITY") "Post activity" else "Post feeling", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary); Text(if (post.feelingActivityType == "ACTIVITY") "Activity: ${post.feelingActivity}" else "Feeling: ${post.feelingActivity}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1) } } }
            if (mine) {
                Box {
                    IconButton(onClick = { menuOpen = true }, enabled = !interactionBusy) { Icon(Icons.Default.MoreHoriz, "Post options") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (interactionState.saved) "Remove from saved" else "Save post") },
                            onClick = { menuOpen = false; onSave(post.id, !interactionState.saved) },
                            leadingIcon = { Icon(if (interactionState.saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, null) }
                        )
                        DropdownMenuItem(
                            text = { Text(if (interactionState.reposted) "Undo repost" else "Repost") },
                            onClick = { menuOpen = false; onRepost(post.id, !interactionState.reposted) },
                            leadingIcon = { Icon(Icons.Default.Repeat, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete post") },
                            onClick = { menuOpen = false; onDelete() },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, null) }
                        )
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onFollow(post.followedByCurrentUser) }, enabled = !interactionBusy) { Text(if (post.followedByCurrentUser) "Following" else "Follow") }
                    Box {
                        IconButton(onClick = { menuOpen = true }, enabled = !interactionBusy) { Icon(Icons.Default.MoreHoriz, "Post options") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("I'm interested") }, onClick = { menuOpen = false })
                            DropdownMenuItem(text = { Text("I'm not interested") }, onClick = { menuOpen = false; onNotInterested(post.id) })
                            DropdownMenuItem(text = { Text("Report post") }, onClick = { menuOpen = false; onReport() })
                        }
                    }
                }
            }
        }
        if (marketplaceAd) Text("MARKETPLACE", Modifier.padding(horizontal = 12.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        if (displayText.isNotBlank()) {
            val backgroundColor = post.textBackgroundColor?.let { Color(it) }
            val foregroundColor = post.textForegroundColor?.let { Color(it) } ?: MaterialTheme.colorScheme.onBackground
            if (backgroundColor != null && !post.textBackground.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = backgroundColor,
                    tonalElevation = 0.dp
                ) {
                    Text(displayText, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp), style = MaterialTheme.typography.bodyLarge.copy(color = foregroundColor))
                }
            } else {
                Text(displayText, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (mediaItems.isNotEmpty()) {
            PostMediaGrid(mediaItems, playbackActive = playbackActive, onOpenMedia = { index -> onOpenMediaViewer(mediaItems, index) }, onOpenVideoDiscovery = onOpenVideoDiscovery.takeIf { mediaItems.size == 1 })
        } else {
            post.mediaUrl?.let { RemoteSocialMedia(it, post.mediaType, openMarketplaceTarget, if (openMarketplaceTarget == null && post.mediaType.equals("video", true)) onOpenVideoDiscovery else null, playbackActive = playbackActive) }
        }
        if (!post.musicMediaId.isNullOrBlank()) MusicPostPlayer(post.musicMediaId!!, post.musicTitle.orEmpty(), post.musicArtist.orEmpty(), post.musicDurationMs)
        if (marketplaceAd) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End) { OutlinedButton(onClick = { openMarketplaceTarget?.invoke() ?: onOpenMarketplace() }) { Icon(Icons.Default.ShoppingBag, null); Spacer(Modifier.width(5.dp)); Text("View in Marketplace") } }
        if (reactionPickerOpen) Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), shape = MaterialTheme.shapes.large, tonalElevation = 2.dp) { Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { ReactionChoice("👍", "LIKE", reactionState.currentReaction == "LIKE", onReact = { onReact(post.id, it) }); ReactionChoice("❤️", "LOVE", reactionState.currentReaction == "LOVE", onReact = { onReact(post.id, it) }); ReactionChoice("😂", "LAUGH", reactionState.currentReaction == "LAUGH", onReact = { onReact(post.id, it) }); ReactionChoice("😮", "WOW", reactionState.currentReaction == "WOW", onReact = { onReact(post.id, it) }); ReactionChoice("😢", "SAD", reactionState.currentReaction == "SAD", onReact = { onReact(post.id, it) }) } }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            FeedActionButton(onClick = { onLike(post.id) }, onLongClick = onOpenReactionPicker, enabled = !interactionBusy, icon = if (post.likedByCurrentUser) Icons.Default.Favorite else Icons.Default.FavoriteBorder, label = "Like", longClickLabel = "Open post reactions", count = reactionState.total.coerceAtLeast(post.likeCount), active = post.likedByCurrentUser)
            FeedActionButton(onClick = onComment, enabled = !interactionBusy, icon = Icons.Default.ChatBubbleOutline, label = "Comment", count = post.commentCount)
            FeedActionButton(onClick = onShare, enabled = !interactionBusy, icon = Icons.Default.Share, label = "Share")
            FeedActionButton(onClick = { onSave(post.id, !interactionState.saved) }, enabled = !interactionBusy, icon = if (interactionState.saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, label = if (interactionState.saved) "Saved" else "Save", count = interactionState.savedCount, active = interactionState.saved)
            FeedActionButton(onClick = { onRepost(post.id, !interactionState.reposted) }, enabled = !interactionBusy, icon = Icons.Default.Repeat, label = if (interactionState.reposted) "Reposted" else "Repost", count = interactionState.repostCount, active = interactionState.reposted)
        }
        if (reactionState.total > 0) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) { Text(reactionSummary(reactionState), style = MaterialTheme.typography.labelMedium, color = FynxDesign.TextSecondary, modifier = Modifier.combinedClickable(role = Role.Button, onClickLabel = "Open people who reacted", onClick = onOpenReactionUsers)) }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PostMediaGrid(items: List<FynxHomePostMediaClient.PostMediaItem>, playbackActive: Boolean, onOpenMedia: (Int) -> Unit, onOpenVideoDiscovery: (() -> Unit)?) {
    val visual = items.filter { it.mediaType.equals("image", true) || it.mediaType.equals("video", true) }.take(4)
    if (visual.isEmpty()) return
    @Composable fun Cell(item: FynxHomePostMediaClient.PostMediaItem, index: Int) {
        Box(Modifier.clip(RoundedCornerShape(4.dp)).clickable { onOpenMedia(index) }) {
            RemoteSocialMedia(item.mediaUrl, item.mediaType, onOpenMedia = { onOpenMedia(index) }, playbackActive = playbackActive && index == visual.indexOfFirst { it.mediaType.equals("video", true) })
        }
    }
    when (visual.size) {
        1 -> Cell(visual[0], 0)
        2 -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) { Cell(visual[0], 0); Cell(visual[1], 1) }
        3 -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) { Box(Modifier.weight(1f)) { Cell(visual[0], 0) }; Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { Cell(visual[1], 1); Cell(visual[2], 2) } }
        else -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) { Box(Modifier.weight(1f)) { Cell(visual[0], 0) }; Box(Modifier.weight(1f)) { Cell(visual[1], 1) } }; Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) { Box(Modifier.weight(1f)) { Cell(visual[2], 2) }; Box(Modifier.weight(1f)) { Cell(visual[3], 3) } } }
    }
    if (visual.size > 1) Text(visual.size.toString() + " media", Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary)
}

@Composable
private fun ReactionChoice(emoji: String, reaction: String, active: Boolean, onReact: (String) -> Unit) { TextButton(onClick = { onReact(reaction) }, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = if (active) MaterialTheme.colorScheme.primary else FynxDesign.TextPrimary)) { Text(emoji, style = MaterialTheme.typography.titleLarge) } }
private fun reactionSummary(state: FynxHomePostReactionsClient.ReactionState): String { val order = listOf("LIKE" to "👍", "LOVE" to "❤️", "LAUGH" to "😂", "WOW" to "😮", "SAD" to "😢"); val visible = order.filter { (key, _) -> (state.counts[key] ?: 0) > 0 }.take(5); return "${visible.joinToString(" ") { it.second }}  ${state.total} reaction${if (state.total == 1) "" else "s"}" }

@Composable
private fun RowScope.FeedActionButton(onClick: () -> Unit, onLongClick: (() -> Unit)? = null, enabled: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, longClickLabel: String? = null, count: Int? = null, active: Boolean = false) {
    Box(Modifier.heightIn(min = 50.dp).weight(1f).combinedClickable(enabled = enabled, role = Role.Button, onClickLabel = label, onLongClickLabel = longClickLabel, onLongClick = onLongClick, onClick = onClick), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(21.dp), tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            if (count != null) { Spacer(Modifier.width(4.dp)); Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
    }
}

private fun sharePost(context: Context, post: FynxRemoteSocialClient.RemotePost): Result<Unit> = runCatching { val text = if (post.text.startsWith(MARKETPLACE_AD_MARKER)) "${post.text.removePrefix(MARKETPLACE_AD_MARKER).trim()}\n\nSee this product on FYNX Marketplace." else "${post.authorDisplayName.ifBlank { post.authorUsername }} on FYNX:\n${post.text}".trim(); val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); putExtra(Intent.EXTRA_TITLE, "Share from FYNX") }; context.startActivity(Intent.createChooser(intent, "Share with…")) }

@Composable
private fun RemoteSocialMedia(path: String, type: String?, onOpenMarketplace: (() -> Unit)? = null, onOpenMedia: (() -> Unit)? = null, playbackActive: Boolean = true) {
    val context = LocalContext.current
    var file by remember(path) { mutableStateOf<File?>(null) }
    var videoAspectRatio by remember(path) { mutableFloatStateOf(16f / 9f) }
    var videoView by remember(path) { mutableStateOf<VideoView?>(null) }
    var preparedPlayer by remember(path) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(path) { mutableStateOf(false) }
    var muted by remember(path) { mutableStateOf(true) }
    var fullscreen by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path) { file = withContext(Dispatchers.IO) { FynxMediaCache.getOrDownload(context, path, type) } }
    LaunchedEffect(file, type) {
        if (file != null && type == "video") videoAspectRatio = withContext(Dispatchers.IO) {
            runCatching {
                MediaMetadataRetriever().run {
                    setDataSource(file!!.absolutePath)
                    val width = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 16f
                    val height = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 9f
                    val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    release()
                    if (rotation == 90 || rotation == 270) height / width else width / height
                }.coerceIn(0.56f, 1.91f)
            }.getOrDefault(16f / 9f)
        }
    }
    LaunchedEffect(playbackActive, videoView) {
        val view = videoView ?: return@LaunchedEffect
        if (playbackActive) { if (!view.isPlaying) runCatching { view.start(); playing = true } }
        else { if (view.isPlaying) runCatching { view.pause() }; playing = false }
    }
    DisposableEffect(videoView) { onDispose { videoView?.stopPlayback(); preparedPlayer = null } }
    if (file == null) Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    else if (type == "audio") AudioPostPlayer(file!!)
    else if (type == "video") {
        Box(Modifier.fillMaxWidth().aspectRatio(videoAspectRatio)) {
            AndroidView(factory = { ctx ->
                FynxPassiveVideoView(ctx).apply {
                    videoView = this
                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                    setMediaController(MediaController(ctx))
                    setVideoPath(file!!.absolutePath)
                    setOnPreparedListener { player ->
                        preparedPlayer = player
                        player.isLooping = true
                        player.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f)
                        if (playbackActive) { start(); playing = true }
                    }
                    setOnCompletionListener { playing = false }
                }
            }, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().clickable {
                when {
                    onOpenMarketplace != null -> onOpenMarketplace()
                    videoView?.isPlaying == true -> { videoView?.pause(); playing = false }
                    playbackActive -> { videoView?.start(); playing = true }
                    else -> fullscreen = true
                }
            })
            Row(Modifier.align(Alignment.BottomEnd).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (onOpenMedia != null) IconButton(onClick = onOpenMedia) { Icon(Icons.Default.OpenInNew, "Open video discovery", tint = Color.White) }
                IconButton(onClick = { muted = !muted; preparedPlayer?.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f) }) {
                    Icon(if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, if (muted) "Unmute video" else "Mute video", tint = Color.White)
                }
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause video" else "Play video", tint = Color.White)
            }
        }
    } else {
        var bitmap by remember(file) { mutableStateOf<android.graphics.Bitmap?>(null) }
        LaunchedEffect(file) { bitmap = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(file!!.absolutePath) }.getOrNull() } }
        bitmap?.let { image ->
            Box(Modifier.fillMaxWidth().aspectRatio((image.width.toFloat() / image.height.toFloat()).coerceIn(0.62f, 1.9f)).clickable { when { onOpenMarketplace != null -> onOpenMarketplace(); onOpenMedia != null -> onOpenMedia(); else -> fullscreen = true } }) {
                Image(image.asImageBitmap(), "Post media", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
    }
    if (fullscreen && file != null && onOpenMarketplace == null && onOpenMedia == null) {
        Dialog(onDismissRequest = { fullscreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                if (type == "video") {
                    AndroidView(factory = { ctx -> VideoView(ctx).apply { layoutParams = ViewGroup.LayoutParams(-1, -1); setMediaController(MediaController(ctx)); setVideoURI(Uri.fromFile(file)); setOnPreparedListener { it.isLooping = true; start() } } }, modifier = Modifier.fillMaxSize())
                } else {
                    var bitmap by remember(file) { mutableStateOf<android.graphics.Bitmap?>(null) }
                    LaunchedEffect(file) { bitmap = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(file!!.absolutePath) }.getOrNull() } }
                    bitmap?.let { Image(it.asImageBitmap(), "Full screen post media", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                IconButton(onClick = { fullscreen = false }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) { Icon(Icons.Default.Close, "Close media viewer", tint = Color.White) }
            }
        }
    }
}

private fun relative(timestamp: Long): String { val minutes = TimeUnit.MILLISECONDS.toMinutes((System.currentTimeMillis() - timestamp).coerceAtLeast(0L)); return when { minutes < 1 -> "now"; minutes < 60 -> "${minutes}m"; minutes < 1440 -> "${minutes / 60}h"; else -> "${minutes / 1440}d" } }

@Composable
private fun MusicPostPlayer(mediaId: String, title: String, artist: String, durationMs: Long) {
    val context = LocalContext.current
    var file by remember(mediaId) { mutableStateOf<File?>(null) }
    LaunchedEffect(mediaId) { file = withContext(Dispatchers.IO) { FynxMediaCache.getOrDownload(context, "/api/social/music/media/" + mediaId, "audio") } }
    if (file != null) {
        val player = remember(file) { MediaPlayer().apply { setDataSource(file!!.absolutePath); prepare() } }
        var playing by remember(file) { mutableStateOf(false) }
        DisposableEffect(player) {
            player.setOnCompletionListener { playing = false; runCatching { player.seekTo(0) } }
            onDispose { runCatching { player.release() } }
        }
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp)) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, "Music", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(title.ifBlank { "Music" }, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(artist.ifBlank { "Unknown artist" }, style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary, maxLines = 1)
                }
                Text(formatMusicDuration(durationMs.takeIf { it > 0 } ?: player.duration.toLong()), style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary)
                IconButton(onClick = { runCatching { if (player.isPlaying) { player.pause(); playing = false } else { if (player.currentPosition >= player.duration) player.seekTo(0); player.start(); playing = true } } }) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause music" else "Play music") }
            }
        }
    }
}

private fun formatMusicDuration(durationMs: Long): String {
    val seconds = (durationMs.coerceAtLeast(0L) / 1000L).toInt()
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

@Composable
private fun AudioPostPlayer(file: File) {
    val player = remember(file) {
        MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
        }
    }
    var playing by remember(file) { mutableStateOf(false) }

    DisposableEffect(player) {
        player.setOnCompletionListener {
            playing = false
            runCatching { player.seekTo(0) }
        }
        onDispose {
            runCatching { player.release() }
        }
    }

    val durationSeconds = (player.duration / 1000).coerceAtLeast(0)

    Surface(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .widthIn(min = 220.dp, max = 340.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    runCatching {
                        if (player.isPlaying) {
                            player.pause()
                            playing = false
                        } else {
                            if (player.currentPosition >= player.duration) player.seekTo(0)
                            player.start()
                            playing = true
                        }
                    }
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pause voice note" else "Play voice note",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.width(4.dp))

            val waveformHeights = listOf(12, 18, 24, 15, 28, 20, 32, 17, 25, 13, 22, 30, 18, 26, 15, 23, 12, 20)
            var playbackProgress by remember(file) { mutableFloatStateOf(0f) }
            val waveTransition = rememberInfiniteTransition(label = "voice_wave")
            val wavePhase by waveTransition.animateFloat(
                initialValue = 0f,
                targetValue = waveformHeights.size.toFloat(),
                animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
                label = "voice_wave_phase"
            )
            LaunchedEffect(playing, player) {
                while (playing) {
                    playbackProgress = if (player.duration > 0) {
                        (player.currentPosition.toFloat() / player.duration.toFloat()).coerceIn(0f, 1f)
                    } else 0f
                    delay(50L)
                }
                playbackProgress = if (player.duration > 0) {
                    (player.currentPosition.toFloat() / player.duration.toFloat()).coerceIn(0f, 1f)
                } else 0f
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                waveformHeights.forEachIndexed { index, baseHeight ->
                    val distance = kotlin.math.abs(index - wavePhase)
                    val pulse = if (playing) (kotlin.math.sin((index + wavePhase) * 0.72f) + 1f) * 0.18f else 0f
                    val height = (baseHeight * (0.82f + pulse)).coerceIn(7f, 34f)
                    val passed = index < playbackProgress * waveformHeights.size
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(height.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (passed || (playing && distance < 0.9f))
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                            )
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            ) {
                Text(
                    text = "${durationSeconds}s",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}


@Composable
private fun HomeAuthorStatusDialog(statuses: List<FynxStatus>, onDismiss: () -> Unit) {
    if (statuses.isEmpty()) return
    var index by remember(statuses) { mutableIntStateOf(0) }
    val status = statuses[index.coerceIn(0, statuses.lastIndex)]
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(status.textStyle.backgroundColor))) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(status.ownerDisplayName.ifBlank { status.ownerUsername }, style = MaterialTheme.typography.titleMedium, color = Color(status.textStyle.foregroundColor))
                Spacer(Modifier.height(16.dp))
                when (status.type) {
                    FynxStatusType.TEXT -> Text(status.text.orEmpty(), color = Color(status.textStyle.foregroundColor), style = MaterialTheme.typography.headlineSmall)
                    FynxStatusType.PHOTO -> status.contentUri?.let { FynxRemoteMedia(it, "image", Modifier.fillMaxWidth().heightIn(max = 560.dp)) }
                    FynxStatusType.VIDEO -> status.contentUri?.let { FynxRemoteMedia(it, "video", Modifier.fillMaxWidth().heightIn(max = 560.dp)) }
                    FynxStatusType.VOICE -> Text("Voice Status", color = Color(status.textStyle.foregroundColor))
                }
                status.text?.takeIf { status.type != FynxStatusType.TEXT && it.isNotBlank() }?.let { Text(it, color = Color(status.textStyle.foregroundColor), modifier = Modifier.padding(top = 12.dp)) }
            }
            Row(Modifier.align(Alignment.TopEnd).padding(12.dp)) { TextButton(onClick = onDismiss) { Text("Close") } }
            Row(Modifier.align(Alignment.CenterStart).padding(8.dp)) { IconButton(onClick = { if (index > 0) index-- }, enabled = index > 0) { Icon(Icons.Default.ArrowBack, "Previous Status") } }
            Row(Modifier.align(Alignment.CenterEnd).padding(8.dp)) { IconButton(onClick = { if (index < statuses.lastIndex) index++ }, enabled = index < statuses.lastIndex) { Icon(Icons.Default.ArrowForward, "Next Status") } }
        }
    }
}


@Composable
private fun HomePeopleRecommendationsCard(items: List<HomePeopleRecommendation>, onOpenProfile: (String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp), shape = FynxDesign.LargeCardShape) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("People You May Know", style = MaterialTheme.typography.titleMedium)
            Text("Real FYNX people based on your existing relationships.", style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary)
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items, key = { it.username }) { person ->
                    Column(Modifier.width(132.dp).clickable { onOpenProfile(person.username) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(64.dp).clip(CircleShape)) { FynxRemoteProfileAvatar(person.photoId, person.displayName, Modifier.fillMaxSize()) }
                        Text(person.displayName, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Text("@${person.username.removePrefix("@")}", style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary, maxLines = 1)
                        if (person.mutualFriends > 0) Text("${person.mutualFriends} mutual friend${if (person.mutualFriends == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        else Text(person.reason.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = FynxDesign.TextSecondary, maxLines = 1)
                    }
                }
            }
        }
    }
}
