package com.fynx.app.ui

import android.net.Uri
import android.widget.ImageView
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

/** Capture result gate: keeps media in a real preview state before the caller sends it. */
@Composable
fun FynxMediaComposer(
    uri: Uri,
    mediaType: String,
    onSend: (Uri, String) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    DisposableEffect(uri, mediaType) {
        onDispose { }
    }
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            if (mediaType.equals("video", true)) "Video ready" else "Photo ready",
            style = MaterialTheme.typography.titleLarge
        )
        Text("Review your media before sending.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (mediaType.equals("video", true)) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            tag = uri.toString()
                            setVideoURI(uri)
                            setMediaController(MediaController(ctx))
                            setOnPreparedListener { player -> player.isLooping = true; player.start() }
                        }
                    },
                    update = { view -> if (view.tag != uri.toString()) { view.tag = uri.toString(); view.setVideoURI(uri) } }
                )
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            adjustViewBounds = true
                            setImageURI(uri)
                        }
                    },
                    update = { it.setImageURI(uri) }
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) { Text("Retake") }
            Button(
                onClick = { onSend(uri, mediaType) },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) { Text("Send") }
        }
    }
}
