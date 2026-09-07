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
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private fun resolveFynxMediaUrl(context: android.content.Context, mediaUrl: String): String {
    val value = mediaUrl.trim()
    if (value.isBlank()) return value
    if (value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true)) return value
    return if (value.startsWith("/")) FynxBackendClient.baseUrl(context) + value else FynxBackendClient.baseUrl(context) + "/" + value
}

@Composable
fun FynxRemoteMedia(mediaUrl: String, type: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resolvedUrl = remember(mediaUrl) { resolveFynxMediaUrl(context, mediaUrl) }
    var kind by remember(resolvedUrl, type) { mutableStateOf("loading") }
    var bitmap by remember(resolvedUrl, type) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var localFile by remember(resolvedUrl, type) { mutableStateOf<File?>(null) }
    LaunchedEffect(resolvedUrl, type) {
        try {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val connection = (URL(resolvedUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10_000
                        readTimeout = 20_000
                        useCaches = false
                        setRequestProperty("Authorization", "Bearer ${FynxBackendClient.accessToken(context).orEmpty()}")
                    }
                    try {
                        if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                        val contentType = connection.contentType.orEmpty().lowercase()
                        val isVideo = type.equals("video", true) || (type.equals("auto", true) && contentType.startsWith("video/"))
                        if (isVideo) {
                            val extension = when {
                                contentType.contains("webm") -> ".webm"
                                contentType.contains("3gpp") -> ".3gp"
                                else -> ".mp4"
                            }
                            val file = File(context.cacheDir, "fynx_media_${resolvedUrl.hashCode()}$extension")
                            if (!file.exists() || file.length() == 0L) {
                                val temp = File(context.cacheDir, "${file.name}.part")
                                temp.delete()
                                connection.inputStream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                                if (!temp.renameTo(file)) {
                                    temp.delete()
                                    error("Unable to cache media")
                                }
                            }
                            MediaLoadResult.Video(file)
                        } else {
                            val decoded = connection.inputStream.use { BitmapFactory.decodeStream(it) }
                            if (decoded == null) error("Unable to decode media")
                            MediaLoadResult.Image(decoded)
                        }
                    } finally {
                        connection.disconnect()
                    }
                }
            }.getOrElse { throw it }
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
        "error" -> Box(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) { Icon(Icons.Default.BrokenImage, "Media unavailable") }
        else -> Box(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) { CircularProgressIndicator() }
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
    var player by remember(resolvedUrl) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(resolvedUrl) { mutableStateOf(false) }
    var loading by remember(resolvedUrl) { mutableStateOf(false) }
    DisposableEffect(resolvedUrl) { onDispose { player?.release(); player = null } }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(enabled = !loading, onClick = {
            if (playing) { player?.pause(); playing = false; return@IconButton }
            if (player == null) {
                loading = true
                val p = MediaPlayer()
                player = p
                runCatching {
                    p.setDataSource(context, Uri.parse(resolvedUrl), mapOf("Authorization" to "Bearer ${FynxBackendClient.accessToken(context).orEmpty()}"))
                    p.setOnPreparedListener { loading = false; playing = true; it.start() }
                    p.setOnCompletionListener { playing = false }
                    p.setOnErrorListener { _, _, _ -> loading = false; playing = false; true }
                    p.prepareAsync()
                }.onFailure {
                    loading = false
                    player?.release()
                    player = null
                }
            } else {
                player?.start()
                playing = true
            }
        }) { Icon(if (playing) Icons.Default.GraphicEq else Icons.Default.PlayArrow, if (playing) "Pause" else "Play") }
        Text(if (loading) "Loading voice message…" else if (playing) "Playing voice message" else "Voice message", color = FynxDesign.TextSecondary)
    }
}
