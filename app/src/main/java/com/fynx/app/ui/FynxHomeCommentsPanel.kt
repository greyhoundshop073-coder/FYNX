package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch

private const val MAX_COMMENT_LENGTH = 1000
private const val COMMENT_PAGE_SIZE = 50

@Composable
fun FynxHomeCommentsPanel(post: FynxRemoteSocialClient.RemotePost, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var comments by remember(post.id) { mutableStateOf<List<FynxRemoteSocialClient.RemoteComment>>(emptyList()) }
    var text by remember(post.id) { mutableStateOf("") }
    var loading by remember(post.id) { mutableStateOf(true) }
    var loadingMore by remember(post.id) { mutableStateOf(false) }
    var sending by remember(post.id) { mutableStateOf(false) }
    var error by remember(post.id) { mutableStateOf<String?>(null) }
    var nextCursor by remember(post.id) { mutableStateOf<String?>(null) }
    var replyingTo by remember(post.id) { mutableStateOf<FynxRemoteSocialClient.RemoteComment?>(null) }
    var replyLoadingId by remember(post.id) { mutableStateOf<String?>(null) }
    var expandedReplies by remember(post.id) { mutableStateOf<Map<String, List<FynxRemoteSocialClient.RemoteComment>>>(emptyMap()) }

    fun loadComments() {
        loading = true; error = null
        scope.launch {
            FynxRemoteSocialClient.commentsPage(context, post.id, null, COMMENT_PAGE_SIZE)
                .onSuccess { page -> comments = page.comments.distinctBy { it.id }; nextCursor = page.nextCursor }
                .onFailure { error = it.message ?: "Unable to load comments." }
            loading = false
        }
    }
    fun loadMore() {
        val cursor = nextCursor ?: return
        if (loadingMore || loading) return
        loadingMore = true
        scope.launch {
            FynxRemoteSocialClient.commentsPage(context, post.id, cursor, COMMENT_PAGE_SIZE)
                .onSuccess { page -> comments = (page.comments + comments).distinctBy { it.id }; nextCursor = page.nextCursor }
                .onFailure { error = it.message ?: "Unable to load more comments." }
            loadingMore = false
        }
    }
    fun send() {
        val value = text.trim()
        if (value.isEmpty() || sending || value.length > MAX_COMMENT_LENGTH) return
        sending = true; error = null
        scope.launch {
            val result = replyingTo?.let { FynxRemoteSocialClient.addReply(context, post.id, it.id, value) } ?: FynxRemoteSocialClient.addComment(context, post.id, value)
            result.onSuccess { comment ->
                comments = (comments + comment).distinctBy { it.id }
                if (comment.parentCommentId != null) {
                    val parent = comment.parentCommentId!!
                    expandedReplies = expandedReplies + mapOf(parent to ((expandedReplies[parent].orEmpty() + comment).distinctBy { it.id }))
                }
                text = ""; replyingTo = null
                scope.launch { if (comments.isNotEmpty()) listState.animateScrollToItem(comments.lastIndex) }
            }.onFailure { error = it.message ?: "Unable to send comment." }
            sending = false
        }
    }
    fun toggleReplies(comment: FynxRemoteSocialClient.RemoteComment) {
        if (expandedReplies.containsKey(comment.id)) { expandedReplies = expandedReplies - comment.id; return }
        replyLoadingId = comment.id
        scope.launch {
            FynxRemoteSocialClient.replies(context, post.id, comment.id)
                .onSuccess { loaded -> expandedReplies = expandedReplies + mapOf(comment.id to loaded.distinctBy { it.id }) }
                .onFailure { error = it.message ?: "Unable to load replies." }
            replyLoadingId = null
        }
    }

    LaunchedEffect(post.id) { loadComments() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close comments") }
                    Column(Modifier.weight(1f)) {
                        Text("Comments", style = MaterialTheme.typography.titleLarge)
                        Text("${comments.size.coerceAtLeast(post.commentCount)} comments", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { if (!loading && !sending) loadComments() }, enabled = !loading && !sending) { Icon(Icons.Default.Refresh, "Refresh comments") }
                }
                HorizontalDivider()
                when {
                    loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    error != null && comments.isEmpty() -> Column(Modifier.fillMaxWidth().weight(1f).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(error!!, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { loadComments() }) { Text("Retry") } }
                    comments.isEmpty() -> Column(Modifier.fillMaxWidth().weight(1f).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text("No comments yet", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(6.dp)); Text("Start the conversation.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    else -> LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (nextCursor != null) item(key = "comments_load_more") { OutlinedButton(onClick = { loadMore() }, enabled = !loadingMore, modifier = Modifier.fillMaxWidth()) { Text(if (loadingMore) "Loading more comments…" else "Load earlier comments") } }
                        items(comments.filter { it.parentCommentId == null }, key = { it.id }) { comment ->
                            Column(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                    Surface(Modifier.size(38.dp).clip(CircleShape), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {}
                                    Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) {
                                        Text(comment.authorDisplayName.ifBlank { comment.authorUsername }, style = MaterialTheme.typography.labelLarge)
                                        Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(relative(comment.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            TextButton(onClick = { replyingTo = comment }) { Text("Reply") }
                                            TextButton(onClick = { toggleReplies(comment) }) { Text(if (expandedReplies.containsKey(comment.id)) "Hide replies" else "Replies") }
                                        }
                                    }
                                }
                                expandedReplies[comment.id].orEmpty().forEach { reply ->
                                    Row(Modifier.fillMaxWidth().padding(48.dp, 8.dp, 0.dp, 0.dp), verticalAlignment = Alignment.Top) {
                                        Surface(Modifier.size(30.dp).clip(CircleShape), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {}
                                        Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) {
                                            Text(reply.authorDisplayName.ifBlank { reply.authorUsername }, style = MaterialTheme.typography.labelMedium)
                                            Text(reply.text, style = MaterialTheme.typography.bodyMedium)
                                            Text(relative(reply.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                if (replyLoadingId == comment.id) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(48.dp, 4.dp, 0.dp, 0.dp))
                            }
                        }
                    }
                }
                if (error != null && comments.isNotEmpty()) Text(error!!, modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp, 16.dp, 4.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                HorizontalDivider()
                if (replyingTo != null) Row(Modifier.fillMaxWidth().padding(12.dp, 6.dp, 12.dp, 0.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Replying to ${replyingTo!!.authorDisplayName.ifBlank { replyingTo!!.authorUsername }}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { replyingTo = null }) { Text("Cancel") }
                }
                Row(Modifier.fillMaxWidth().imePadding().padding(10.dp), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        OutlinedTextField(value = text, onValueChange = { text = it.take(MAX_COMMENT_LENGTH) }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(if (replyingTo == null) "Write a comment…" else "Write a reply…") }, maxLines = 4, enabled = !sending, singleLine = false, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, keyboardType = KeyboardType.Text, imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send() }))
                        Text("${text.length}/$MAX_COMMENT_LENGTH", modifier = Modifier.fillMaxWidth().padding(0.dp, 2.dp, 4.dp, 0.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp)); IconButton(onClick = { send() }, enabled = text.trim().isNotEmpty() && !sending) { if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Send, "Send comment") }
                }
            }
        }
    }
}
private fun relative(timestamp: Long): String { val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes((System.currentTimeMillis() - timestamp).coerceAtLeast(0L)); return when { minutes < 1 -> "now"; minutes < 60 -> "${minutes}m"; minutes < 1440 -> "${minutes / 60}h"; else -> "${minutes / 1440}d" } }
