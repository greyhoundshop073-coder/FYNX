package com.fynx.app.ui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.view.ViewGroup
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun FynxHomeDiscoverySection(videos: List<FynxDiscoveryClient.TrendingPost>, loadingMore: Boolean, hasMore: Boolean, onLoadMore: () -> Unit, onOpenVideo: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Discovery", style = MaterialTheme.typography.titleMedium)
                Text("Real FYNX videos people are engaging with.", style = MaterialTheme.typography.bodySmall, color = FynxDesign.TextSecondary)
            }
            if (loadingMore) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 0.dp)) {
            itemsIndexed(videos.take(30), key = { _, video -> video.id }) { index, video ->
                FynxDiscoveryPreviewCard(video = video, onOpen = { onOpenVideo(index) })
                if (index >= minOf(videos.size, 30) - 4 && hasMore && !loadingMore) {
                    LaunchedEffect(videos.size) { onLoadMore() }
                }
            }
            if (loadingMore) item {
                Box(Modifier.width(180.dp).aspectRatio(9f / 16f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

@Composable
private fun FynxDiscoveryPreviewCard(video: FynxDiscoveryClient.TrendingPost, onOpen: () -> Unit) {
    val context = LocalContext.current
    var file by remember(video.id) { mutableStateOf<File?>(null) }
    var durationMs by remember(video.id) { mutableLongStateOf(0L) }
    LaunchedEffect(video.id) {
        file = withContext(Dispatchers.IO) { FynxMediaCache.getOrDownload(context, "/api/social/media/${video.mediaId}", "video") }
        durationMs = file?.let { resolveDuration(it) } ?: 0L
    }
    Box(Modifier.width(180.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(16.dp)).background(Color.Black).clickable(onClick = onOpen)) {
        if (file != null) {
            AndroidView(factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                    setVideoPath(file!!.absolutePath)
                    setOnPreparedListener { player -> player.isLooping = true; player.setVolume(0f, 0f); player.start() }
                }
            }, modifier = Modifier.fillMaxSize())
        } else CircularProgressIndicator(Modifier.align(Alignment.Center))
        Surface(Modifier.align(Alignment.Center), shape = androidx.compose.foundation.shape.CircleShape, color = Color.Black.copy(alpha = 0.55f)) {
            Icon(Icons.Default.PlayArrow, "Open video", Modifier.padding(12.dp), tint = Color.White)
        }
        Surface(Modifier.align(Alignment.BottomStart).padding(8.dp), shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.65f)) {
            Text(formatVideoDuration(durationMs), Modifier.padding(horizontal = 7.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
        Text(video.authorDisplayName.ifBlank { video.authorUsername }, Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp).padding(top = 30.dp), style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
    }
}

@Composable
fun FynxHomeDiscoveryViewer(context: Context, videos: List<FynxDiscoveryClient.TrendingPost>, initialIndex: Int, onLoadMore: () -> Unit, onDismiss: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, (videos.size - 1).coerceAtLeast(0)), pageCount = { videos.size })
    LaunchedEffect(pagerState, videos.size) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            videos.getOrNull(page)?.let { FynxDiscoveryClient.recordView(context, it.id) }
            if (videos.size - page <= 3) onLoadMore()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                videos.getOrNull(page)?.let { FynxDiscoveryFullscreenVideo(it) }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) { Icon(Icons.Default.Close, "Close Discovery", tint = Color.White) }
            videos.getOrNull(pagerState.currentPage)?.let { video ->
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(video.authorDisplayName.ifBlank { video.authorUsername }, style = MaterialTheme.typography.titleMedium, color = Color.White)
                    if (video.text.isNotBlank()) Text(video.text, style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 3)
                    Text("${video.likeCount} likes • ${video.commentCount} comments", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
private fun FynxDiscoveryFullscreenVideo(video: FynxDiscoveryClient.TrendingPost) {
    val context = LocalContext.current
    var file by remember(video.id) { mutableStateOf<File?>(null) }
    LaunchedEffect(video.id) { file = withContext(Dispatchers.IO) { FynxMediaCache.getOrDownload(context, "/api/social/media/${video.mediaId}", "video") } }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        file?.let { local ->
            AndroidView(factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                    setVideoPath(local.absolutePath)
                    setOnPreparedListener { player -> player.isLooping = true; player.start() }
                }
            }, modifier = Modifier.fillMaxSize())
        } ?: CircularProgressIndicator()
    }
}

private suspend fun resolveDuration(file: File): Long = withContext(Dispatchers.IO) {
    runCatching {
        MediaMetadataRetriever().run {
            setDataSource(file.absolutePath)
            val value = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            release()
            value
        }
    }.getOrDefault(0L)
}

private fun formatVideoDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L).toInt()
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
