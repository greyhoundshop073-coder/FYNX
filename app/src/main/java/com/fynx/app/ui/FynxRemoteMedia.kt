package com.fynx.app.ui

import android.graphics.BitmapFactory
import android.media.MediaPlayer
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
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(resolvedUrl.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return File(directory, "media_${digest.take(32)}$extension")
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
                // Auto media cannot safely cache a video because the cache does not persist content type.
                // Decode the authoritative response first so a cached video can never be mistaken for an image.
                val cacheTarget = if (isKnownVideo) remoteMediaCacheFile(context, resolvedUrl, ".media") else null
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
                        // Keep the stable .media cache target. VideoView reads the file bytes, not the suffix,
                        // and retaining the same path makes subsequent cache hits actually hit.
                        MediaLoadResult.Video(target)
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
                    result.getOrThrow()
                    // Keep the stable .audio cache target; MediaPlayer uses the file bytes and this avoids
                    // renaming away from the path used for future cache lookups.
                    val finalFile = target
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
