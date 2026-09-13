package com.fynx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

@Composable
fun FynxHomeCommentsPanel(
    post: FynxRemoteSocialClient.RemotePost,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var comments by remember(post.id) { mutableStateOf<List<FynxRemoteSocialClient.RemoteComment>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var loading by remember(post.id) { mutableStateOf(true) }
    var sending by remember(post.id) { mutableStateOf(false) }
    var error by remember(post.id) { mutableStateOf<String?>(null) }

    fun loadComments() {
        loading = true
        error = null
        scope.launch {
            FynxRemoteSocialClient.comments(context, post.id)
                .onSuccess { comments = it }
                .onFailure { error = it.message ?: "Unable to load comments." }
            loading = false
        }
    }

    LaunchedEffect(post.id) { loadComments() }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close comments") }
                    Column(Modifier.weight(1f)) {
                        Text("Comments", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${post.commentCount} comments",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { loadComments() }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, "Refresh comments")
                    }
                }
                HorizontalDivider()

                when {
                    loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    error != null -> Column(
                        Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(error!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { loadComments() }) { Text("Retry") }
                    }
                    comments.isEmpty() -> Column(
                        Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No comments yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("Start the conversation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> LazyColumn(
                        Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(comments, key = { it.id }) { comment ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                Surface(
                                    Modifier.size(38.dp).clip(CircleShape),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {}
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        comment.authorDisplayName.ifBlank { comment.authorUsername },
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        relative(comment.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().imePadding().padding(10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(1000) },
                        Modifier.weight(1f),
                        placeholder = { Text("Write a comment…") },
                        maxLines = 4,
                        enabled = !sending,
                        singleLine = false
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val trimmed = text.trim()
                            if (trimmed.isEmpty() || sending) return@IconButton
                            sending = true
                            scope.launch {
                                FynxRemoteSocialClient.addComment(context, post.id, trimmed)
                                    .onSuccess { comment -> comments = comments + comment; text = "" }
                                    .onFailure { error = it.message ?: "Unable to send comment." }
                                sending = false
                            }
                        },
                        enabled = text.trim().isNotEmpty() && !sending
                    ) {
                        Icon(Icons.Default.Send, "Send comment")
                    }
                }
            }
        }
    }
}

private fun relative(timestamp: Long): String {
    val minutes = java.util.concurrent.TimeUnit.MILLISECONDS
        .toMinutes((System.currentTimeMillis() - timestamp).coerceAtLeast(0L))
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 1440 -> "${minutes / 60}h"
        else -> "${minutes / 1440}d"
    }
}
