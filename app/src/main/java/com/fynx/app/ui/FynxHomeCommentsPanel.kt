package com.fynx.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private const val MAX_COMMENT_LENGTH = 1000
private const val COMMENT_PAGE_SIZE = 50

@Composable
fun FynxHomeCommentsPanel(post: FynxRemoteSocialClient.RemotePost, onClose: () -> Unit, onCommentCountChanged: (Int) -> Unit = {}) {
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
    val consumedCursors = remember(post.id) { mutableStateOf<Set<String>>(emptySet()) }
    val replyingTo = replyingToId?.let { id -> comments.firstOrNull { it.id == id } }

    fun resolveCommenterPhotos(items: List<FynxRemoteSocialClient.RemoteComment>) {
        val names = items.map { it.authorUsername.removePrefix("@").trim() }.filter { it.isNotBlank() }.distinct()
        val missing = names.filterNot { authorPhotos.containsKey(it.lowercase()) }
        if (missing.isEmpty()) return
        scope.launch {
            val resolved = mutableMapOf<String, String?>()
            missing.forEach { username ->
                val user = FynxSocialClient.searchUsers(context, username).getOrNull()?.firstOrNull { it.username.removePrefix("@").equals(username, true) }
                resolved[username.lowercase()] = user?.profilePhotoMediaId
            }
            if (resolved.isNotEmpty()) authorPhotos = authorPhotos + resolved
        }
    }
    fun resetPagingState() { nextCursor = null; consumedCursors.value = emptySet(); loadingMore = false }
    fun loadComments() {
        if (sending || loading) return
        loading = true; error = null; replyLoadingId = null; replyErrorId = null; expandedReplies = emptyMap(); resetPagingState()
        scope.launch {
            FynxRemoteSocialClient.commentsPage(context, post.id, null, COMMENT_PAGE_SIZE)
                .onSuccess { page -> comments = page.comments.distinctBy { it.id }; resolveCommenterPhotos(comments); nextCursor = page.nextCursor }
                .onFailure { failure -> comments = emptyList(); error = if (failure.message?.contains("404") == true) "This post is no longer available." else failure.message ?: "Unable to load comments." }
            loading = false
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
                    comments = (fresh + comments).distinctBy { it.id }; resolveCommenterPhotos(fresh)
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
                .onSuccess { loaded -> val unique = loaded.distinctBy { it.id }; expandedReplies = expandedReplies + mapOf(comment.id to unique); resolveCommenterPhotos(unique) }
                .onFailure { failure -> replyErrorId = comment.id; error = failure.message ?: "Unable to load replies." }
            replyLoadingId = null
        }
    }
    LaunchedEffect(post.id) { comments = emptyList(); text = ""; commentCount = post.commentCount; replyingToId = null; authorPhotos = emptyMap(); resetPagingState(); loadComments() }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = onClose)
        // Keep the entire bottom-sheet surface above the IME. Applying imePadding
        // to the outer layout moves the sheet as a unit instead of pushing only
        // the composer row, which previously allowed the typing box to sit below
        // the visible keyboard/screen boundary on some Android window sizes.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(Modifier.fillMaxSize().imePadding(), color = MaterialTheme.colorScheme.background, shape = MaterialTheme.shapes.extraLarge, tonalElevation = 8.dp) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close comments") }
                        Column(Modifier.weight(1f)) { Text("Comments", style = MaterialTheme.typography.titleLarge); Text("$commentCount comments", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        IconButton(onClick = { if (!loading && !sending && replyLoadingId == null) loadComments() }, enabled = !loading && !sending && replyLoadingId == null) { Icon(Icons.Default.Refresh, "Refresh comments") }
                    }
                    HorizontalDivider()
                    when {
                        loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        error != null && comments.isEmpty() -> Column(Modifier.fillMaxWidth().weight(1f).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(error!!, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { loadComments() }) { Text("Retry") } }
                        comments.isEmpty() -> Column(Modifier.fillMaxWidth().weight(1f).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text("No comments yet", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(6.dp)); Text("Start the conversation.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        else -> LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            if (nextCursor != null) item(key = "comments_load_more") { OutlinedButton(onClick = { loadMore() }, enabled = !loadingMore && !sending, modifier = Modifier.fillMaxWidth()) { Text(if (loadingMore) "Loading more comments…" else "Load earlier comments") } }
                            items(comments.filter { it.parentCommentId == null }, key = { it.id }) { comment ->
                                Column(Modifier.fillMaxWidth()) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                        val photo = authorPhotos[comment.authorUsername.removePrefix("@").trim().lowercase()]
                                        FynxRemoteProfileAvatar(photo, comment.authorDisplayName.ifBlank { comment.authorUsername }, Modifier.size(38.dp).clip(CircleShape)); Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(comment.authorDisplayName.ifBlank { comment.authorUsername }, style = MaterialTheme.typography.labelLarge)
                                            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(relative(comment.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                TextButton(onClick = { if (!sending) replyingToId = comment.id }) { Text("Reply") }
                                                TextButton(onClick = { toggleReplies(comment) }, enabled = replyLoadingId == null && !sending) { val loadedReplyCount = expandedReplies[comment.id]?.size; Text(if (loadedReplyCount != null) { if (loadedReplyCount == 0) "No replies" else "$loadedReplyCount replies" } else "Replies") }
                                            }
                                        }
                                    }
                                    expandedReplies[comment.id].orEmpty().forEach { reply ->
                                        Row(Modifier.fillMaxWidth().padding(start = 48.dp, top = 8.dp), verticalAlignment = Alignment.Top) {
                                            val photo = authorPhotos[reply.authorUsername.removePrefix("@").trim().lowercase()]
                                            FynxRemoteProfileAvatar(photo, reply.authorDisplayName.ifBlank { reply.authorUsername }, Modifier.size(30.dp).clip(CircleShape)); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(reply.authorDisplayName.ifBlank { reply.authorUsername }, style = MaterialTheme.typography.labelMedium); Text(reply.text, style = MaterialTheme.typography.bodyMedium); Text(relative(reply.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        }
                                    }
                                    if (replyLoadingId == comment.id) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(start = 48.dp, top = 4.dp))
                                    if (replyErrorId == comment.id && replyLoadingId == null) Row(Modifier.fillMaxWidth().padding(start = 48.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) { Text("Replies couldn't be loaded.", Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall); TextButton(onClick = { toggleReplies(comment) }, enabled = !sending) { Text("Retry") }
                                    }
                                }
                            }
                        }
                    }
                    if (error != null && comments.isNotEmpty() && replyErrorId == null) Text(error!!, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    HorizontalDivider()
                    if (replyingTo != null) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Text("Replying to ${replyingTo!!.authorDisplayName.ifBlank { replyingTo!!.authorUsername }}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f)); TextButton(onClick = { replyingToId = null }) { Text("Cancel") }
                    }
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(10.dp), verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            OutlinedTextField(value = text, onValueChange = { text = it.take(MAX_COMMENT_LENGTH) }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(if (replyingTo == null) "Write a comment…" else "Write a reply…") }, maxLines = 4, enabled = !sending && !loading, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = KeyboardType.Text, imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send() }))
                            Text("${text.length}/$MAX_COMMENT_LENGTH", Modifier.fillMaxWidth().padding(top = 2.dp, end = 4.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(8.dp)); IconButton(onClick = { send() }, enabled = text.trim().isNotEmpty() && !sending && !loading) { if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Send, "Send comment") }
                    }
                }
            }
        }
    }
}

private fun relative(timestamp: Long): String {
    val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes((System.currentTimeMillis() - timestamp).coerceAtLeast(0L))
    return when { minutes < 1 -> "now"; minutes < 60 -> "${minutes}m"; minutes < 1440 -> "${minutes / 60}h"; else -> "${minutes / 1440}d" }
}