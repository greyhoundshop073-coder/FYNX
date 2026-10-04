package com.fynx.app.ui

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
    onOpenMedia: () -> Unit = {},
) {
    var aspect by remember(file, type) { mutableFloatStateOf(1f) }
    val isVideo = type.equals("video", ignoreCase = true)

    LaunchedEffect(file, type) {
        aspect = withContext(Dispatchers.IO) {
            FynxHomeMediaSizing.aspect(file, type)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp, max = 720.dp)
            .aspectRatio(aspect, matchHeightConstraintsFirst = false)
            .clickable(onClick = onOpenMedia),
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
                        // Home media must not autoplay on feed entry.
                        setOnPreparedListener { mp -> mp.isLooping = true }
                    }
                },
                modifier = Modifier.fillMaxSize(),
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
                    runCatching {
                        val bounds = android.graphics.BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                        val sample = FynxHomeMediaSizing.sampleSize(bounds.outWidth, bounds.outHeight)
                        val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                        android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)
                    }.getOrNull()
                }
            }
            bitmap?.let { image ->
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = "Post media",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}
