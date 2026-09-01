package com.fynx.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private enum class FynxCallState { RINGING, CONNECTING, ACTIVE }

@Composable
fun FynxCallsPanel(initialName: String? = null, initialVideo: Boolean = false) {
    val context = LocalContext.current
    var activeCall by remember { mutableStateOf(initialName) }
    var video by remember { mutableStateOf(initialVideo) }
    var callState by remember { mutableStateOf(if (initialName != null) FynxCallState.RINGING else null) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var calls by remember { mutableStateOf(FynxCallsStore.load(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val required = if (video) {
            listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            listOf(Manifest.permission.RECORD_AUDIO)
        }
        if (required.all { result[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            callState = FynxCallState.CONNECTING
            permissionMessage = null
        } else {
            permissionMessage = if (video) "Camera and microphone access are needed for video calls." else "Microphone access is needed for voice calls."
            activeCall = null
            callState = null
        }
    }

    fun startCall(name: String, isVideo: Boolean) {
        video = isVideo
        activeCall = name
        callState = FynxCallState.RINGING
        permissionMessage = null
        val required = if (isVideo) {
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
        if (required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            callState = FynxCallState.CONNECTING
        } else {
            permissionLauncher.launch(required)
        }
        val record = FynxCallRecord(
            id = "call-${System.currentTimeMillis()}",
            name = name,
            type = if (isVideo) "Video call" else "Voice call",
            time = "Just now"
        )
        FynxCallsStore.add(context, record)
        calls = FynxCallsStore.load(context)
    }

    if (activeCall != null && callState != null) {
        FynxActiveCallPanel(
            name = activeCall!!,
            video = video,
            state = callState!!,
            onRetry = {
                val required = if (video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO)
                if (required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
                    callState = FynxCallState.CONNECTING
                } else {
                    permissionLauncher.launch(required)
                }
            },
            onEnd = { activeCall = null; callState = null }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(FynxDesign.Background).padding(16.dp)) {
        Text("Calls", style = MaterialTheme.typography.headlineSmall)
        Text("Voice and video calls", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        permissionMessage?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(it, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Spacer(Modifier.height(10.dp))
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(calls, key = { it.id }) { call ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(if (call.type == "Video call") Icons.Default.Videocam else Icons.Default.Call, "Call type", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(call.name, style = MaterialTheme.typography.titleMedium)
                            Text("${call.type} • ${call.time}", style = MaterialTheme.typography.bodySmall, color = if (call.missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { startCall(call.name, call.type == "Video call") }) {
                            Icon(if (call.type == "Video call") Icons.Default.Videocam else Icons.Default.Call, "Call ${call.name}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FynxActiveCallPanel(
    name: String,
    video: Boolean,
    state: FynxCallState,
    onRetry: () -> Unit,
    onEnd: () -> Unit
) {
    var muted by remember { mutableStateOf(false) }
    var speakerOn by remember { mutableStateOf(false) }
    var cameraOn by remember { mutableStateOf(video) }
    val isConnecting = state != FynxCallState.ACTIVE

    Column(Modifier.fillMaxSize().background(FynxDesign.Background), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(44.dp))
        Text(name, style = MaterialTheme.typography.headlineSmall)
        Text(
            when (state) {
                FynxCallState.RINGING -> "Calling…"
                FynxCallState.CONNECTING -> "Connecting…"
                FynxCallState.ACTIVE -> if (video) "Video call" else "Voice call"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(30.dp))
        Box(Modifier.size(190.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.weight(1f))
        if (isConnecting) {
            Text("Call connection is not available yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onRetry) { Text("Retry connection") }
            Spacer(Modifier.height(18.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { muted = !muted }, enabled = !isConnecting) {
                Icon(if (muted) Icons.Default.MicOff else Icons.Default.Mic, "Mute")
            }
            FilledTonalIconButton(onClick = { speakerOn = !speakerOn }, enabled = !isConnecting) {
                Icon(if (speakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff, "Speaker")
            }
            if (video) {
                FilledTonalIconButton(onClick = { cameraOn = !cameraOn }, enabled = !isConnecting) {
                    Icon(if (cameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff, "Camera")
                }
            }
            FloatingActionButton(onClick = onEnd) { Icon(Icons.Default.CallEnd, "End call") }
        }
        Spacer(Modifier.height(32.dp))
    }
}
