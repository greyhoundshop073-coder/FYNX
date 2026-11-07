package com.fynx.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.view.ViewGroup
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal data class FynxVideoVisualFrame(
    val poster: Bitmap?,
    val durationMs: Long,
    val aspectRatio: Float
)

/** Reads a lightweight poster frame and the clip's display ratio off the main thread. */
internal suspend fun readFynxVideoVisual(file: File): FynxVideoVisualFrame = withContext(Dispatchers.IO) {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(file.absolutePath)
        val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 16f
        val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 9f
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val width = if (rotation == 90 || rotation == 270) rawHeight else rawWidth
        val height = if (rotation == 90 || rotation == 270) rawWidth else rawHeight
        val ratio = (width / height).takeIf { it.isFinite() && it > 0f }?.coerceIn(0.45f, 2.4f) ?: (16f / 9f)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        // Some valid MP4s do not expose a sync frame at 250 ms (especially
        // short clips or files whose first keyframe is later). Try several safe
        // timestamps/modes so the feed can still paint the real clip as its
        // ambient side-fill instead of leaving black letterbox areas.
        val rawPoster = sequenceOf(
            { retriever.getFrameAtTime(250_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) },
            { retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST) },
            { retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST) },
            { retriever.getFrameAtTime(-1L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) }
        ).mapNotNull { extract -> runCatching(extract).getOrNull() }.firstOrNull()
        val poster = rawPoster?.let { frame ->
            val longest = maxOf(frame.width, frame.height)
            if (longest <= 720) frame
            else {
                val scale = 720f / longest.toFloat()
                Bitmap.createScaledBitmap(
                    frame,
                    (frame.width * scale).toInt().coerceAtLeast(1),
                    (frame.height * scale).toInt().coerceAtLeast(1),
                    true
                ).also { if (it !== frame) frame.recycle() }
            }
        }
        FynxVideoVisualFrame(poster, duration.coerceAtLeast(0L), ratio)
    } catch (_: Exception) {
        FynxVideoVisualFrame(null, 0L, 16f / 9f)
    } finally {
        runCatching { retriever.release() }
    }
}

/**
 * Uses a frame from this same clip as an ambient backdrop. The original video
 * remains fitted at its real aspect ratio above this layer, so portrait and
 * landscape media never get stretched to fill the screen.
 */
@Composable
internal fun FynxVideoAmbientBackdrop(poster: Bitmap?, modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.verticalGradient(
                // Keep a deliberate FYNX-blue treatment visible even when a
                // damaged/unsupported clip cannot provide a poster frame.
                listOf(Color(0xFF174B78), Color(0xFF123451), Color(0xFF102B3C))
            )
        )
    ) {
        poster?.let { frame ->
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(28.dp),
                contentScale = ContentScale.Crop
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
    }
}

@Composable
fun FynxHomeDiscoverySection(
    videos: List<FynxDiscoveryClient.TrendingPost>,
    loadingMore: Boolean,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenVideo: (Int) -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Discovery", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Real FYNX videos people are engaging with.",
                    style = MaterialTheme.typography.bodySmall,
                    color = FynxDesign.TextSecondary
                )
            }
            if (loadingMore) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(videos.take(30), key = { _, video -> video.id }) { index, video ->
                FynxDiscoveryPreviewCard(video = video, onOpen = { onOpenVideo(index) })
                if (index >= minOf(videos.size, 30) - 4 && hasMore && !loadingMore) {
                    LaunchedEffect(videos.size) { onLoadMore() }
                }
            }
            if (loadingMore) item {
                Box(Modifier.width(180.dp).aspectRatio(9f / 16f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

/** Discovery cards use real video poster frames, not multiple autoplaying players. */
@Composable
private fun FynxDiscoveryPreviewCard(video: FynxDiscoveryClient.TrendingPost, onOpen: () -> Unit) {
    val context = LocalContext.current
    var file by remember(video.id) { mutableStateOf<File?>(null) }
    var visual by remember(video.id) { mutableStateOf<FynxVideoVisualFrame?>(null) }
    var mediaLoading by remember(video.id) { mutableStateOf(true) }
    var mediaFailed by remember(video.id) { mutableStateOf(false) }
    var retryNonce by remember(video.id) { mutableIntStateOf(0) }
    LaunchedEffect(video.id, retryNonce) {
        mediaLoading = true
        mediaFailed = false
        file = video.mediaId?.let {
            withContext(Dispatchers.IO) {
                FynxMediaCache.getOrDownload(context, "/api/social/media/$it", "video")
            }
        }
        mediaFailed = file == null
        mediaLoading = false
    }
    LaunchedEffect(file) {
        visual = file?.let { media -> withContext(Dispatchers.IO) { readFynxVideoVisual(media) } }
    }
    Box(
        Modifier.width(180.dp).aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF24364D), Color(0xFF111A29))))
            .semantics(mergeDescendants = true) {
                contentDescription = "Open Discovery video"
                role = Role.Button
            }
            .clickable(
                role = Role.Button,
                onClickLabel = "Open Discovery video",
                onClick = onOpen
            )
    ) {
        visual?.poster?.let { poster ->
            Image(
                poster.asImageBitmap(),
                contentDescription = "Video preview by ${video.authorDisplayName.ifBlank { video.authorUsername }}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to Color.Black.copy(alpha = 0.02f),
                    0.56f to Color.Transparent,
                    1.0f to Color.Black.copy(alpha = 0.76f)
                )
            )
        )
        Surface(
            Modifier.align(Alignment.Center),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.52f)
        ) {
            Icon(Icons.Default.PlayArrow, "Open video", Modifier.padding(12.dp), tint = Color.White)
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                video.authorDisplayName.ifBlank { video.authorUsername },
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                maxLines = 1
            )
            Text(
                "${formatVideoDuration(visual?.durationMs ?: 0L)}  •  ${video.likeCount} likes",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.88f),
                maxLines = 1
            )
        }
        if (mediaLoading) {
            CircularProgressIndicator(
                Modifier.align(Alignment.TopEnd).padding(10.dp).size(18.dp),
                strokeWidth = 2.dp,
                color = Color.White
            )
        } else if (mediaFailed) {
            Surface(
                Modifier.align(Alignment.TopEnd).padding(8.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.68f)
            ) {
                Text("!", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun FynxHomeDiscoveryViewer(
    context: Context,
    videos: List<FynxDiscoveryClient.TrendingPost>,
    initialIndex: Int,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit
) {
    var viewerVideos by remember { mutableStateOf(videos) }
    LaunchedEffect(videos) {
        val currentById = viewerVideos.associateBy { it.id }
        viewerVideos = videos.map { incoming ->
            val current = currentById[incoming.id] ?: incoming
            incoming.copy(
                likedByCurrentUser = current.likedByCurrentUser,
                likeCount = current.likeCount,
                commentCount = current.commentCount,
                shareCount = current.shareCount
            )
        }
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (viewerVideos.size - 1).coerceAtLeast(0)),
        pageCount = { viewerVideos.size }
    )
    val scope = rememberCoroutineScope()
    var busyPostIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var actionNotice by remember { mutableStateOf<String?>(null) }
    var commentsVideo by remember { mutableStateOf<FynxDiscoveryClient.TrendingPost?>(null) }
    val currentVideo = viewerVideos.getOrNull(pagerState.currentPage)

    fun updateVideo(id: String, update: (FynxDiscoveryClient.TrendingPost) -> FynxDiscoveryClient.TrendingPost) {
        viewerVideos = viewerVideos.map { if (it.id == id) update(it) else it }
    }

    fun like(video: FynxDiscoveryClient.TrendingPost) {
        if (video.id in busyPostIds) return
        val before = viewerVideos.firstOrNull { it.id == video.id } ?: video
        val desired = !before.likedByCurrentUser
        updateVideo(video.id) {
            it.copy(likedByCurrentUser = desired, likeCount = (it.likeCount + if (desired) 1 else -1).coerceAtLeast(0))
        }
        busyPostIds = busyPostIds + video.id
        actionNotice = null
        scope.launch {
            FynxRemoteSocialClient.like(context, video.id, desired).onSuccess { (liked, count) ->
                updateVideo(video.id) { it.copy(likedByCurrentUser = liked, likeCount = count.coerceAtLeast(0)) }
                if (desired) FynxDiscoveryClient.recordEngagement(context, "LIKE", video.id)
            }.onFailure { failure ->
                updateVideo(video.id) { it.copy(likedByCurrentUser = before.likedByCurrentUser, likeCount = before.likeCount) }
                actionNotice = failure.message ?: "Unable to update this like."
            }
            busyPostIds = busyPostIds - video.id
        }
    }

    fun share(video: FynxDiscoveryClient.TrendingPost) {
        runCatching {
            val shareText = "${video.authorDisplayName.ifBlank { video.authorUsername }} on FYNX:\n${video.text}".trim()
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_TITLE, "Share FYNX video")
            }
            context.startActivity(Intent.createChooser(intent, "Share video"))
            updateVideo(video.id) { it.copy(shareCount = it.shareCount + 1) }
            scope.launch { FynxDiscoveryClient.recordEngagement(context, "SHARE", video.id) }
            actionNotice = null
        }.onFailure { failure ->
            actionNotice = failure.message ?: "Unable to share this video."
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            viewerVideos.getOrNull(page)?.let { video ->
                FynxDiscoveryClient.recordView(context, video.id)
                if (viewerVideos.size - page <= 3) onLoadMore()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0
            ) { page ->
                viewerVideos.getOrNull(page)?.let { video ->
                    FynxDiscoveryFullscreenVideo(video, isActive = page == pagerState.currentPage)
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)
            ) {
                Icon(Icons.Default.Close, "Close Discovery", tint = Color.White)
            }
            currentVideo?.let { video ->
                Column(
                    Modifier.align(Alignment.CenterEnd).padding(end = 10.dp, bottom = 76.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(15.dp)
                ) {
                    DiscoveryActionButton(
                        icon = if (video.likedByCurrentUser) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        label = if (video.likedByCurrentUser) "Unlike video" else "Like video",
                        count = video.likeCount,
                        active = video.likedByCurrentUser,
                        enabled = video.id !in busyPostIds,
                        onClick = { like(video) }
                    )
                    DiscoveryActionButton(
                        icon = Icons.Default.ChatBubbleOutline,
                        label = "Open comments",
                        count = video.commentCount,
                        onClick = { commentsVideo = video }
                    )
                    DiscoveryActionButton(
                        icon = Icons.Default.Share,
                        label = "Share video",
                        count = video.shareCount,
                        onClick = { share(video) }
                    )
                }
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .padding(start = 18.dp, end = 88.dp, bottom = 26.dp)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        video.authorDisplayName.ifBlank { video.authorUsername },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    if (video.text.isNotBlank()) {
                        Text(
                            video.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            maxLines = 3
                        )
                    }
                }
            }
            actionNotice?.let { notice ->
                Surface(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp),
                    color = Color(0xCC202938),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(notice, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), color = Color.White)
                }
            }
            commentsVideo?.let { video ->
                FynxHomeCommentsPanel(
                    post = video.toRemotePost(),
                    onClose = { commentsVideo = null },
                    onCommentCountChanged = { count ->
                        updateVideo(video.id) { it.copy(commentCount = count) }
                        scope.launch { FynxDiscoveryClient.recordEngagement(context, "COMMENT", video.id) }
                    }
                )
            }
        }
    }
}

@Composable
private fun DiscoveryActionButton(
    icon: ImageVector,
    label: String,
    count: Int,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.45f)) {
            IconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.size(50.dp).semantics { contentDescription = label }
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (active) Color(0xFFFF5277) else Color.White,
                    modifier = Modifier.size(25.dp)
                )
            }
        }
        Text(count.coerceAtLeast(0).toString(), style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

private fun FynxDiscoveryClient.TrendingPost.toRemotePost(): FynxRemoteSocialClient.RemotePost =
    FynxRemoteSocialClient.RemotePost(
        id = id,
        authorId = authorId,
        authorUsername = authorUsername,
        authorDisplayName = authorDisplayName,
        text = text,
        visibility = visibility,
        mediaId = mediaId,
        mediaType = mediaType ?: "video",
        mediaUrl = mediaId?.let { "/api/social/media/$it" },
        timestamp = timestamp,
        likeCount = likeCount,
        commentCount = commentCount,
        likedByCurrentUser = likedByCurrentUser,
        followedByCurrentUser = false,
        isDiscovery = true,
        discoveryScore = score
    )

@Composable
private fun FynxDiscoveryFullscreenVideo(video: FynxDiscoveryClient.TrendingPost, isActive: Boolean) {
    val context = LocalContext.current
    var file by remember(video.id) { mutableStateOf<File?>(null) }
    var visual by remember(video.id) { mutableStateOf<FynxVideoVisualFrame?>(null) }
    var prepared by remember(video.id) { mutableStateOf(false) }
    var playing by remember(video.id) { mutableStateOf(false) }
    var mediaLoading by remember(video.id) { mutableStateOf(true) }
    var mediaFailed by remember(video.id) { mutableStateOf(false) }
    var playbackError by remember(video.id) { mutableStateOf(false) }
    var retryNonce by remember(video.id) { mutableIntStateOf(0) }
    val viewState = remember(video.id) { mutableStateOf<VideoView?>(null) }
    val playerState = remember(video.id) { mutableStateOf<android.media.MediaPlayer?>(null) }
    var muted by remember(video.id) { mutableStateOf(true) }
    val mutedState = rememberUpdatedState(muted)
    val activeState = rememberUpdatedState(isActive)

    LaunchedEffect(video.id, retryNonce) {
        mediaLoading = true
        mediaFailed = false
        playbackError = false
        prepared = false
        playing = false
        file = video.mediaId?.let { mediaId ->
            withContext(Dispatchers.IO) {
                FynxMediaCache.getOrDownload(
                    context,
                    "/api/social/media/$mediaId",
                    "video",
                    forceRefresh = retryNonce > 0
                )
            }
        }
        mediaFailed = file == null
        mediaLoading = false
    }
    LaunchedEffect(file) {
        visual = file?.let { media -> withContext(Dispatchers.IO) { readFynxVideoVisual(media) } }
    }
    DisposableEffect(video.id) {
        onDispose {
            runCatching { viewState.value?.stopPlayback() }
            viewState.value = null
            playerState.value = null
        }
    }
    DisposableEffect(file, retryNonce) {
        onDispose {
            // Read the actual attached player during disposal so retries always
            // stop the view being replaced, rather than a stale/null snapshot.
            val attachedView = viewState.value
            runCatching { attachedView?.stopPlayback() }
            if (viewState.value === attachedView) viewState.value = null
        }
    }
    LaunchedEffect(isActive, prepared, viewState.value) {
        val view = viewState.value ?: return@LaunchedEffect
        if (!prepared) return@LaunchedEffect
        runCatching {
            if (isActive && !view.isPlaying) {
                view.start()
                playing = true
            } else if (!isActive && view.isPlaying) {
                view.pause()
                playing = false
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        FynxVideoAmbientBackdrop(visual?.poster, Modifier.fillMaxSize())
        val aspect = visual?.aspectRatio ?: (16f / 9f)
        val containerAspect = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
        val fittedWidth = if (containerAspect > aspect) maxHeight * aspect else maxWidth
        val fittedHeight = if (containerAspect > aspect) maxHeight else maxWidth / aspect
        if (file != null && !mediaLoading) {
            key(file, retryNonce) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            viewState.value = this
                            layoutParams = ViewGroup.LayoutParams(-1, -1)
                            setVideoPath(file!!.absolutePath)
                            setOnPreparedListener { player ->
                                playerState.value = player
                                player.isLooping = true
                                player.setVolume(if (mutedState.value) 0f else 1f, if (mutedState.value) 0f else 1f)
                                prepared = true
                                playbackError = false
                                if (activeState.value) {
                                    start()
                                    playing = true
                                }
                            }
                            setOnErrorListener { _, _, _ ->
                                playerState.value = null
                                prepared = false
                                playing = false
                                playbackError = true
                                true
                            }
                        }
                    },
                    modifier = Modifier.width(fittedWidth).height(fittedHeight).align(Alignment.Center),
                    update = { viewState.value = it }
                )
            }
        }
        when {
            mediaLoading || (file != null && !prepared && !playbackError) -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
            mediaFailed || playbackError -> {
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.14f)) {
                        Text("!", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Color.White, style = MaterialTheme.typography.headlineSmall)
                    }
                    Text(
                        if (playbackError) "This video could not start" else "This video could not be loaded",
                        color = Color.White
                    )
                    TextButton(onClick = {
                        mediaLoading = true
                        file = null
                        retryNonce++
                    }) {
                        Text("Retry video", color = Color.White)
                    }
                }
            }
        }
        if (prepared) {
            IconButton(
                onClick = {
                    muted = !muted
                    runCatching {
                        playerState.value?.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f)
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 18.dp, bottom = 24.dp)
                    .size(48.dp)
                    .semantics {
                        contentDescription = if (muted) "Unmute Discovery video" else "Mute Discovery video"
                    },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.42f),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = null
                )
            }
        }
        if (prepared) {
            Surface(
                Modifier.align(Alignment.Center),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.42f)
            ) {
                IconButton(
                    onClick = {
                        val view = viewState.value ?: return@IconButton
                        runCatching {
                            if (view.isPlaying) {
                                view.pause()
                                playing = false
                            } else if (isActive) {
                                view.start()
                                playing = true
                            }
                        }
                    },
                    modifier = Modifier.size(58.dp).semantics {
                        contentDescription = if (playing) "Pause Discovery video" else "Play Discovery video"
                    }
                ) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }
    }
}

private fun formatVideoDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L).toInt()
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
