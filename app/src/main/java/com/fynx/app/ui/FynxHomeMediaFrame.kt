package com.fynx.app.ui

import android.media.MediaMetadataRetriever
import android.view.ViewGroup
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Home feed media surface: preserves source dimensions and avoids forced crop. */
@Composable
fun FynxHomeMediaFrame(
    file: File,
    type: String,
    modifier: Modifier = Modifier,
    onOpenMedia: () -> Unit = {}
) {
    var aspect by remember(file) { mutableFloatStateOf(1f) }
    val isVideo = type.equals("video", ignoreCase = true)

    LaunchedEffect(file, isVideo) {
        aspect = withContext(Dispatchers.IO) {
            if (isVideo) {
                runCatching {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(file.absolutePath)
                        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 1f
                        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 1f
                        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                        if (rotation == 90 || rotation == 270) height / width else width / height
                    } finally {
                        retriever.release()
                    }
                }.getOrDefault(1f)
            } else {
                runCatching {
                    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
                    if (options.outWidth > 0 && options.outHeight > 0) options.outWidth.toFloat() / options.outHeight.toFloat() else 1f
                }.getOrDefault(1f)
            }
        }.coerceIn(0.05f, 20f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp, max = 720.dp)
            .aspectRatio(aspect, matchHeightConstraintsFirst = false)
            .clickable(onClick = onOpenMedia)
    ) {
        if (isVideo) {
            var player by remember(file) { mutableStateOf<VideoView?>(null) }
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        player = this
                        layoutParams = ViewGroup.LayoutParams(-1, -1)
                        keepScreenOn = true
                        setVideoPath(file.absolutePath)
                        setOnPreparedListener { mp -> mp.isLooping = true }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            DisposableEffect(file) {
                onDispose {
                    player?.stopPlayback()
                    player?.keepScreenOn = false
                    player = null
                }
            }
        } else {
            var bitmap by remember(file) { mutableStateOf<android.graphics.Bitmap?>(null) }
            LaunchedEffect(file) {
                bitmap = withContext(Dispatchers.IO) {
                    runCatching { android.graphics.BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
                }
            }
            bitmap?.let { image ->
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = "Post media",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
