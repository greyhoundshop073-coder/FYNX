package com.fynx.app.ui

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    return File(directory, "media_${resolvedUrl.hashCode()}$extension")
}

private fun mediaExtension(contentType: String?, fallback: String): String = when {
    contentType.orEmpty().contains("webm") -> ".webm"
    contentType.orEmpty().contains("3gpp") -> ".3gp"
    contentType.orEmpty().contains("mpeg") || contentType.orEmpty().contains("mp3") -> ".mp3"
    contentType.orEmpty().contains("wav") -> ".wav"
    contentType.orEmpty().contains("ogg") -> ".ogg"
    contentType.orEmpty().contains("m4a") || contentType.orEmpty().contains("mp4") -> ".mp4"
    contentType.orEmpty().contains("png") -> ".png"
    contentType.orEmpty().contains("webp") -> ".webp"
    contentType.orEmpty().contains("gif") -> ".gif"
    contentType.orEmpty().contains("jpeg") || contentType.orEmpty().contains("jpg") -> ".jpg"
    else -> fallback
}

private suspend fun downloadRemoteMedia(context: android.content.Context, resolvedUrl: String, destination: File): Result<FynxBackendClient.DownloadedMedia> =
    FynxBackendClient.downloadToFile(context, resolvedUrl, destination, MAX_REMOTE_MEDIA_BYTES)

@Composable
fun FynxRemoteMedia(mediaUrl: String, type: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resolvedUrl = remember(mediaUrl) { resolveFynxMediaUrl(context, mediaUrl) }
    var kind by remember(resolvedUrl, type) { mutableStateOf("loading") }
    var bitmap by remember(resolvedUrl, type) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var localFile by remember(resolvedUrl, type) { mutableStateOf<File?>(null) }
    var reloadNonce by remember(resolvedUrl, type) { mutableIntStateOf(0) }
    LaunchedEffect(resolvedUrl, type, reloadNonce) {
        kind = "loading"
        bitmap = null
        localFile = null
        try {
            val loaded = withContext(Dispatchers.IO) {
                val isKnownVideo = type.equals("video", true)
                val cacheTarget = if (isKnownVideo || type.equals("auto", true)) remoteMediaCacheFile(context, resolvedUrl, ".media") else null
                val target = cacheTarget ?: File.createTempFile("fynx_media_", ".media", context.cacheDir)
                val result = if (cacheTarget?.exists() == true && cacheTarget.length() > 0L) {
                    Result.success(FynxBackendClient.DownloadedMedia(null, cacheTarget.length()))
                } else {
                    downloadRemoteMedia(context, resolvedUrl, target)
                }
                result.getOrThrow().let { downloaded ->
                    val contentType = downloaded.contentType.orEmpty()
                    val isVideo = isKnownVideo || (type.equals("auto", true) && contentType.startsWith("video/"))
                    if (isVideo) {
                        val finalFile = if (cacheTarget != null) {
                            val extension = mediaExtension(contentType, ".mp4")
                            val desired = if (target.extension.equals(extension.removePrefix("."), true)) target else File(target.parentFile, "media_${resolvedUrl.hashCode()}$extension")
                            if (target != desired && target.exists() && !target.renameTo(desired)) throw IllegalStateException("Unable to finalize cached media")
                            desired
                        } else target
                        MediaLoadResult.Video(finalFile)
                    } else {
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            kind = "error"
        }
    }
    when (kind) {
        "image" -> bitmap?.let { Image(it.asImageBitmap(), "Media", modifier.clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop) }
        "video" -> localFile?.let { file -> AndroidView(factory = { ctx -> android.widget.VideoView(ctx).apply { setVideoPath(file.absolutePath); setOnPreparedListener { player -> player.isLooping = true; start() } } }, modifier = modifier.clip(RoundedCornerShape(14.dp))) }
        "error" -> Box(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.BrokenImage, "Media unavailable")
                Text("Media unavailable", style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { reloadNonce++ }) { Text("Retry") }
            }
        }
        else -> Box(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

@Composable
fun FynxRemoteProfileAvatar(mediaId: String?, contentDescription: String?, modifier: Modifier = Modifier) {
    if (mediaId.isNullOrBlank()) {
        Box(modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(contentDescription.orEmpty().trim().firstOrNull()?.uppercase() ?: "F", color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    } else {
        FynxRemoteMedia(
            mediaUrl = "/api/media/${mediaId.trim()}",
            type = "image",
            modifier = modifier.clip(RoundedCornerShape(50))
        )
    }
}

private sealed interface MediaLoadResult {
    data class Image(val bitmap: android.graphics.Bitmap) : MediaLoadResult
    data class Video(val file: File) : MediaLoadResult
}

@Composable
fun FynxRemoteAudio(mediaUrl: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resolvedUrl = remember(mediaUrl) { resolveFynxMediaUrl(context, mediaUrl) }
    val scope = rememberCoroutineScope()
    var player by remember(resolvedUrl) { mutableStateOf<MediaPlayer?>(null) }
    var localFile by remember(resolvedUrl) { mutableStateOf<File?>(null) }
    var playing by remember(resolvedUrl) { mutableStateOf(false) }
    var loading by remember(resolvedUrl) { mutableStateOf(false) }
    DisposableEffect(resolvedUrl) { onDispose { player?.release(); player = null; localFile = null } }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(enabled = !loading, onClick = {
            if (playing) { player?.pause(); playing = false; return@IconButton }
            if (player != null) { player?.start(); playing = true; return@IconButton }
            loading = true
            scope.launch {
                try {
                    val cached = remoteMediaCacheFile(context, resolvedUrl, ".audio")
                    val target = cached ?: File.createTempFile("fynx_audio_", ".audio", context.cacheDir)
                    val result = if (cached?.exists() == true && cached.length() > 0L) Result.success(FynxBackendClient.DownloadedMedia(null, cached.length())) else downloadRemoteMedia(context, resolvedUrl, target)
                    val downloaded = result.getOrThrow()
                    val finalFile = if (cached != null) {
                        val extension = mediaExtension(downloaded.contentType, ".audio")
                        val desired = if (target.extension.equals(extension.removePrefix("."), true)) target else File(target.parentFile, "media_${resolvedUrl.hashCode()}$extension")
                        if (target != desired && target.exists() && !target.renameTo(desired)) throw IllegalStateException("Unable to finalize cached audio")
                        desired
                    } else target
                    val p = MediaPlayer()
                    p.setDataSource(finalFile.absolutePath)
                    p.setOnPreparedListener { loading = false; playing = true; it.start() }
                    p.setOnCompletionListener { playing = false }
                    p.setOnErrorListener { _, _, _ -> loading = false; playing = false; true }
                    p.prepareAsync()
                    player = p
                    localFile = finalFile
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    loading = false
                }
            }
        }) { Icon(if (playing) Icons.Default.GraphicEq else Icons.Default.PlayArrow, if (playing) "Pause" else "Play") }
        Text(if (loading) "Loading voice message…" else if (playing) "Playing voice message" else "Voice message", color = FynxDesign.TextSecondary)
    }
}
