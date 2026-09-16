package com.fynx.app.ui

import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.Image
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
import androidx.compose.ui.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    onVideoCompleted: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val resolvedUrl = remember(mediaUrl) { resolveFynxMediaUrl(context, mediaUrl) }
    var kind by remember(resolvedUrl, type) { mutableStateOf("loading") }
    var bitmap by remember(resolvedUrl, type) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var localFile by remember(resolvedUrl, type) { mutableStateOf<File?>(null) }
    var reloadNonce by remember(resolvedUrl, type) { mutableIntStateOf(0) }
    var videoView by remember(resolvedUrl, type) { mutableStateOf<android.widget.VideoView?>(null) }
    var videoPlaying by remember(resolvedUrl, type) { mutableStateOf(false) }
    LaunchedEffect(resolvedUrl, type, reloadNonce) {
        kind = "loading"; bitmap = null; localFile = null; videoView = null; videoPlaying = false
        try {
            val loaded = withContext(Dispatchers.IO) {
                val isKnownVideo = type.equals("video", true)
                val cacheTarget = if (isKnownVideo) remoteMediaCacheFile(context, resolvedUrl, ".media") else null
                val target = cacheTarget ?: File.createTempFile("fynx_media_", ".media", context.cacheDir)
                val result = if (cacheTarget?.exists() == true && cacheTarget.length() > 0L) Result.success(FynxBackendClient.DownloadedMedia(null, cacheTarget.length())) else downloadRemoteMedia(context, resolvedUrl, target)
                result.getOrThrow().let { downloaded ->
                    val contentType = downloaded.contentType.orEmpty()
                    val isVideo = isKnownVideo || (type.equals("auto", true) && contentType.startsWith("video/"))
                    if (isVideo) MediaLoadResult.Video(target)
                    else {
                        val decoded = BitmapFactory.decodeFile(target.absolutePath) ?: throw IllegalStateException("Unable to decode media")
                        if (cacheTarget == null) target.delete()
                        MediaLoadResult.Image(decoded)
                    }
                }
            }
            when (loaded) {
                is MediaLoadResult.Image -> { bitmap = loaded.bitmap; localFile = null; kind = "image" }
                is MediaLoadResult.Video -> { localFile = loaded.file; bitmap = null; kind = "video" }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Throwable) { kind = "error" }
    }
    DisposableEffect(resolvedUrl, type) { onDispose { videoView?.stopPlayback(); videoView = null } }
    when (kind) {
        "image" -> bitmap?.let { Image(it.asImageBitmap(), "Media", modifier.clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop) }
        "video" -> localFile?.let { file ->
            Box(modifier.clip(RoundedCornerShape(14.dp))) {
                AndroidView(
                    factory = { ctx ->
                        android.widget.VideoView(ctx).apply {
                            setVideoPath(file.absolutePath)
                            setOnPreparedListener { player -> player.isLooping = loopVideo; player.start(); videoPlaying = true }
                            setOnCompletionListener { videoPlaying = false; onVideoCompleted?.invoke() }
                            setOnErrorListener { _, _, _ -> videoPlaying = false; true }
                            videoView = this
                        }
                    },
                    update = { view ->
                        if (view.tag != file.absolutePath) {
                            view.tag = file.absolutePath
                            view.setVideoPath(file.absolutePath)
                            view.setOnPreparedListener { player -> player.isLooping = loopVideo; player.start(); videoPlaying = true }
                            view.setOnCompletionListener { videoPlaying = false; onVideoCompleted?.invoke() }
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
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Default.BrokenImage, "Media unavailable"); Text("Media unavailable", style = MaterialTheme.typography.labelSmall); TextButton(onClick = { reloadNonce++ }) { Text("Retry") } }
        }
        else -> Box(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

@Composable
fun FynxRemoteProfileAvatar(mediaId: String?, contentDescription: String?, modifier: Modifier = Modifier) {
    if (mediaId.isNullOrBlank()) Box(modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Text(contentDescription.orEmpty().trim().firstOrNull()?.uppercase() ?: "F", color = MaterialTheme.colorScheme.onPrimaryContainer) }
    else FynxRemoteMedia("/api/media/${mediaId.trim()}", "image", modifier.clip(RoundedCornerShape(50)))
}

private sealed interface MediaLoadResult { data class Image(val bitmap: android.graphics.Bitmap) : MediaLoadResult; data class Video(val file: File) : MediaLoadResult }

@Composable
fun FynxRemoteAudio(mediaUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resolvedUrl = remember(mediaUrl) { resolveFynxMediaUrl(context, mediaUrl) }
    val scope = rememberCoroutineScope()
    var player by remember(resolvedUrl) { mutableStateOf<MediaPlayer?>(null) }
    var localFile by remember(resolvedUrl) { mutableStateOf<File?>(null) }
    var playing by remember(resolvedUrl) { mutableStateOf(false) }
    var loading by remember(resolvedUrl) { mutableStateOf(false) }
    var error by remember(resolvedUrl) { mutableStateOf<String?>(null) }

    DisposableEffect(resolvedUrl) {
        onDispose { player?.release(); player = null; localFile = null }
    }

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(enabled = !loading, onClick = {
            if (playing) { player?.pause(); playing = false; return@IconButton }
            if (player != null) { player?.start(); playing = true; error = null; return@IconButton }
            loading = true
            error = null
            scope.launch {
                try {
                    val cached = remoteMediaCacheFile(context, resolvedUrl, ".audio")
                    val target = cached ?: File.createTempFile("fynx_audio_", ".audio", context.cacheDir)
                    val result = if (cached?.exists() == true && cached.length() > 0L) {
                        Result.success(FynxBackendClient.DownloadedMedia(null, cached.length()))
                    } else downloadRemoteMedia(context, resolvedUrl, target)
                    result.getOrThrow()
                    if (!target.exists() || target.length() == 0L) error("Downloaded voice media is empty")
                    val finalFile = target
                    val p = MediaPlayer().apply {
                        setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                        setDataSource(finalFile.absolutePath)
                        setOnPreparedListener {
                            loading = false
                            playing = true
                            it.start()
                        }
                        setOnCompletionListener {
                            playing = false
                            release()
                            player = null
                        }
                        setOnErrorListener { mp, _, _ ->
                            loading = false
                            playing = false
                            error = "Voice media could not be decoded or played."
                            runCatching { mp.reset() }
                            runCatching { mp.release() }
                            player = null
                            true
                        }
                    }
                    player = p
                    localFile = finalFile
                    p.prepareAsync()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    loading = false
                    playing = false
                    error = failure.message ?: "Voice Status could not be loaded."
                    player?.release()
                    player = null
                }
            }
        }) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause voice" else "Play voice") }
        Icon(Icons.Default.GraphicEq, "Voice Status")
        Column(Modifier.weight(1f)) {
            Text(if (loading) "Loading voice…" else if (playing) "Playing voice" else "Voice Status", style = MaterialTheme.typography.bodyMedium)
            error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 2) }
        }
    }
}
