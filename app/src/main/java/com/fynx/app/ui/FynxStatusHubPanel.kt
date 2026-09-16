package com.fynx.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import java.util.UUID

/** Single Status/Stories surface. The old local-only Stories panel is no longer rendered beside the backend feed. */
@Composable
fun FynxStatusHubPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var composing by remember { mutableStateOf(false) }
    var cameraOpen by remember { mutableStateOf(false) }
    var publishingCameraStatus by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var timelineRefreshKey by remember { mutableIntStateOf(0) }

    fun publishCapturedStatus(uri: Uri, type: String) {
        if (publishingCameraStatus) return
        publishingCameraStatus = true; cameraError = null
        scope.launch {
            try {
                val auth = FynxAuthStore.load(context)
                val username = auth.username?.removePrefix("@").orEmpty().ifBlank { "preview" }
                val mime = context.contentResolver.getType(uri) ?: if (type == "video") "video/mp4" else "image/jpeg"
                val mediaId = FynxStatusClient.uploadMedia(context, uri, mime).getOrElse { cameraError = it.message ?: "Status media upload failed."; return@launch }
                val statusType = if (type == "video") FynxStatusType.VIDEO else FynxStatusType.PHOTO
                val now = System.currentTimeMillis()
                val status = FynxStatus(UUID.randomUUID().toString(), username, username, statusType, null, now, now + FYNX_STATUS_EXPIRY_MS, FynxStatusTextStyle(0xFF111111, 0xFFFFFFFF, FynxStatusTextFont.CLASSIC, 1), true, 0L, FynxStatusAudience.FRIENDS)
                FynxStatusClient.create(context, status, mediaId).getOrElse { cameraError = it.message ?: "Status publishing failed."; return@launch }
                FynxStatusStore.save(context, status.copy(contentUri = "/api/media/$mediaId"))
                cameraOpen = false; timelineRefreshKey++
            } finally { publishingCameraStatus = false }
        }
    }

    LaunchedEffect(composing, cameraOpen) { if (!composing && !cameraOpen) timelineRefreshKey++ }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            if (composing) FynxMatureStatusComposerPanel(onClose = { composing = false }) else key(timelineRefreshKey) { FynxStatusTimelinePanel() }
            if (!composing) {
                Row(modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 26.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SmallFloatingActionButton(onClick = { if (!publishingCameraStatus) { cameraError = null; cameraOpen = true } }) { Icon(Icons.Default.PhotoCamera, contentDescription = "Open Status camera") }
                    FloatingActionButton(onClick = { composing = true }) { Icon(Icons.Default.Add, contentDescription = "Create Status") }
                }
            }
            cameraError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 102.dp, start = 18.dp, end = 18.dp)) }
        }
    }

    if (cameraOpen) Dialog(onDismissRequest = { if (!publishingCameraStatus) cameraOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                FynxCameraCapturePanel(onCaptured = { uri, type -> publishCapturedStatus(uri, type) }, onDismiss = { if (!publishingCameraStatus) cameraOpen = false })
            }
        }
    }
}