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

@Composable
fun FynxCallsPanel(initialName: String? = null, initialVideo: Boolean = false) {
    val context = LocalContext.current
    var activeCall by remember { mutableStateOf(initialName) }
    var video by remember { mutableStateOf(initialVideo) }
    var session by remember {
        mutableStateOf(
            initialName?.let {
                FynxCallSession(
                    id = "incoming-${System.currentTimeMillis()}",
                    callerUsername = it,
                    participantUsernames = listOf(it),
                    type = if (initialVideo) FynxCallType.VIDEO else FynxCallType.VOICE,
                    state = FynxCallState.RINGING
                )
            }
        )
    }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    var calls by remember { mutableStateOf(FynxCallsStore.load(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val required = if (video) listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else listOf(Manifest.permission.RECORD_AUDIO)
        if (required.all { result[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            session = session?.let(FynxCallsFoundation::start)
            permissionMessage = null
        } else {
            permissionMessage = if (video) "Camera and microphone access are needed for video calls." else "Microphone access is needed for voice calls."
            activeCall = null
            session = null
        }
    }

    fun startCall(name: String, isVideo: Boolean) {
        video = isVideo
        activeCall = name
        permissionMessage = null
        val newSession = FynxCallSession(
            id = "call-${System.currentTimeMillis()}",
            callerUsername = "me",
            participantUsernames = listOf(name),
            type = if (isVideo) FynxCallType.VIDEO else FynxCallType.VOICE
        )
        session = newSession
        val required = if (isVideo) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO)
        if (required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            session = FynxCallsFoundation.start(newSession)
        } else {
            permissionLauncher.launch(required)
        }
        FynxCallsStore.add(context, FynxCallRecord("call-${System.currentTimeMillis()}", name, if (isVideo) "Video call" else "Voice call", "Just now"))
        calls = FynxCallsStore.load(context)
    }

    if (activeCall != null && session != null) {
        FynxActiveCallPanel(
            name = activeCall!!,
            session = session!!,
            onAnswer = { session = FynxCallsFoundation.answer(session!!) },
            onRetry = {
                val required = if (video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO)
                if (required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
                    session = FynxCallsFoundation.start(session!!)
                } else {
                    permissionLauncher.launch(required)
                }
            },
            onToggleMicrophone = { session = FynxCallsFoundation.toggleMicrophone(session!!) },
            onToggleCamera = { session = FynxCallsFoundation.toggleCamera(session!!) },
            onSwitchCamera = { session = FynxCallsFoundation.switchCamera(session!!) },
            onEnd = {
                session = FynxCallsFoundation.end(session!!)
                activeCall = null
                session = null
            }
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
    session: FynxCallSession,
    onAnswer: () -> Unit,
    onRetry: () -> Unit,
    onToggleMicrophone: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEnd: () -> Unit
) {
    val isConnecting = session.state == FynxCallState.CONNECTING
    val isIncoming = session.state == FynxCallState.RINGING
    val isConnected = session.state == FynxCallState.CONNECTED
    val isVideo = session.type == FynxCallType.VIDEO

    Column(Modifier.fillMaxSize().background(FynxDesign.Background), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(44.dp))
        Text(name, style = MaterialTheme.typography.headlineSmall)
        Text(
            when (session.state) {
                FynxCallState.IDLE -> "Ready"
                FynxCallState.RINGING -> "Incoming ${if (isVideo) "video" else "voice"} call"
                FynxCallState.CONNECTING -> "Connecting…"
                FynxCallState.CONNECTED -> if (isVideo) "Video call" else "Voice call"
                FynxCallState.ENDED -> "Call ended"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(30.dp))
        Box(Modifier.size(190.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.weight(1f))

        if (isIncoming) {
            Text("Answer this call?", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onEnd) { Text("Decline") }
                Button(onClick = onAnswer) { Icon(Icons.Default.Call, null); Spacer(Modifier.width(6.dp)); Text("Answer") }
            }
            Spacer(Modifier.height(24.dp))
        } else if (isConnecting) {
            Text("Call connection is not available yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onRetry) { Text("Retry connection") }
            Spacer(Modifier.height(18.dp))
        }

        if (isConnected) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onToggleMicrophone) {
                    Icon(if (session.microphoneEnabled) Icons.Default.Mic else Icons.Default.MicOff, "Mute")
                }
                if (isVideo) {
                    FilledTonalIconButton(onClick = onToggleCamera) {
                        Icon(if (session.cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff, "Camera")
                    }
                    FilledTonalIconButton(onClick = onSwitchCamera) { Icon(Icons.Default.Videocam, "Switch camera") }
                }
                FilledTonalIconButton(onClick = {}) { Icon(Icons.Default.VolumeUp, "Speaker") }
                FloatingActionButton(onClick = onEnd) { Icon(Icons.Default.CallEnd, "End call") }
            }
        } else if (!isIncoming) {
            FloatingActionButton(onClick = onEnd) { Icon(Icons.Default.CallEnd, "End call") }
        }
        Spacer(Modifier.height(32.dp))
    }
}
