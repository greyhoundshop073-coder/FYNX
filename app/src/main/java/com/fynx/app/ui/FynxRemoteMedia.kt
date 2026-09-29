package com.fynx.app.ui

import android.graphics.BitmapFactory
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.graphics.ImageDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

private const val MAX_REMOTE_MEDIA_BYTES = 12L * 1024L * 1024L

private fun resolveFynxMediaUrl(context: android.content.Context, mediaUrl: String): String {
    val value = mediaUrl.trim()
    if (value.isBlank()) return value
    if (value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)) return value
    return if (value.startsWith("/")) FynxBackendClient.baseUrl(context) + value else FynxBackendClient.baseUrl(context) + "/" + value
}

private fun remoteMediaCacheFile(context: android.content.Context, resolvedUrl: String, extension: String): File? {
    val accountKey = FynxAuthStore.accountStorageKey(context) ?: return null
    if (!FynxBackendClient.hasAccessToken(context)) return null
    val safeAccount = accountKey.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").take(80).ifBlank { return null }
    val directory = File(context.cacheDir, "fynx_media_remote_$safeAccount")
    if (!directory.exists() && !directory.mkdirs()) return null
    val digest = MessageDigest.getInstance("SHA-256").digest(resolvedUrl.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    return File(directory, "media_${digest.take(32)}$extension")
}

private suspend fun downloadRemoteMedia(context: android.content.Context, resolvedUrl: String, destination: File): Result<FynxBackendClient.DownloadedMedia> =
    FynxBackendClient.downloadToFile(context, resolvedUrl, destination, MAX_REMOTE_MEDIA_BYTES)

@Composable
fun FynxRemoteMedia(
    mediaUrl: String,
    type: String,
    modifier: Modifier = Modifier,
    loopVideo: Boolean = true,
    onVideoCompleted: (() -> Unit)? = null,
    contentScale: ContentScale = ContentScale.Fit,
    rounded: Boolean = true,
    autoPlay: Boolean = true,
    playbackActive: Boolean = true
) {
    val context = LocalContext.current
    val resolvedUrl = remember(mediaUrl) { resolveFynxMediaUrl(context, mediaUrl) }
    var kind by remember(resolvedUrl, type) { mutableStateOf("loading") }
    var bitmap by remember(resolvedUrl, type) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var animatedDrawable by remember(resolvedUrl, type) { mutableStateOf<Drawable?>(null) }
    var localFile by remember(resolvedUrl, type) { mutableStateOf<File?>(null) }
    var reloadNonce by remember(resolvedUrl, type) { mutableIntStateOf(0) }
    var videoView by remember(resolvedUrl, type) { mutableStateOf<android.widget.VideoView?>(null) }
    var preparedPlayer by remember(resolvedUrl, type) { mutableStateOf<android.media.MediaPlayer?>(null) }
    var videoPlaying by remember(resolvedUrl, type) { mutableStateOf(false) }
    LaunchedEffect(resolvedUrl, type, reloadNonce) {
        kind = "loading"; bitmap = null; animatedDrawable = null; localFile = null; videoView = null; videoPlaying = false
        try {
            val loaded = withContext(Dispatchers.IO) {
                val isKnownVideo = type.equals("video", true)
                val extension = if (isKnownVideo) ".media" else ".image"
                val cacheTarget = remoteMediaCacheFile(context, resolvedUrl, extension)
                val target = cacheTarget ?: File.createTempFile("fynx_media_", ".media", context.cacheDir)
                val result = if (cacheTarget?.exists() == true && cacheTarget.length() > 0L) Result.success(FynxBackendClient.DownloadedMedia(null, cacheTarget.length())) else downloadRemoteMedia(context, resolvedUrl, target)
                result.getOrThrow().let { downloaded ->
                    val contentType = downloaded.contentType.orEmpty()
                    val isVideo = isKnownVideo || (type.equals("auto", true) && contentType.startsWith("video/"))
                    val isGif = type.equals("gif", true) || contentType.equals("image/gif", true)
                    if (isVideo) MediaLoadResult.Video(target)
                    else if (isGif && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(android.os.ParcelFileDescriptor.open(target, android.os.ParcelFileDescriptor.MODE_READ_ONLY).fileDescriptor)
                        MediaLoadResult.Gif(ImageDecoder.decodeDrawable(source))
                    } else {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(target.absolutePath, bounds)
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IllegalStateException("Unable to decode media")
                        val maxDimension = 2048
                        var sample = 1
                        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2
                        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 }
                        val decoded = BitmapFactory.decodeFile(target.absolutePath, options) ?: throw IllegalStateException("Unable to decode media")
                        if (cacheTarget == null) target.delete()
                        MediaLoadResult.Image(decoded)
                    }
                }
            }
            when (loaded) {
                is MediaLoadResult.Image -> { bitmap = loaded.bitmap; animatedDrawable = null; localFile = null; kind = "image" }
                is MediaLoadResult.Gif -> { animatedDrawable = loaded.drawable; bitmap = null; localFile = loaded.file; kind = "gif" }
                is MediaLoadResult.Video -> { localFile = loaded.file; bitmap = null; animatedDrawable = null; kind = "video" }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Throwable) { kind = "error" }
    }
    LaunchedEffect(playbackActive, videoView, kind) {
        val view = videoView ?: return@LaunchedEffect
        if (kind != "video") return@LaunchedEffect
        if (playbackActive && autoPlay) { if (!view.isPlaying) runCatching { view.start(); videoPlaying = true } }
        else if (!playbackActive) { if (view.isPlaying) runCatching { view.pause() }; videoPlaying = false }
    }
    DisposableEffect(resolvedUrl, type) {
        onDispose {
            (animatedDrawable as? AnimatedImageDrawable)?.stop()
            videoView?.stopPlayback()
            preparedPlayer = null
            videoView = null
        }
    }
    when (kind) {
        "gif" -> animatedDrawable?.let { drawable ->
            val imageModifier = if (rounded) modifier.clip(RoundedCornerShape(14.dp)) else modifier
            AndroidView(
                factory = { ctx -> android.widget.ImageView(ctx).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER; setImageDrawable(drawable); (drawable as? AnimatedImageDrawable)?.start() } },
                update = { view -> if (view.drawable !== drawable) view.setImageDrawable(drawable); (drawable as? AnimatedImageDrawable)?.takeIf { !it.isRunning }?.start() },
                modifier = imageModifier
            )
        }
        "image" -> bitmap?.let {
            val imageModifier = if (rounded) modifier.clip(RoundedCornerShape(14.dp)) else modifier
            Image(it.asImageBitmap(), "Media", imageModifier, contentScale = contentScale)
        }
        "video" -> localFile?.let { file ->
            val videoModifier = if (rounded) modifier.clip(RoundedCornerShape(14.dp)) else modifier
            Box(videoModifier) {
                AndroidView(
                    factory = { ctx ->
                        FynxPassiveVideoView(ctx).apply {
                            tag = file.absolutePath
                            setVideoPath(file.absolutePath)
                            setOnPreparedListener { player -> preparedPlayer = player; player.isLooping = loopVideo; if (autoPlay && playbackActive) { player.start(); videoPlaying = true } }
                            setOnCompletionListener { videoPlaying = false; onVideoCompleted?.invoke() }
                            setOnErrorListener { _, _, _ -> videoPlaying = false; true }
                            videoView = this
                        }
                    },
                    update = { view ->
                        if (view.tag != file.absolutePath) {
                            view.tag = file.absolutePath
                            view.setVideoPath(file.absolutePath)
                            view.setOnPreparedListener { player ->
                                preparedPlayer = player
                                player.isLooping = loopVideo
                                if (autoPlay && playbackActive) {
                                    player.start()
                                    videoPlaying = true
                                } else {
                                    videoPlaying = false
                                }
                            }
                            view.setOnCompletionListener { videoPlaying = false; onVideoCompleted?.invoke() }
                            view.setOnErrorListener { _, _, _ -> videoPlaying = false; true }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                Surface(color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f), shape = RoundedCornerShape(50), modifier = Modifier.align(Alignment.Center)) {
                    IconButton(onClick = { videoView?.let { view -> if (view.isPlaying) { view.pause(); videoPlaying = false } else { view.start(); videoPlaying = true } } }) {
                        Icon(if (videoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (videoPlaying) "Pause video" else "Play video", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        "error" -> Box(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.BrokenImage, "Media unavailable"); Text("Media unavailable", style = MaterialTheme.typography.labelSmall); TextButton(onClick = { reloadNonce++ }) { Text("Retry") }
            }
        }
        else -> Box(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

object FynxStatusNavigation {
    var opener: ((String) -> Unit)? = null
}

private object FynxStatusPresenceStore {
    private var loadedAt = 0L
    private var activeOwners: Set<String> = emptySet()

    suspend fun activeOwners(context: android.content.Context): Set<String> {
        val now = System.currentTimeMillis()
        if (now - loadedAt < 30_000L) return activeOwners
        return runCatching { FynxStatusClient.list(context).getOrNull() }
            .getOrNull()
            ?.filterNot(FynxStatus::isExpired)
            ?.map { it.ownerUsername.removePrefix("@").trim().lowercase() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?.also { activeOwners = it; loadedAt = now }
            ?: activeOwners
    }
}

@Composable
fun FynxRemoteProfileAvatar(
    mediaId: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    ownerUsername: String? = null,
    onStatusClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    // Resolve a known person photo from the existing cache when the caller has no media id.
    val resolvedMediaId = mediaId?.takeIf { it.isNotBlank() } ?: ownerUsername
        ?.removePrefix("@")?.trim()?.takeIf { it.isNotBlank() }
        ?.let { FynxProfileRemoteClient.cachedProfilePhotoId(context, it) }
    var hasActiveStatus by remember(ownerUsername) { mutableStateOf(false) }
    LaunchedEffect(ownerUsername) {
        val owner = ownerUsername?.removePrefix("@")?.trim()?.lowercase().orEmpty()
        if (owner.isBlank()) {
            hasActiveStatus = false
        } else {
            hasActiveStatus = runCatching {
                FynxStatusPresenceStore.activeOwners(context).contains(owner)
            }.getOrDefault(false)
        }
    }
    val avatar: @Composable () -> Unit = {
        if (resolvedMediaId.isNullOrBlank()) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Text(contentDescription.orEmpty().trim().firstOrNull()?.uppercase() ?: "F", color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        } else {
            FynxRemoteMedia("/api/media/${resolvedMediaId.trim()}", "image", Modifier.fillMaxSize().clip(RoundedCornerShape(50)))
        }
    }
    Box(
        modifier = modifier
            .then(if (hasActiveStatus) Modifier.border(2.dp, Color(0xFF22C55E), RoundedCornerShape(50)).padding(2.dp) else Modifier)
            .clip(RoundedCornerShape(50))
            .then(if (hasActiveStatus && onStatusClick != null) Modifier.clickable { onStatusClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) { avatar() }
}

private sealed interface MediaLoadResult { data class Image(val bitmap: android.graphics.Bitmap) : MediaLoadResult; data class Gif(val drawable: Drawable, val file: File) : MediaLoadResult; data class Video(val file: File) : MediaLoadResult }

@Composable
fun FynxRemoteAudio(mediaUrl: String, modifier: Modifier = Modifier, maxDurationMs: Long? = null) {
    val context = LocalContext.current
    val resolvedUrl = remember(mediaUrl) { resolveFynxMediaUrl(context, mediaUrl) }
    val scope = rememberCoroutineScope()
    var player by remember(resolvedUrl) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(resolvedUrl) { mutableStateOf(false) }
    var loading by remember(resolvedUrl) { mutableStateOf(false) }
    var error by remember(resolvedUrl) { mutableStateOf<String?>(null) }
    var positionMs by remember(resolvedUrl) { mutableLongStateOf(0L) }
    var durationMs by remember(resolvedUrl) { mutableLongStateOf(maxDurationMs ?: 0L) }

    DisposableEffect(resolvedUrl) {
        onDispose { player?.release(); player = null }
    }

    LaunchedEffect(playing, player) {
        while (playing) {
            positionMs = player?.currentPosition?.toLong() ?: positionMs
            delay(120L)
        }
    }

    Row(
        modifier = modifier.padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            IconButton(
                enabled = !loading,
                onClick = {
                    if (playing) {
                        player?.pause()
                        playing = false
                        return@IconButton
                    }
                    if (player != null) {
                        player?.start()
                        playing = true
                        return@IconButton
                    }
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            val cached = remoteMediaCacheFile(context, resolvedUrl, ".audio")
                            val target = cached ?: File.createTempFile("fynx_audio_", ".audio", context.cacheDir)
                            val result = if (cached?.exists() == true && cached.length() > 0L) {
                                Result.success(FynxBackendClient.DownloadedMedia(null, cached.length()))
                            } else {
                                downloadRemoteMedia(context, resolvedUrl, target)
                            }
                            result.getOrThrow()
                            if (!target.exists() || target.length() == 0L) throw IllegalStateException("Downloaded voice media is empty")
                            val finalFile = target
                            val p = MediaPlayer().apply {
                                setAudioAttributes(
                                    AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_MEDIA)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                        .build()
                                )
                                setDataSource(finalFile.absolutePath)
                                setOnPreparedListener {
                                    durationMs = maxDurationMs?.takeIf { it > 0L } ?: it.duration.toLong()
                                    positionMs = 0L
                                    loading = false
                                    playing = true
                                    it.start()
                                }
                                setOnCompletionListener {
                                    positionMs = 0L
                                    playing = false
                                    release()
                                    player = null
                                }
                                setOnErrorListener { mp, _, _ ->
                                    loading = false
                                    playing = false
                                    error = "Voice message could not be played."
                                    runCatching { mp.reset() }
                                    runCatching { mp.release() }
                                    player = null
                                    true
                                }
                            }
                            player = p
                            p.prepareAsync()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Throwable) {
                            loading = false
                            playing = false
                            error = failure.message ?: "Voice message could not be loaded."
                            player?.release()
                            player = null
                        }
                    }
                }
            ) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause voice message" else "Play voice message")
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            val waveformPlayedColor = MaterialTheme.colorScheme.primary
            val waveformIdleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
            Canvas(Modifier.fillMaxWidth().height(34.dp)) {
                val bars = 32
                val gap = 3.dp.toPx()
                val barWidth = ((size.width - gap * (bars - 1)) / bars).coerceAtLeast(1f)
                val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                for (i in 0 until bars) {
                    val phase = ((i * 37) % 17) / 17f
                    val normalized = (0.25f + 0.75f * kotlin.math.abs(kotlin.math.sin(i * 0.73f + phase))).coerceIn(0.22f, 1f)
                    val height = size.height * normalized
                    val x = i * (barWidth + gap)
                    val y = (size.height - height) / 2f
                    val played = i.toFloat() / bars <= progress
                    drawRoundRect(
                        color = if (played) waveformPlayedColor else waveformIdleColor,
                        topLeft = androidx.compose.ui.geometry.Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(barWidth, height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatVoiceDuration(positionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatVoiceDuration(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 1) }
        }
    }
}

private fun formatVoiceDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

/** Video surface stays passive so parent LazyColumn/LazyRow containers keep ownership of drag gestures. */
internal class FynxPassiveVideoView(context: android.content.Context) : android.widget.VideoView(context) {
    override fun onTouchEvent(event: android.view.MotionEvent): Boolean = false
    override fun performClick(): Boolean = false
}
