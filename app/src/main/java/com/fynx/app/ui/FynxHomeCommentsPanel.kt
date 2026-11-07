package com.fynx.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
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
private const val MAX_COMMENT_REPLY_DEPTH = 5

private data class VisibleHomeReply(val comment: FynxRemoteSocialClient.RemoteComment, val depth: Int)

private fun flattenHomeReplies(
    parentId: String,
    expandedReplies: Map<String, List<FynxRemoteSocialClient.RemoteComment>>,
    depth: Int = 1,
    ancestors: Set<String> = emptySet()
): List<VisibleHomeReply> {
    if (depth > MAX_COMMENT_REPLY_DEPTH || parentId in ancestors) return emptyList()
    val nextAncestors = ancestors + parentId
    return expandedReplies[parentId].orEmpty().flatMap { child ->
        listOf(VisibleHomeReply(child, depth)) +
            flattenHomeReplies(child.id, expandedReplies, depth + 1, nextAncestors)
    }
}

@Composable
fun FynxHomeCommentsPanel(post: FynxRemoteSocialClient.RemotePost, initialCommentId: String? = null, onClose: () -> Unit, onCommentCountChanged: (Int) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val isLocalPreview = remember(post.id) {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 && post.id.startsWith("ci-home-post-")
    }
    var comments by remember(post.id) { mutableStateOf<List<FynxRemoteSocialClient.RemoteComment>>(emptyList()) }
    var text by rememberSaveable(post.id) { mutableStateOf("") }
    var aiCommentBusy by remember(post.id) { mutableStateOf(false) }
    var aiCommentError by remember(post.id) { mutableStateOf<String?>(null) }
    var aiCommentSuggestion by remember(post.id) { mutableStateOf<String?>(null) }
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
    var commentEngagementStates by remember(post.id) { mutableStateOf<Map<String, FynxRemoteSocialClient.CommentEngagementState>>(emptyMap()) }
    var engagementPending by remember(post.id) { mutableStateOf<Set<String>>(emptySet()) }
    var viewedCommentIds by remember(post.id) { mutableStateOf<Set<String>>(emptySet()) }
    var selectedMediaUri by remember(post.id) { mutableStateOf<Uri?>(null) }
    var selectedMediaKind by remember(post.id) { mutableStateOf<String?>(null) }
    var linkedVideoPostId by remember(post.id) { mutableStateOf<String?>(null) }
    var linkVideoDialogOpen by remember(post.id) { mutableStateOf(false) }
    var linkVideoPostIdDraft by remember(post.id) { mutableStateOf("") }
    var linkVideoError by remember(post.id) { mutableStateOf<String?>(null) }
    var mediaPreview by remember(post.id) { mutableStateOf<Triple<String, String?, String>?>(null) }
    var linkedVideoLoadingId by remember(post.id) { mutableStateOf<String?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedMediaUri = uri
        selectedMediaKind = if (uri == null) null else "image"
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedMediaUri = uri
        selectedMediaKind = if (uri == null) null else "video"
    }
    var commentSort by rememberSaveable(post.id) { mutableStateOf("Relevant") }
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

    fun loadCommentEngagement(items: List<FynxRemoteSocialClient.RemoteComment>) {
        if (isLocalPreview) return
        val ids = items.map { it.id }.distinct()
        if (ids.isEmpty()) return
        scope.launch {
            FynxRemoteSocialClient.commentEngagement(context, post.id, ids).onSuccess { loaded ->
                commentEngagementStates = commentEngagementStates + loaded
            }
        }
    }
    fun recordCommentView(comment: FynxRemoteSocialClient.RemoteComment) {
        if (isLocalPreview || comment.id in viewedCommentIds) return
        viewedCommentIds = viewedCommentIds + comment.id
        scope.launch {
            FynxRemoteSocialClient.recordCommentView(context, post.id, comment.id)
                .onSuccess { count ->
                    val previous = commentEngagementStates[comment.id] ?: FynxRemoteSocialClient.CommentEngagementState()
                    commentEngagementStates = commentEngagementStates + (comment.id to previous.copy(viewCount = count))
                }
                .onFailure { viewedCommentIds = viewedCommentIds - comment.id }
        }
    }
    fun toggleCommentRepost(comment: FynxRemoteSocialClient.RemoteComment) {
        if (isLocalPreview || comment.id in engagementPending) return
        val previous = commentEngagementStates[comment.id] ?: FynxRemoteSocialClient.CommentEngagementState()
        val desired = !previous.reposted
        commentEngagementStates = commentEngagementStates + (comment.id to previous.copy(reposted = desired, repostCount = (previous.repostCount + if (desired) 1 else -1).coerceAtLeast(0)))
        engagementPending = engagementPending + comment.id
        scope.launch {
            FynxRemoteSocialClient.setCommentRepost(context, post.id, comment.id, desired)
                .onSuccess { (enabled, count) ->
                    val current = commentEngagementStates[comment.id] ?: previous
                    commentEngagementStates = commentEngagementStates + (comment.id to current.copy(reposted = enabled, repostCount = count))
                }
                .onFailure { failure ->
                    commentEngagementStates = commentEngagementStates + (comment.id to previous)
                    error = failure.message ?: "Comment repost could not be updated."
                }
            engagementPending = engagementPending - comment.id
        }
    }
    fun toggleCommentBookmark(comment: FynxRemoteSocialClient.RemoteComment) {
        if (isLocalPreview || comment.id in engagementPending) return
        val previous = commentEngagementStates[comment.id] ?: FynxRemoteSocialClient.CommentEngagementState()
        val desired = !previous.bookmarked
        commentEngagementStates = commentEngagementStates + (comment.id to previous.copy(bookmarked = desired, bookmarkCount = (previous.bookmarkCount + if (desired) 1 else -1).coerceAtLeast(0)))
        engagementPending = engagementPending + comment.id
        scope.launch {
            FynxRemoteSocialClient.setCommentBookmark(context, post.id, comment.id, desired)
                .onSuccess { (enabled, count) ->
                    val current = commentEngagementStates[comment.id] ?: previous
                    commentEngagementStates = commentEngagementStates + (comment.id to current.copy(bookmarked = enabled, bookmarkCount = count))
                }
                .onFailure { failure ->
                    commentEngagementStates = commentEngagementStates + (comment.id to previous)
                    error = failure.message ?: "Comment bookmark could not be updated."
                }
            engagementPending = engagementPending - comment.id
        }
    }
    fun openLinkedVideo(comment: FynxRemoteSocialClient.RemoteComment) {
        if (comment.linkedPostId.isNullOrBlank() || linkedVideoLoadingId != null) return
        linkedVideoLoadingId = comment.id
        scope.launch {
            FynxRemoteSocialClient.linkedVideo(context, post.id, comment.id)
                .onSuccess { video -> mediaPreview = Triple(video.mediaUrl, video.mediaType, "Part 2 video by ${video.authorDisplayName.ifBlank { video.authorUsername }}") }
                .onFailure { failure -> error = failure.message ?: "The linked FYNX video is unavailable." }
            linkedVideoLoadingId = null
        }
    }
    fun loadReactions(items: List<FynxRemoteSocialClient.RemoteComment>) {
        loadCommentEngagement(items)
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
        if (isLocalPreview) return
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
        if (isLocalPreview) {
            val now = System.currentTimeMillis()
            val first = FynxRemoteSocialClient.RemoteComment("ci-comment-1", "Great portrait fit. ❤️", now - 8 * 60_000L, "ci-author-1", "@amara", "Amara Okafor")
            val second = FynxRemoteSocialClient.RemoteComment("ci-comment-2", "Full-screen playback feels smooth.", now - 5 * 60_000L, "ci-author-2", "@david", "David James")
            val reply = FynxRemoteSocialClient.RemoteComment("ci-reply-1", "Agreed — the feed stays uncluttered.", now - 3 * 60_000L, "ci-author-3", "@zara", "Zara Bello", first.id)
            val nestedReply = FynxRemoteSocialClient.RemoteComment("ci-reply-2", "And it opens the same clip.", now - 2 * 60_000L, "ci-author-4", "@leo", "Leo Mensah", reply.id)
            val thirdLevelReply = FynxRemoteSocialClient.RemoteComment("ci-reply-3", "Easy to follow.", now - 1 * 60_000L, "ci-author-5", "@nina", "Nina James", nestedReply.id)
            val previewComments = listOf(first, second, reply, nestedReply, thirdLevelReply)
            comments = listOf(first, second)
            expandedReplies = mapOf(
                first.id to listOf(reply),
                reply.id to listOf(nestedReply),
                nestedReply.id to listOf(thirdLevelReply)
            )
            reactionStates = mapOf(
                first.id to FynxRemoteSocialClient.CommentReactionState(mapOf("LOVE" to 12, "THUMBS_UP" to 5, "LAUGH" to 3), "LOVE"),
                second.id to FynxRemoteSocialClient.CommentReactionState(mapOf("THUMBS_UP" to 8, "WOW" to 2), null),
                reply.id to FynxRemoteSocialClient.CommentReactionState(mapOf("LOVE" to 4), null),
                nestedReply.id to FynxRemoteSocialClient.CommentReactionState(mapOf("THUMBS_UP" to 2), null),
                thirdLevelReply.id to FynxRemoteSocialClient.CommentReactionState(mapOf("LAUGH" to 1), null)
            )
            commentEngagementStates = mapOf(
                first.id to FynxRemoteSocialClient.CommentEngagementState(replyCount = 1, repostCount = 3, viewCount = 56),
                second.id to FynxRemoteSocialClient.CommentEngagementState(viewCount = 36),
                reply.id to FynxRemoteSocialClient.CommentEngagementState(replyCount = 1, viewCount = 56),
                nestedReply.id to FynxRemoteSocialClient.CommentEngagementState(replyCount = 1, viewCount = 36),
                thirdLevelReply.id to FynxRemoteSocialClient.CommentEngagementState(viewCount = 24)
            )
            commentCount = maxOf(post.commentCount, previewComments.size)
            onCommentCountChanged(commentCount)
            // Local preview authors intentionally use initials; avoid all profile/backend lookups.
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
        val value = text.trim(); val hasAttachment = selectedMediaUri != null || !linkedVideoPostId.isNullOrBlank(); if ((value.isEmpty() && !hasAttachment) || sending || loading || value.length > MAX_COMMENT_LENGTH) return
        val parentId = replyingToId
        if (isLocalPreview) {
            if (hasAttachment) { error = "Media uploads and linked videos are disabled in the offline visual fixture."; return }
            val now = System.currentTimeMillis()
            val parentDepth = if (parentId == null || comments.any { it.id == parentId }) 0
                else comments.asSequence()
                    .flatMap { root -> flattenHomeReplies(root.id, expandedReplies).asSequence() }
                    .firstOrNull { it.comment.id == parentId }?.depth ?: 0
            if (parentId != null && parentDepth >= MAX_COMMENT_REPLY_DEPTH) {
                error = "Replies are limited to five levels."
                return
            }
            val previewComment = FynxRemoteSocialClient.RemoteComment(
                "ci-local-$now", value, now, "ci-local-author", "@fynx_preview", "FYNX Preview", parentId
            )
            if (parentId == null) {
                comments = comments + previewComment
            } else {
                expandedReplies = expandedReplies + (parentId to (expandedReplies[parentId].orEmpty() + previewComment))
            }
            expandedReplies = expandedReplies + (previewComment.id to emptyList())
            commentCount += 1
            onCommentCountChanged(commentCount)
            text = ""
            replyingToId = null
            error = null
            return
        }
        sending = true; error = null; replyErrorId = null
        scope.launch {
            val result = if (selectedMediaUri != null || !linkedVideoPostId.isNullOrBlank()) {
                FynxRemoteSocialClient.addCommentExperience(context, post.id, value, parentId, selectedMediaUri, selectedMediaKind, linkedVideoPostId)
            } else if (parentId != null) FynxRemoteSocialClient.addReply(context, post.id, parentId, value) else FynxRemoteSocialClient.addComment(context, post.id, value)
            result.onSuccess { comment ->
                comments = (comments + comment).distinctBy { it.id }; resolveCommenterPhotos(listOf(comment)); commentCount += 1; onCommentCountChanged(commentCount)
                if (comment.parentCommentId != null) { val parent = comment.parentCommentId!!; expandedReplies = expandedReplies + mapOf(parent to ((expandedReplies[parent].orEmpty() + comment).distinctBy { it.id })) }
                text = ""; aiCommentSuggestion = null; aiCommentError = null; replyingToId = null; selectedMediaUri = null; selectedMediaKind = null; linkedVideoPostId = null
                if (comment.parentCommentId == null) scope.launch { val topLevelCount = comments.count { it.parentCommentId == null }; listState.animateScrollToItem((topLevelCount - 1 + if (nextCursor != null) 1 else 0).coerceAtLeast(0)) }
            }.onFailure { failure -> if (parentId != null) replyErrorId = parentId; error = failure.message ?: if (parentId != null) "Unable to send reply." else "Unable to send comment." }
            sending = false
        }
    }
    fun toggleReplies(comment: FynxRemoteSocialClient.RemoteComment) {
        if (replyLoadingId != null || sending) return
        if (expandedReplies.containsKey(comment.id)) { expandedReplies = expandedReplies - comment.id; replyErrorId = null; return }
        if (isLocalPreview) {
            expandedReplies = expandedReplies + (comment.id to emptyList())
            replyErrorId = null
            return
        }
        replyLoadingId = comment.id; replyErrorId = null; error = null
        scope.launch {
            FynxRemoteSocialClient.replies(context, post.id, comment.id)
                .onSuccess { loaded -> val unique = loaded.distinctBy { it.id }; expandedReplies = expandedReplies + mapOf(comment.id to unique); resolveCommenterPhotos(unique); loadReactions(unique) }
                .onFailure { failure -> replyErrorId = comment.id; error = failure.message ?: "Unable to load replies." }
            replyLoadingId = null
        }
    }
    LaunchedEffect(post.id) { comments = emptyList(); text = ""; aiCommentSuggestion = null; aiCommentError = null; commentCount = post.commentCount; replyingToId = null; authorPhotos = emptyMap(); commentEngagementStates = emptyMap(); viewedCommentIds = emptySet(); resetPagingState(); loadComments() }

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
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text("POST CONVERSATION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(
                            post.text.ifBlank { "Join the conversation and share a thoughtful reply." },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Started by ${post.authorDisplayName.ifBlank { post.authorUsername }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                            Text(comment.authorDisplayName.ifBlank { comment.authorUsername }, style = MaterialTheme.typography.labelLarge)
                                            Spacer(Modifier.height(3.dp))
                                            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                                            comment.mediaUrl?.let { mediaPath ->
                                                Card(
                                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable {
                                                        mediaPreview = Triple(mediaPath, comment.mediaType, if (comment.mediaType == "video") "Video comment" else "Photo comment")
                                                    },
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
                                                ) {
                                                    Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Text(if (comment.mediaType == "video") "VIDEO COMMENT" else "PHOTO COMMENT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                        RemoteSocialMedia(mediaPath, comment.mediaType, playbackActive = false)
                                                    }
                                                }
                                            }
                                            comment.linkedPostId?.let {
                                                TextButton(onClick = { openLinkedVideo(comment) }, enabled = linkedVideoLoadingId != comment.id) {
                                                    Icon(Icons.Default.VideoLibrary, contentDescription = null)
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(if (linkedVideoLoadingId == comment.id) "Opening Part 2…" else "Watch linked FYNX video")
                                                }
                                            }
                                            HomeCommentActionBar(
                                                reaction = reactionStates[comment.id],
                                                engagement = commentEngagementStates[comment.id],
                                                pending = reactionPending.contains(comment.id) || engagementPending.contains(comment.id),
                                                onExpandReplies = { toggleReplies(comment) },
                                                onRepost = { toggleCommentRepost(comment) },
                                                onReact = { reaction -> reactToComment(comment, reaction) },
                                                onBookmark = { toggleCommentBookmark(comment) },
                                                onShare = { shareComment(comment) }
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(relative(comment.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                TextButton(onClick = { if (!sending) replyingToId = comment.id }) { Text("Reply") }
                                            }
                                            if (!isLocalPreview) LaunchedEffect(comment.id) { recordCommentView(comment) }
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
                                                    Text(reply.authorDisplayName.ifBlank { reply.authorUsername }, style = MaterialTheme.typography.labelMedium)
                                                    Spacer(Modifier.height(2.dp))
                                                    Text(reply.text, style = MaterialTheme.typography.bodyMedium)
                                                    reply.mediaUrl?.let { mediaPath ->
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable {
                                                                mediaPreview = Triple(mediaPath, reply.mediaType, if (reply.mediaType == "video") "Video comment" else "Photo comment")
                                                            },
                                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
                                                        ) {
                                                            Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                                Text(if (reply.mediaType == "video") "VIDEO COMMENT" else "PHOTO COMMENT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                                RemoteSocialMedia(mediaPath, reply.mediaType, playbackActive = false)
                                                            }
                                                        }
                                                    }
                                                    reply.linkedPostId?.let {
                                                        TextButton(onClick = { openLinkedVideo(reply) }, enabled = linkedVideoLoadingId != reply.id) {
                                                            Icon(Icons.Default.VideoLibrary, contentDescription = null)
                                                            Spacer(Modifier.width(6.dp))
                                                            Text(if (linkedVideoLoadingId == reply.id) "Opening Part 2…" else "Watch linked FYNX video")
                                                        }
                                                    }
                                                    HomeCommentActionBar(
                                                        reaction = reactionStates[reply.id],
                                                        engagement = commentEngagementStates[reply.id],
                                                        pending = reactionPending.contains(reply.id) || engagementPending.contains(reply.id),
                                                        onExpandReplies = { toggleReplies(reply) },
                                                        onRepost = { toggleCommentRepost(reply) },
                                                        onReact = { reaction -> reactToComment(reply, reaction) },
                                                        onBookmark = { toggleCommentBookmark(reply) },
                                                        onShare = { shareComment(reply) }
                                                    )
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(relative(reply.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        if (visible.depth < MAX_COMMENT_REPLY_DEPTH) {
                                                            TextButton(onClick = { if (!sending) replyingToId = reply.id }) { Text("Reply") }
                                                        }
                                                    }
                                                    if (!isLocalPreview) LaunchedEffect(reply.id) { recordCommentView(reply) }
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
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(
                                onClick = {
                                    val requestedText = text.trim()
                                    if (requestedText.isNotBlank()) {
                                        aiCommentBusy = true
                                        aiCommentError = null
                                        aiCommentSuggestion = null
                                        scope.launch {
                                            AiAssistantClient.improveCommentText(context, requestedText, replyingTo != null)
                                                .onSuccess { suggestion ->
                                                    if (text.trim() == requestedText) aiCommentSuggestion = suggestion.trim().take(MAX_COMMENT_LENGTH)
                                                    else aiCommentError = "Your comment changed while FYNX AI was preparing it. Please try again."
                                                }
                                                .onFailure { failure ->
                                                    aiCommentError = failure.message?.takeIf { it.isNotBlank() }
                                                        ?: "FYNX AI could not improve this comment. Please try again."
                                                }
                                            aiCommentBusy = false
                                        }
                                    }
                                },
                                enabled = text.isNotBlank() && !sending && !loading && !aiCommentBusy && !isLocalPreview
                            ) {
                                if (aiCommentBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                else Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (aiCommentBusy) "Improving…" else "Improve with AI")
                            }
                        }
                        aiCommentError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        aiCommentSuggestion?.takeIf { it.isNotBlank() }?.let { suggestion ->
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("FYNX AI suggestion", style = MaterialTheme.typography.titleSmall)
                                    }
                                    Text(suggestion, style = MaterialTheme.typography.bodyMedium)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = { aiCommentSuggestion = null }) { Text("Keep my draft") }
                                        Button(onClick = {
                                            text = suggestion.take(MAX_COMMENT_LENGTH)
                                            aiCommentSuggestion = null
                                            aiCommentError = null
                                        }) { Text("Use suggestion") }
                                    }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { photoPicker.launch("image/*") }, enabled = !sending && !loading && !isLocalPreview) {
                                Icon(Icons.Default.Image, contentDescription = "Attach photo", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { videoPicker.launch("video/*") }, enabled = !sending && !loading && !isLocalPreview) {
                                Icon(Icons.Default.VideoLibrary, contentDescription = "Attach video", tint = MaterialTheme.colorScheme.primary)
                            }
                            TextButton(onClick = { linkVideoPostIdDraft = ""; linkVideoError = null; linkVideoDialogOpen = true }, enabled = !sending && !loading && !isLocalPreview) {
                                Icon(Icons.Default.Link, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Link Part 2")
                            }
                        }
                        if (selectedMediaUri != null) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (selectedMediaKind == "video") "Video attached" else "Photo attached", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                TextButton(onClick = { selectedMediaUri = null; selectedMediaKind = null }) { Text("Remove") }
                            }
                        }
                        if (!linkedVideoPostId.isNullOrBlank()) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Part 2 video linked", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                TextButton(onClick = { linkedVideoPostId = null }) { Text("Remove") }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                            OutlinedTextField(
                                value = text,
                                onValueChange = { value ->
                                    if (value != text) {
                                        aiCommentSuggestion = null
                                        aiCommentError = null
                                    }
                                    text = value.take(MAX_COMMENT_LENGTH)
                                },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(if (replyingTo == null) "Write a comment…" else "Write a reply…") },
                                minLines = 1,
                                maxLines = 4,
                                shape = FynxDesign.ControlShape,
                                enabled = !sending && !loading,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = KeyboardType.Text, imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { send() }),
                                trailingIcon = {
                                    IconButton(onClick = { send() }, enabled = (text.trim().isNotEmpty() || selectedMediaUri != null || !linkedVideoPostId.isNullOrBlank()) && !sending && !loading) {
                                        if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Send, if (replyingTo == null) "Send comment" else "Send reply")
                                    }
                                }
                            )
                        }
                        Text("${text.length}/$MAX_COMMENT_LENGTH", Modifier.fillMaxWidth().padding(top = 2.dp, end = 4.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (linkVideoDialogOpen) {
        AlertDialog(
            onDismissRequest = { linkVideoDialogOpen = false },
            title = { Text("Link a Part 2 video") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste the FYNX video post link or enter its post ID. FYNX checks that the video is visible to this audience.")
                    OutlinedTextField(value = linkVideoPostIdDraft, onValueChange = { linkVideoPostIdDraft = it.take(500); linkVideoError = null },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("FYNX video link or post ID") }, isError = linkVideoError != null)
                    linkVideoError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val digits = Regex("(\\d+)(?:[/?#].*)?$").find(linkVideoPostIdDraft.trim())?.groupValues?.get(1)
                    if (digits.isNullOrBlank()) linkVideoError = "Enter a valid FYNX video link or numeric post ID."
                    else { linkedVideoPostId = digits; linkVideoDialogOpen = false }
                }) { Text("Attach video") }
            },
            dismissButton = { TextButton(onClick = { linkVideoDialogOpen = false }) { Text("Cancel") } }
        )
    }
    mediaPreview?.let { (path, type, title) ->
        AlertDialog(
            onDismissRequest = { mediaPreview = null },
            title = { Text(title) },
            text = { Column { RemoteSocialMedia(path, type, playbackActive = true) } },
            confirmButton = { TextButton(onClick = { mediaPreview = null }) { Text("Close") } }
        )
    }
}

private fun relative(timestamp: Long): String {
    val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes((System.currentTimeMillis() - timestamp).coerceAtLeast(0L))
    return when { minutes < 1 -> "now"; minutes < 60 -> "${minutes}m"; minutes < 1440 -> "${minutes / 60}h"; else -> "${minutes / 1440}d" }
}
@Composable
private fun HomeCommentActionBar(
    reaction: FynxRemoteSocialClient.CommentReactionState?,
    engagement: FynxRemoteSocialClient.CommentEngagementState?,
    pending: Boolean,
    onExpandReplies: () -> Unit,
    onRepost: () -> Unit,
    onReact: (String) -> Unit,
    onBookmark: () -> Unit,
    onShare: () -> Unit
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val totalReactions = reaction?.counts?.values?.sum() ?: 0
    val activeEmoji = when (reaction?.currentReaction) {
        "LOVE" -> "❤️"; "LAUGH" -> "😂"; "THUMBS_UP", "LIKE" -> "👍"; "WOW" -> "😮"; "SAD" -> "😢"; "FIRE" -> "🔥"; else -> null
    }
    val choices = listOf(
        Triple("LOVE", "❤️", "Love"), Triple("LAUGH", "😂", "Funny"), Triple("THUMBS_UP", "👍", "Like"),
        Triple("WOW", "😮", "Wow"), Triple("SAD", "😢", "Sad"), Triple("FIRE", "🔥", "Fire")
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onExpandReplies, enabled = !pending, contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)) {
            Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Open replies", modifier = Modifier.size(18.dp))
            if ((engagement?.replyCount ?: 0) > 0) Text(" ${engagement?.replyCount}", style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = onRepost, enabled = !pending, contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)) {
            Icon(Icons.Default.Repeat, contentDescription = "Repost comment", modifier = Modifier.size(18.dp), tint = if (engagement?.reposted == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            if ((engagement?.repostCount ?: 0) > 0) Text(" ${engagement?.repostCount}", style = MaterialTheme.typography.labelSmall)
        }
        Box {
            Row(
                modifier = Modifier.combinedClickable(enabled = !pending, onClick = { onReact("LOVE") }, onLongClick = { pickerOpen = true })
                    .padding(horizontal = 5.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (activeEmoji == null) Icon(Icons.Default.FavoriteBorder, contentDescription = "Like comment; hold for reactions", modifier = Modifier.size(19.dp))
                else Text(activeEmoji, style = MaterialTheme.typography.titleSmall)
                if (totalReactions > 0) Text(" $totalReactions", style = MaterialTheme.typography.labelSmall)
            }
            if (pickerOpen) {
                androidx.compose.ui.window.Popup(
                    alignment = Alignment.TopCenter,
                    onDismissRequest = { pickerOpen = false }
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 14.dp,
                        tonalElevation = 5.dp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            choices.forEach { (type, emoji, label) ->
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable(enabled = !pending) {
                                            pickerOpen = false
                                            onReact(type)
                                        }
                                        .padding(horizontal = 7.dp, vertical = 8.dp)
                                ) {
                                    Text(emoji, style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 2.dp, vertical = 8.dp)) {
            Icon(Icons.Default.BarChart, contentDescription = "Comment views", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if ((engagement?.viewCount ?: 0) > 0) Text(" ${engagement?.viewCount}", style = MaterialTheme.typography.labelSmall)
        }
        TextButton(onClick = onBookmark, enabled = !pending, contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)) {
            Icon(if (engagement?.bookmarked == true) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = "Bookmark comment", modifier = Modifier.size(18.dp),
                tint = if (engagement?.bookmarked == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onShare, enabled = !pending, contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp)) {
            Icon(Icons.Default.Share, contentDescription = "Share comment", modifier = Modifier.size(18.dp))
        }
    }
}
