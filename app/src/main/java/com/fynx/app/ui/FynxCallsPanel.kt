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

enum class FynxCallHistoryFilter { ALL, MISSED, VIDEO, VOICE }

@Composable
fun FynxCallsPanel(initialName: String? = null, initialVideo: Boolean = false) {
    val context = LocalContext.current
    var activeCall by remember { mutableStateOf(initialName) }
    var video by remember { mutableStateOf(initialVideo) }
    var session by remember {
        mutableStateOf(
            initialName?.let {
                val id = "incoming-${System.currentTimeMillis()}"
                FynxCallSession(
                    id = id,
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
    var filter by remember { mutableStateOf(FynxCallHistoryFilter.ALL) }

    LaunchedEffect(initialName, session?.id) {
        val current = session ?: return@LaunchedEffect
        if (initialName != null && calls.none { it.id == current.id }) {
            FynxCallsStore.add(context, FynxCallRecord(current.id, initialName, if (initialVideo) "Video call" else "Voice call", "Just now", missed = true, status = "Incoming"))
            calls = FynxCallsStore.load(context)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val current = session
        val required = if (video) listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else listOf(Manifest.permission.RECORD_AUDIO)
        if (current != null && required.all { result[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            session = FynxCallsFoundation.start(current)
            FynxCallsStore.updateStatus(context, current.id, "Connecting")
            calls = FynxCallsStore.load(context)
            permissionMessage = null
        } else {
            permissionMessage = if (video) "Camera and microphone access are needed for video calls." else "Microphone access is needed for voice calls."
            current?.let { FynxCallsStore.updateStatus(context, it.id, "Permission denied", missed = false) }
            activeCall = null
            session = null
            calls = FynxCallsStore.load(context)
        }
    }

    fun startCall(name: String, isVideo: Boolean) {
        video = isVideo
        activeCall = name
        permissionMessage = null
        val id = "call-${System.currentTimeMillis()}"
        val newSession = FynxCallSession(
            id = id,
            callerUsername = "me",
            participantUsernames = listOf(name),
            type = if (isVideo) FynxCallType.VIDEO else FynxCallType.VOICE
        )
        session = newSession
        FynxCallsStore.add(context, FynxCallRecord(id, name, if (isVideo) "Video call" else "Voice call", "Just now", status = "Outgoing"))
        calls = FynxCallsStore.load(context)
        val required = if (isVideo) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO)
        if (required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            session = FynxCallsFoundation.start(newSession)
            FynxCallsStore.updateStatus(context, id, "Connecting")
            calls = FynxCallsStore.load(context)
        } else {
            permissionLauncher.launch(required)
        }
    }

    if (activeCall != null && session != null) {
        FynxActiveCallPanel(
            name = activeCall!!,
            session = session!!,
            onAnswer = {
                val current = session!!
                session = FynxCallsFoundation.answer(current)
                FynxCallsStore.updateStatus(context, current.id, "Answered", missed = false)
                calls = FynxCallsStore.load(context)
            },
            onRetry = {
                val current = session!!
                val required = if (video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO)
                if (required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
                    session = FynxCallsFoundation.start(current)
                    FynxCallsStore.updateStatus(context, current.id, "Connecting")
                    calls = FynxCallsStore.load(context)
                } else {
                    permissionLauncher.launch(required)
                }
            },
            onToggleMicrophone = { session = FynxCallsFoundation.toggleMicrophone(session!!) },
            onToggleCamera = { session = FynxCallsFoundation.toggleCamera(session!!) },
            onSwitchCamera = { session = FynxCallsFoundation.switchCamera(session!!) },
            onToggleSpeaker = { session = FynxCallsFoundation.toggleSpeaker(session!!) },
            onEnd = {
                val current = session!!
                val wasIncoming = current.state == FynxCallState.RINGING
                session = FynxCallsFoundation.end(current)
                FynxCallsStore.updateStatus(context, current.id, if (wasIncoming) "Declined" else "Ended", missed = wasIncoming)
                calls = FynxCallsStore.load(context)
                activeCall = null
                session = null
            }
        )
        return
    }

    val filteredCalls = remember(calls, filter) {
        when (filter) {
            FynxCallHistoryFilter.ALL -> calls
            FynxCallHistoryFilter.MISSED -> calls.filter { it.missed }
            FynxCallHistoryFilter.VIDEO -> calls.filter { it.type == "Video call" }
            FynxCallHistoryFilter.VOICE -> calls.filter { it.type == "Voice call" }
        }
    }

    Column(Modifier.fillMaxSize().background(FynxDesign.Background).padding(16.dp)) {
        Text("Calls", style = MaterialTheme.typography.headlineSmall)
        Text("Voice and video calls", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(
                FynxCallHistoryFilter.ALL to "All",
                FynxCallHistoryFilter.MISSED to "Missed",
                FynxCallHistoryFilter.VIDEO to "Video",
                FynxCallHistoryFilter.VOICE to "Voice"
            ).forEach { (value, label) ->
                FilterChip(
                    selected = filter == value,
                    onClick = { filter = value },
                    label = { Text(label) }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        permissionMessage?.let {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(it, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Spacer(Modifier.height(10.dp))
        }
        if (filteredCalls.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Call, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("No calls here", style = MaterialTheme.typography.titleMedium)
                    Text("Your call history will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredCalls, key = { it.id }) { call ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                Icon(if (call.type == "Video call") Icons.Default.Videocam else Icons.Default.Call, "Call type", tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(call.name, style = MaterialTheme.typography.titleMedium)
                                Text("${call.type} • ${call.time}", style = MaterialTheme.typography.bodySmall, color = if (call.missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(call.status, style = MaterialTheme.typography.labelSmall, color = if (call.missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
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
    onToggleSpeaker: () -> Unit,
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
                FilledTonalIconButton(onClick = onToggleSpeaker) {
                    Icon(if (session.speakerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff, if (session.speakerEnabled) "Speaker on" else "Speaker off")
                }
                FloatingActionButton(onClick = onEnd) { Icon(Icons.Default.CallEnd, "End call") }
            }
        } else if (!isIncoming) {
            FloatingActionButton(onClick = onEnd) { Icon(Icons.Default.CallEnd, "End call") }
        }
        Spacer(Modifier.height(32.dp))
    }
}
