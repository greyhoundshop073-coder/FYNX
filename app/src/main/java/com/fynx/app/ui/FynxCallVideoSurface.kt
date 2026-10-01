package com.fynx.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.VideoTrack
import org.webrtc.SurfaceViewRenderer

@Composable
fun FynxCallVideoSurface(track: VideoTrack?, modifier: Modifier = Modifier, mirror: Boolean = false) {
    val context = LocalContext.current
    val eglBase = remember { EglBase.create() }
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (track == null) {
            Text("Waiting for video…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AndroidView(
                factory = { ctx: Context ->
                    SurfaceViewRenderer(ctx).apply {
                        init(eglBase.eglBaseContext, null)
                        setEnableHardwareScaler(true)
                        setMirror(mirror)
                    }
                },
                update = { renderer ->
                    renderer.setMirror(mirror)
                    renderer.clearImage()
                    track.addSink(renderer)
                },
                onRelease = { renderer ->
                    track.removeSink(renderer)
                    renderer.release()
                }
            )
        }
    }
    DisposableEffect(Unit) {
        onDispose { eglBase.release() }
    }
}
