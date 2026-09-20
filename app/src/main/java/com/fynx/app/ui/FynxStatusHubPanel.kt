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

/** Single Status/Stories surface. The old local-only Stories panel is no longer rendered beside the backend feed. */
@Composable
fun FynxStatusHubPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var composing by remember { mutableStateOf(false) }
    var cameraOpen by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var capturedUri by remember { mutableStateOf<Uri?>(null) }
    var capturedType by remember { mutableStateOf<FynxStatusType?>(null) }
    var timelineRefreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(composing, cameraOpen) { if (!composing && !cameraOpen) timelineRefreshKey++ }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            if (composing) FynxMatureStatusComposerPanel(initialMediaUri = capturedUri, initialType = capturedType, onClose = { capturedUri = null; capturedType = null; composing = false }) else key(timelineRefreshKey) { FynxStatusTimelinePanel() }
            if (!composing) Row(modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 26.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallFloatingActionButton(onClick = { cameraError = null; cameraOpen = true }) { Icon(Icons.Default.PhotoCamera, contentDescription = "Open Status camera") }
                FloatingActionButton(onClick = { composing = true }) { Icon(Icons.Default.Add, contentDescription = "Create Status") }
            }
            cameraError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 102.dp, start = 18.dp, end = 18.dp)) }
        }
    }
    if (cameraOpen) Dialog(onDismissRequest = { cameraOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) { FynxCameraCapturePanel(onCaptured = { uri, type -> capturedUri = uri; capturedType = if (type == "video") FynxStatusType.VIDEO else FynxStatusType.PHOTO; cameraOpen = false; composing = true }, onDismiss = { cameraOpen = false }) }
        }
    }
}