package com.fynx.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class FynxPostViewerItem(val id: String, val mediaType: String, val position: Int, val mediaUrl: String)

@Composable
fun FynxPostMediaViewer(context: Context, post: FynxRemoteSocialClient.RemotePost, media: List<FynxPostViewerItem>, initialIndex: Int = 0, onDismiss: () -> Unit) {
    if (media.isEmpty()) return
    val pager = rememberPagerState(initialPage = initialIndex.coerceIn(0, media.lastIndex), pageCount = { media.size })
    val scope = rememberCoroutineScope()
    var liked by remember(post.id) { mutableStateOf(post.likedByCurrentUser) }
    var likes by remember(post.id) { mutableIntStateOf(post.likeCount) }
    var saved by remember(post.id) { mutableStateOf(false) }
    var reposted by remember(post.id) { mutableStateOf(false) }
    var comments by remember(post.id) { mutableIntStateOf(post.commentCount) }
    var busy by remember(post.id) { mutableStateOf(false) }
    var commentsOpen by remember(post.id) { mutableStateOf(false) }
    var error by remember(post.id) { mutableStateOf<String?>(null) }
    BackHandler { onDismiss() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close viewer", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(post.authorDisplayName.ifBlank { post.authorUsername }, color = Color.White, style = MaterialTheme.typography.titleSmall)
                        Text("@" + post.authorUsername.removePrefix("@") + " • " + viewerRelative(post.timestamp), color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
                    }
                    if (media.size > 1) Text((pager.currentPage + 1).toString() + "/" + media.size, color = Color.White, modifier = Modifier.padding(end = 8.dp))
                }
                if (media.size > 1) Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(media.size) { index -> LinearProgressIndicator(progress = { if (index <= pager.currentPage) 1f else 0f }, modifier = Modifier.weight(1f).height(3.dp), color = Color.White, trackColor = Color.White.copy(alpha = .25f)) }
                }
                HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page -> FynxViewerMediaPage(context, media[page]) }
                if (post.text.isNotBlank()) Text(post.text, Modifier.fillMaxWidth().padding(12.dp), color = Color.White, maxLines = 4)
                Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ViewerAction(if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder, likes.toString(), liked, !busy) {
                        if (!busy) { busy = true; scope.launch { FynxRemoteSocialClient.like(context, post.id).onSuccess { r -> liked = r.first; likes = r.second }.onFailure { error = it.message }; busy = false } }
                    }
                    ViewerAction(Icons.Default.ChatBubbleOutline, comments.toString(), false, !busy) { commentsOpen = true }
                    ViewerAction(Icons.Default.Share, "Share", false, !busy) {
                        runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, (post.authorDisplayName.ifBlank { post.authorUsername } + " on FYNX:\n" + post.text).trim()) }, "Share with…")) }.onFailure { error = it.message }
                    }
                    ViewerAction(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "Save", saved, !busy) {
                        if (!busy) { busy = true; scope.launch { FynxRemoteSocialClient.save(context, post.id, !saved).onSuccess { r -> saved = r.first }.onFailure { error = it.message }; busy = false } }
                    }
                    ViewerAction(Icons.Default.Repeat, "Repost", reposted, !busy) {
                        if (!busy) { busy = true; scope.launch { FynxRemoteSocialClient.repost(context, post.id, !reposted).onSuccess { r -> reposted = r.first }.onFailure { error = it.message }; busy = false } }
                    }
                }
                error?.let { Text(it ?: "", Modifier.padding(8.dp), color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
    if (commentsOpen) FynxHomeCommentsPanel(post = post.copy(likeCount = likes, commentCount = comments, likedByCurrentUser = liked), onClose = { commentsOpen = false }, onCommentCountChanged = { comments = it })
}

@Composable private fun FynxViewerMediaPage(context: Context, item: FynxPostViewerItem) {
    var file by remember(item.mediaUrl) { mutableStateOf<File?>(null) }
    LaunchedEffect(item.mediaUrl) { file = withContext(Dispatchers.IO) { FynxMediaCache.getOrDownload(context, item.mediaUrl, item.mediaType) } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (file == null) CircularProgressIndicator(color = Color.White)
        else if (item.mediaType.equals("video", true)) AndroidView(factory = { ctx -> VideoView(ctx).apply { layoutParams = ViewGroup.LayoutParams(-1, -1); setMediaController(MediaController(ctx)); setVideoURI(Uri.fromFile(file)); setOnPreparedListener { it.isLooping = true; start() } } }, modifier = Modifier.fillMaxSize())
        else {
            var bitmap by remember(file) { mutableStateOf<android.graphics.Bitmap?>(null) }
            LaunchedEffect(file) { bitmap = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(file!!.absolutePath) }.getOrNull() } }
            bitmap?.let { Image(it.asImageBitmap(), "Post photo", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
    }
}

@Composable private fun ViewerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { IconButton(onClick = onClick, enabled = enabled) { Icon(icon, label, tint = if (active) MaterialTheme.colorScheme.primary else Color.White) }; Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall) }
}

private fun viewerRelative(timestamp: Long): String { val minutes = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / 60000L); return when { minutes < 1 -> "now"; minutes < 60 -> minutes.toString() + "m"; minutes < 1440 -> (minutes / 60).toString() + "h"; else -> (minutes / 1440).toString() + "d" } }