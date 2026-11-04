package com.fynx.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import android.content.pm.ApplicationInfo
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private const val MAX_COMMENT_LENGTH = 1000
private const val COMMENT_PAGE_SIZE = 50

private data class VisibleHomeReply(val comment: FynxRemoteSocialClient.RemoteComment, val depth: Int)

private fun flattenHomeReplies(
    parentId: String,
    expandedReplies: Map<String, List<FynxRemoteSocialClient.RemoteComment>>,
    depth: Int = 1,
    ancestors: Set<String> = emptySet()
): List<VisibleHomeReply> {
    if (depth > 5 || parentId in ancestors) return emptyList()
    val nextAncestors = ancestors + parentId
    return expandedReplies[parentId].orEmpty().flatMap { child ->
        listOf(VisibleHomeReply(child, depth)) +
            flattenHomeReplies(child.id, expandedReplies, depth + 1, nextAncestors)
    }
}

@Composable
fun FynxHomeCommentsPanel(
    post: FynxRemoteSocialClient.RemotePost,
    initialCommentId: String? = null,
    interactionState: FynxRemoteSocialClient.SocialInteractionState = FynxRemoteSocialClient.SocialInteractionState(false, false, 0, 0),
    interactionBusy: Boolean = false,
    onLikePost: () -> Unit = {},
    onRepostPost: () -> Unit = {},
    onSavePost: () -> Unit = {},
    onSharePost: () -> Unit = {},
    onClose: () -> Unit,
    onCommentCountChanged: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var comments by remember(post.id) { mutableStateOf<List<FynxRemoteSocialClient.RemoteComment>>(emptyList()) }
    var text by rememberSaveable(post.id) { mutableStateOf("") }
    var commentCount by remember(post.id) { mutableIntStateOf(post.commentCount) }
    var loading by remember(post.id) { mutableStateOf(false) }
    var loadingMore by remember(post.id) { mutableStateOf(false) }
    var sending by remember(post.id) { mutableStateOf(false) }
    var error by remember(post.id) { mutableStateOf<String?>(null) }
    var nextCursor by remember(post.id) { mutableStateOf<String?>(null) }
    var replyingToId by rememberSaveable(post.id) { mutableStateOf<String?>(null) }
    var replyLoadingId by remember(post.id) { mutableStateOf<String?>(null) }
    var replyErrorId by remember(post.id) { mutableStateOf<String?>(null) }
    var expandedReplies by remember(post.id) { mutableStateOf<Map<String, List<FynxRemoteSocialClient.RemoteComment>>>(emptyMap()) }
    var authorPhotos by remember(post.id) { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var reactionStates by remember(post.id) { mutableStateOf<Map<String, FynxRemoteSocialClient.CommentReactionState>>(emptyMap()) }
    var reactionPending by remember(post.id) { mutableStateOf<Set<String>>(emptySet()) }
    var commentSort by rememberSaveable(post.id) { mutableStateOf("Relevant") }
    var openCommentMenuId by remember(post.id) { mutableStateOf<String?>(null) }
    var highlightedCommentId by remember(post.id, initialCommentId) { mutableStateOf(initialCommentId) }
    val consumedCursors = remember(post.id) { mutableStateOf<Set<String>>(emptySet()) }
    val availableComments = remember(comments, expandedReplies) {
        (comments + expandedReplies.values.flatten()).distinctBy { it.id }
    }
    val replyingTo = replyingToId?.let { id -> availableComments.firstOrNull { it.id == id } }

    fun resolveCommenterPhotos(items: List<FynxRemoteSocialClient.RemoteComment>) {
        val names = items.map { it.authorUsername.removePrefix("@").trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val normalized = names.map { it.lowercase() }
        val known = authorPhotos.toMutableMap()
        normalized.forEach { username ->
            if (!known.containsKey(username)) {
                known[username] = FynxProfileRemoteClient.cachedProfilePhotoId(context, username)
            }
        }
        authorPhotos = known
        val missing = names.filter { username ->
            val key = username.lowercase()
            !authorPhotos.containsKey(key) || authorPhotos[key].isNullOrBlank()
        }
        if (missing.isEmpty()) return
        scope.launch {
            val resolved = missing.map { username ->
                async(kotlinx.coroutines.Dispatchers.IO) {
                    val key = username.lowercase()
                    key to FynxProfileRemoteClient.get(context, username).getOrNull()?.profilePhotoMediaId
                }
            }.let { jobs -> jobs.map { it.await() }.toMap() }
            if (resolved.isNotEmpty()) authorPhotos = authorPhotos + resolved
        }
    }

    fun loadReactions(items: List<FynxRemoteSocialClient.RemoteComment>) {
        val ids = items.map { it.id }.distinct()
        if (ids.isEmpty()) return
        scope.launch {
            FynxRemoteSocialClient.commentReactions(context, post.id, ids).onSuccess { loaded ->
                reactionStates = reactionStates + loaded
            }
        }
    }
    fun reactToComment(comment: FynxRemoteSocialClient.RemoteComment, selected: String) {
        if (comment.id in reactionPending) return
        val previous = reactionStates[comment.id] ?: FynxRemoteSocialClient.CommentReactionState(emptyMap(), null)
        val nextReaction = if (previous.currentReaction == selected) null else selected
        val optimisticCounts = previous.counts.toMutableMap()
        previous.currentReaction?.let { old -> optimisticCounts[old] = (optimisticCounts[old] ?: 1) - 1 }
        nextReaction?.let { new -> optimisticCounts[new] = (optimisticCounts[new] ?: 0) + 1 }
        reactionStates = reactionStates + (comment.id to FynxRemoteSocialClient.CommentReactionState(optimisticCounts.filterValues { it > 0 }, nextReaction))
        reactionPending = reactionPending + comment.id
        scope.launch {
            FynxRemoteSocialClient.setCommentReaction(context, post.id, comment.id, nextReaction)
                .onSuccess { confirmed -> reactionStates = reactionStates + (comment.id to confirmed) }
                .onFailure { reactionStates = reactionStates + (comment.id to previous); error = it.message ?: "Reaction could not be saved." }
            reactionPending = reactionPending - comment.id
        }
    }
    fun resetPagingState() { nextCursor = null; consumedCursors.value = emptySet(); loadingMore = false }

    fun shareComment(comment: FynxRemoteSocialClient.RemoteComment) {
        val author = comment.authorDisplayName.ifBlank { comment.authorUsername }
        val shareText = "$author on FYNX: ${comment.text}"
        runCatching {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }, "Share comment"))
        }.onFailure { error = it.message ?: "Comment could not be shared." }
    }
    fun commentEngagement(comment: FynxRemoteSocialClient.RemoteComment): Int =
        reactionStates[comment.id]?.counts?.values?.sum() ?: 0
    fun loadComments() {
        if (sending || loading) return
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 && post.id.startsWith("ci-home-post-")) {
            val now = System.currentTimeMillis()
            val first = FynxRemoteSocialClient.RemoteComment("ci-comment-1", "This video looks amazing! The portrait preview fits naturally in the feed. ❤️", now - 8 * 60_000L, "ci-author-1", "@amara", "Amara Okafor")
            val second = FynxRemoteSocialClient.RemoteComment("ci-comment-2", "I like how the full video opens when you tap it.", now - 5 * 60_000L, "ci-author-2", "@david", "David James")
            val reply = FynxRemoteSocialClient.RemoteComment("ci-reply-1", "Exactly! It keeps the feed clean while the full video gets the space it needs.", now - 3 * 60_000L, "ci-author-3", "@zara", "Zara Bello", first.id)
            comments = listOf(first, second)
            expandedReplies = mapOf(first.id to listOf(reply))
            reactionStates = mapOf(
                first.id to FynxRemoteSocialClient.CommentReactionState(mapOf("LOVE" to 12, "THUMBS_UP" to 5, "LAUGH" to 3), "LOVE"),
                second.id to FynxRemoteSocialClient.CommentReactionState(mapOf("THUMBS_UP" to 8, "WOW" to 2), null),
                reply.id to FynxRemoteSocialClient.CommentReactionState(mapOf("LOVE" to 4), null)
            )
            commentCount = maxOf(post.commentCount, 3)
            onCommentCountChanged(commentCount)
            resolveCommenterPhotos(listOf(first, second, reply))
            loading = false
            error = null
            return
        }
        loading = true; error = null; replyLoadingId = null; replyErrorId = null; expandedReplies = emptyMap(); resetPagingState()
        scope.launch {
            FynxRemoteSocialClient.commentsPage(context, post.id, null, COMMENT_PAGE_SIZE)
                .onSuccess { page -> comments = page.comments.distinctBy { it.id }; resolveCommenterPhotos(comments); loadReactions(comments); nextCursor = page.nextCursor }
                .onFailure { failure -> comments = emptyList(); error = if (failure.message?.contains("404") == true) "This post is no longer available." else failure.message ?: "Unable to load comments." }
            loading = false
        }
    }
    LaunchedEffect(comments, highlightedCommentId) {
        val targetId = highlightedCommentId ?: return@LaunchedEffect
        val target = comments.firstOrNull { it.id == targetId } ?: return@LaunchedEffect
        val commentLookup = comments.associateBy { it.id }
        var child = target
        while (child.parentCommentId != null) {
            val parent = commentLookup[child.parentCommentId] ?: break
            val existing = expandedReplies[parent.id].orEmpty()
            if (existing.none { it.id == child.id }) {
                expandedReplies = expandedReplies + (parent.id to (existing + child).distinctBy { it.id })
            }
            child = parent
        }
        val topLevel = comments.filter { it.parentCommentId == null }
        val index = topLevel.indexOfFirst { it.id == child.id }
        if (index >= 0) {
            listState.animateScrollToItem(index + if (nextCursor != null) 1 else 0)
            highlightedCommentId = null
        }
    }
    fun loadMore() {
        val cursor = nextCursor ?: return
        if (loadingMore || loading || sending || consumedCursors.value.contains(cursor)) return
        consumedCursors.value = consumedCursors.value + cursor; loadingMore = true; error = null
        scope.launch {
            FynxRemoteSocialClient.commentsPage(context, post.id, cursor, COMMENT_PAGE_SIZE)
                .onSuccess { page ->
                    val existingIds = comments.asSequence().map { it.id }.toHashSet()
                    val fresh = page.comments.filterNot { existingIds.contains(it.id) }
                    comments = (fresh + comments).distinctBy { it.id }; resolveCommenterPhotos(fresh); loadReactions(fresh)
                    nextCursor = page.nextCursor?.takeUnless { it == cursor || consumedCursors.value.contains(it) }
                }
                .onFailure { failure -> consumedCursors.value = consumedCursors.value - cursor; error = failure.message ?: "Unable to load more comments." }
            loadingMore = false
        }
    }
    fun send() {
        val value = text.trim(); if (value.isEmpty() || sending || loading || value.length > MAX_COMMENT_LENGTH) return
        val parentId = replyingToId; sending = true; error = null; replyErrorId = null
        scope.launch {
            val result = if (parentId != null) FynxRemoteSocialClient.addReply(context, post.id, parentId, value) else FynxRemoteSocialClient.addComment(context, post.id, value)
            result.onSuccess { comment ->
                comments = (comments + comment).distinctBy { it.id }; resolveCommenterPhotos(listOf(comment)); commentCount += 1; onCommentCountChanged(commentCount)
                if (comment.parentCommentId != null) { val parent = comment.parentCommentId!!; expandedReplies = expandedReplies + mapOf(parent to ((expandedReplies[parent].orEmpty() + comment).distinctBy { it.id })) }
                text = ""; replyingToId = null
                if (comment.parentCommentId == null) scope.launch { val topLevelCount = comments.count { it.parentCommentId == null }; listState.animateScrollToItem((topLevelCount - 1 + if (nextCursor != null) 1 else 0).coerceAtLeast(0)) }
            }.onFailure { failure -> if (parentId != null) replyErrorId = parentId; error = failure.message ?: if (parentId != null) "Unable to send reply." else "Unable to send comment." }
            sending = false
        }
    }
    fun toggleReplies(comment: FynxRemoteSocialClient.RemoteComment) {
        if (replyLoadingId != null || sending) return
        if (expandedReplies.containsKey(comment.id)) { expandedReplies = expandedReplies - comment.id; replyErrorId = null; return }
        replyLoadingId = comment.id; replyErrorId = null; error = null
        scope.launch {
            FynxRemoteSocialClient.replies(context, post.id, comment.id)
                .onSuccess { loaded -> val unique = loaded.distinctBy { it.id }; expandedReplies = expandedReplies + mapOf(comment.id to unique); resolveCommenterPhotos(unique); loadReactions(unique) }
                .onFailure { failure -> replyErrorId = comment.id; error = failure.message ?: "Unable to load replies." }
            replyLoadingId = null
        }
    }
    LaunchedEffect(post.id) { comments = emptyList(); text = ""; commentCount = post.commentCount; replyingToId = null; authorPhotos = emptyMap(); resetPagingState(); loadComments() }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = onClose)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, shape = MaterialTheme.shapes.extraLarge, tonalElevation = 8.dp) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close comments") }
                        Column(Modifier.weight(1f)) { Text("Comments", style = MaterialTheme.typography.titleLarge); Text("$commentCount comments", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        IconButton(onClick = { if (!loading && !sending && replyLoadingId == null) loadComments() }, enabled = !loading && !sending && replyLoadingId == null) { Icon(Icons.Default.Refresh, "Refresh comments") }
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Sort by", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = commentSort == "Relevant", onClick = { commentSort = "Relevant" }, label = { Text("Relevant") })
                        Spacer(Modifier.width(7.dp))
                        FilterChip(selected = commentSort == "Newest", onClick = { commentSort = "Newest" }, label = { Text("Newest") })
                    }
                    HorizontalDivider()
                    Column(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text("POST CONVERSATION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            post.text.ifBlank { "Join the conversation and share a thoughtful reply." },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Started by ${post.authorDisplayName.ifBlank { post.authorUsername }} · @${post.authorUsername.removePrefix("@")} · ${relative(post.timestamp)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CommentPostEngagementAction(
                                icon = null,
                                emoji = "👍",
                                label = "Like",
                                count = post.likeCount,
                                selected = post.likedByCurrentUser,
                                enabled = !interactionBusy,
                                onClick = onLikePost
                            )
                            CommentPostEngagementAction(
                                icon = Icons.Default.ChatBubbleOutline,
                                label = "Comments",
                                count = commentCount,
                                selected = true,
                                enabled = false,
                                onClick = {}
                            )
                            CommentPostEngagementAction(
                                icon = Icons.Default.Repeat,
                                label = "Repost",
                                count = interactionState.repostCount,
                                selected = interactionState.reposted,
                                enabled = !interactionBusy,
                                onClick = onRepostPost
                            )
                            CommentPostEngagementAction(
                                icon = if (interactionState.saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                label = "Save",
                                count = interactionState.savedCount,
                                selected = interactionState.saved,
                                enabled = !interactionBusy,
                                onClick = onSavePost
                            )
                            CommentPostEngagementAction(
                                icon = Icons.Default.Share,
                                label = "Share",
                                count = null,
                                selected = false,
                                enabled = !interactionBusy,
                                onClick = onSharePost
                            )
                        }
                    }
                    HorizontalDivider()
                    when {
                        loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        error != null && comments.isEmpty() -> Column(Modifier.fillMaxWidth().weight(1f).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(error!!, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { loadComments() }) { Text("Retry") } }
                        comments.isEmpty() -> Column(Modifier.fillMaxWidth().weight(1f).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text("No comments yet", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(6.dp)); Text("Start the conversation.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        else -> LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (nextCursor != null) item(key = "comments_load_more") { OutlinedButton(onClick = { loadMore() }, enabled = !loadingMore && !sending, modifier = Modifier.fillMaxWidth()) { Text(if (loadingMore) "Loading more comments…" else "Load earlier comments") } }
                            items((if (commentSort == "Newest") comments.filter { it.parentCommentId == null }.sortedByDescending { it.timestamp } else comments.filter { it.parentCommentId == null }.sortedWith(compareByDescending<FynxRemoteSocialClient.RemoteComment> { commentEngagement(it) }.thenByDescending { it.timestamp })), key = { it.id }) { comment ->
                                Column(Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                        val photo = authorPhotos[comment.authorUsername.removePrefix("@").trim().lowercase()]
                                        FynxRemoteProfileAvatar(photo, comment.authorDisplayName.ifBlank { comment.authorUsername }, Modifier.size(40.dp).clip(CircleShape), ownerUsername = comment.authorUsername)
                                        Spacer(Modifier.width(11.dp))
                                        Column(Modifier.weight(1f)) {
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                Text(comment.authorDisplayName.ifBlank { comment.authorUsername }, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                                Spacer(Modifier.width(6.dp))
                                                Text("@${comment.authorUsername.removePrefix("@")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                                Text(" · ${relative(comment.timestamp)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Spacer(Modifier.weight(1f))
                                                val engagement = commentEngagement(comment)
                                                if (engagement > 0) Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                                                    Text(compactEngagement(engagement), Modifier.padding(horizontal = 7.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Text(" ⋯", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                                            HomeCommentReactionRow(reactionStates[comment.id], reactionPending.contains(comment.id)) { reaction -> reactToComment(comment, reaction) }
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                                TextButton(onClick = { if (!sending) replyingToId = comment.id }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) { Text("↩  Reply", style = MaterialTheme.typography.labelSmall) }
                                                TextButton(onClick = { toggleReplies(comment) }, enabled = replyLoadingId == null && !sending, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) {
                                                    val loadedReplyCount = expandedReplies[comment.id]?.size
                                                    Text("💬 ${if (loadedReplyCount != null) loadedReplyCount else "Replies"}", style = MaterialTheme.typography.labelSmall)
                                                }
                                                TextButton(onClick = { shareComment(comment) }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) { Text("↗  Share", style = MaterialTheme.typography.labelSmall) }
                                            }
                                        }
                                    }
                                    flattenHomeReplies(comment.id, expandedReplies).forEach { visible ->
                                        val reply = visible.comment
                                        val indentation = (50 + (visible.depth - 1) * 20).dp
                                        Column(Modifier.fillMaxWidth().padding(start = indentation, top = 7.dp)) {
                                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                                val replyPhoto = authorPhotos[reply.authorUsername.removePrefix("@").trim().lowercase()]
                                                FynxRemoteProfileAvatar(replyPhoto, reply.authorDisplayName.ifBlank { reply.authorUsername }, Modifier.size(30.dp).clip(CircleShape), ownerUsername = reply.authorUsername)
                                                Spacer(Modifier.width(8.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                                        Text(reply.authorDisplayName.ifBlank { reply.authorUsername }, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                                        Spacer(Modifier.width(5.dp))
                                                        Text("@${reply.authorUsername.removePrefix("@")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                                        Text(" · ${relative(reply.timestamp)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        Spacer(Modifier.weight(1f))
                                                        val replyEngagement = commentEngagement(reply)
                                                        if (replyEngagement > 0) Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                                                            Text(compactEngagement(replyEngagement), Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                        Box {
                                                            IconButton(onClick = { openCommentMenuId = reply.id }, modifier = Modifier.size(26.dp)) {
                                                                Icon(Icons.Default.MoreHoriz, "More reply actions", modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            }
                                                            DropdownMenu(expanded = openCommentMenuId == reply.id, onDismissRequest = { openCommentMenuId = null }) {
                                                                DropdownMenuItem(
                                                                    text = { Text("Reply") },
                                                                    onClick = { openCommentMenuId = null; if (!sending) replyingToId = reply.id }
                                                                )
                                                                DropdownMenuItem(
                                                                    text = { Text("Copy reply") },
                                                                    onClick = { openCommentMenuId = null; copyCommentText(context, reply.text) }
                                                                )
                                                            }
                                                        }
                                                    }
                                                    Spacer(Modifier.height(2.dp))
                                                    Text(reply.text, style = MaterialTheme.typography.bodyMedium)
                                                    HomeCommentReactionRow(reactionStates[reply.id], reactionPending.contains(reply.id)) { reaction -> reactToComment(reply, reaction) }
                                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                                        TextButton(onClick = { if (!sending) replyingToId = reply.id }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text("↩ Reply", style = MaterialTheme.typography.labelSmall) }
                                                        TextButton(onClick = { toggleReplies(reply) }, enabled = replyLoadingId == null && !sending, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
                                                            val loadedReplyCount = expandedReplies[reply.id]?.size
                                                            Text("💬 ${if (loadedReplyCount != null) loadedReplyCount else "Replies"}", style = MaterialTheme.typography.labelSmall)
                                                        }
                                                        TextButton(onClick = { shareComment(reply) }, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) { Text("↗ Share", style = MaterialTheme.typography.labelSmall) }
                                                    }
                                                }
                                            }
                                            if (replyLoadingId == reply.id) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 2.dp))
                                            if (replyErrorId == reply.id && replyLoadingId == null) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Replies couldn't be loaded.", Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                                    TextButton(onClick = { toggleReplies(reply) }, enabled = !sending) { Text("Retry") }
                                                }
                                            }
                                        }
                                    }
                                    if (replyLoadingId == comment.id) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(start = 50.dp, top = 4.dp))
                                    if (replyErrorId == comment.id && replyLoadingId == null) {
                                        Row(Modifier.fillMaxWidth().padding(start = 50.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("Replies couldn't be loaded.", Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                                            TextButton(onClick = { toggleReplies(comment) }, enabled = !sending) { Text("Retry") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (error != null && comments.isNotEmpty() && replyErrorId == null) Text(error!!, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    HorizontalDivider()
                    if (replyingTo != null) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Text("Replying to ${replyingTo!!.authorDisplayName.ifBlank { replyingTo!!.authorUsername }}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f)); TextButton(onClick = { replyingToId = null }) { Text("Cancel") }
                    }
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it.take(MAX_COMMENT_LENGTH) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(if (replyingTo == null) "Write a comment…" else "Write a reply…") },
                                minLines = 1,
                                maxLines = 4,
                                shape = FynxDesign.ControlShape,
                                enabled = !sending && !loading,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = KeyboardType.Text, imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { send() }),
                                trailingIcon = {
                                    IconButton(onClick = { send() }, enabled = text.trim().isNotEmpty() && !sending && !loading) {
                                        if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Send, if (replyingTo == null) "Send comment" else "Send reply")
                                    }
                                }
                            )
                            Text("${text.length}/$MAX_COMMENT_LENGTH", Modifier.fillMaxWidth().padding(top = 2.dp, end = 4.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentPostEngagementAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    emoji: String? = null,
    label: String,
    count: Int?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 3.dp, vertical = 2.dp)
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(19.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (!emoji.isNullOrBlank()) {
            Text(
                emoji,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (count != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                compactEngagement(count),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

private fun copyCommentText(context: android.content.Context, value: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("FYNX comment", value))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun compactEngagement(count: Int): String = when {
    count >= 1_000_000 -> compactCommentCount(count, 1_000_000, "M")
    count >= 1_000 -> compactCommentCount(count, 1_000, "K")
    else -> count.coerceAtLeast(0).toString()
}

private fun compactCommentCount(count: Int, divisor: Int, suffix: String): String {
    val value = if (count % divisor == 0) (count / divisor).toString()
    else "%.1f".format(java.util.Locale.US, count / divisor.toDouble())
    return value + suffix
}

private fun relative(timestamp: Long): String {
    val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes((System.currentTimeMillis() - timestamp).coerceAtLeast(0L))
    return when { minutes < 1 -> "now"; minutes < 60 -> "${minutes}m"; minutes < 1440 -> "${minutes / 60}h"; else -> "${minutes / 1440}d" }
}
@Composable
private fun HomeCommentReactionRow(
    state: FynxRemoteSocialClient.CommentReactionState?,
    pending: Boolean,
    onReact: (String) -> Unit
) {
    val options = listOf(
        Triple("LOVE", "❤️", Color(0xFFE53935)),
        Triple("LAUGH", "😂", Color(0xFFFFA000)),
        Triple("THUMBS_UP", "👍", Color(0xFF1976D2)),
        Triple("WOW", "😮", Color(0xFF1976D2)),
        Triple("SAD", "😢", Color(0xFF1976D2)),
        Triple("FIRE", "🔥", Color(0xFFFF7043)),
        Triple("LIKE", "👏", Color(0xFF1976D2))
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { (type, emoji, tint) ->
            val count = state?.counts?.get(type) ?: 0
            val selected = state?.currentReaction == type
            if (count > 0 || selected || type == "LOVE") {
                Surface(
                    color = if (selected) tint.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = CircleShape
                ) {
                    TextButton(onClick = { onReact(type) }, enabled = !pending,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                        Text(if (count > 0) "$emoji $count" else emoji,
                            color = if (selected) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
