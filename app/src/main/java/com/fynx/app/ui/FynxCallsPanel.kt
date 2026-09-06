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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

enum class FynxCallHistoryFilter { ALL, MISSED, VIDEO, VOICE }

@Composable
fun FynxCallsPanel(initialName: String? = null, initialVideo: Boolean = false, initialOutgoing: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeCall by remember { mutableStateOf(initialName?.removePrefix("@").orEmpty().ifBlank { initialName }) }
    var video by remember { mutableStateOf(initialVideo) }
    var targetUserId by remember { mutableStateOf<String?>(null) }
    var targetUsername by remember { mutableStateOf(initialName?.removePrefix("@")?.trim()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var calls by remember { mutableStateOf(FynxCallsStore.load(context)) }
    var filter by remember { mutableStateOf(FynxCallHistoryFilter.ALL) }
    var realtimeState by remember { mutableStateOf(FynxRealtimeClient.State.DISCONNECTED) }
    var session by remember { mutableStateOf(initialName?.let { name -> FynxCallSession("call-${System.currentTimeMillis()}", if (initialOutgoing) "me" else name, listOf(name), if (initialVideo) FynxCallType.VIDEO else FynxCallType.VOICE, if (initialOutgoing) FynxCallState.CONNECTING else FynxCallState.RINGING) }) }

    val realtimeClient = remember {
        FynxRealtimeClient(
            context = context,
            onMessage = {},
            onStateChanged = { realtimeState = it },
            onEvent = { event ->
                if (event is FynxRealtimeClient.Event.Call) {
                    when (event.signalType) {
                        "invite" -> if (!initialOutgoing && session == null) {
                            val incomingName = event.fromUsername?.removePrefix("@").orEmpty().ifBlank { event.fromUserId }
                            video = event.callType.equals("video", true)
                            targetUserId = event.fromUserId
                            targetUsername = incomingName
                            activeCall = incomingName
                            session = FynxCallSession(event.callId, event.fromUsername ?: event.fromUserId, listOf(incomingName), if (video) FynxCallType.VIDEO else FynxCallType.VOICE, FynxCallState.RINGING)
                            FynxCallsStore.add(context, FynxCallRecord(event.callId, "@$incomingName", if (video) "Video call" else "Voice call", "Just now", missed = true, status = "Incoming"))
                            calls = FynxCallsStore.load(context)
                        }
                        "accept" -> if (session?.id == event.callId) {
                            session = FynxCallsFoundation.connect(session!!)
                            FynxCallsStore.updateStatus(context, event.callId, "Accepted", missed = false)
                            calls = FynxCallsStore.load(context)
                        }
                        "reject", "end" -> if (session?.id == event.callId) {
                            FynxCallsStore.updateStatus(context, event.callId, if (event.signalType == "reject") "Declined" else "Ended", missed = false)
                            calls = FynxCallsStore.load(context)
                            session = null
                            activeCall = null
                        }
                        "busy" -> if (session?.id == event.callId) {
                            errorMessage = "@$targetUsername is already on another call."
                            FynxCallsStore.updateStatus(context, event.callId, "Busy", missed = false)
                            calls = FynxCallsStore.load(context)
                            session = null
                            activeCall = null
                        }
                    }
                }
            }
        )
    }

    DisposableEffect(Unit) {
        realtimeClient.startRealtime()
        onDispose { realtimeClient.stopRealtime() }
    }

    suspend fun resolveUser(username: String): String? = FynxSocialClient.searchUsers(context, username).getOrNull()?.firstOrNull { it.username.equals(username, true) }?.id

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val current = session ?: return@rememberLauncherForActivityResult
        val required = if (video) listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else listOf(Manifest.permission.RECORD_AUDIO)
        if (required.all { result[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            targetUserId?.let { realtimeClient.sendCallInvite(current.id, it, video) }
            FynxCallsStore.updateStatus(context, current.id, "Calling")
            calls = FynxCallsStore.load(context)
        } else {
            errorMessage = if (video) "Camera and microphone access are needed for video calls." else "Microphone access is needed for voice calls."
            FynxCallsStore.updateStatus(context, current.id, "Permission denied", missed = false)
            activeCall = null; session = null; calls = FynxCallsStore.load(context)
        }
    }

    fun beginOutgoing(name: String, isVideo: Boolean) {
        val username = name.removePrefix("@").trim()
        if (username.isBlank()) return
        video = isVideo; activeCall = username; targetUsername = username; errorMessage = null
        scope.launch {
            val resolvedId = resolveUser(username)
            if (resolvedId == null) { errorMessage = "We couldn't find @$username. Please check the username and try again."; activeCall = null; session = null; return@launch }
            targetUserId = resolvedId
            val id = "call-${System.currentTimeMillis()}"
            val newSession = FynxCallSession(id, "me", listOf("@$username"), if (isVideo) FynxCallType.VIDEO else FynxCallType.VOICE, FynxCallState.CONNECTING)
            session = newSession
            FynxCallsStore.add(context, FynxCallRecord(id, "@$username", if (isVideo) "Video call" else "Voice call", "Just now", status = "Outgoing"))
            calls = FynxCallsStore.load(context)
            val required = if (isVideo) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO)
            if (required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
                realtimeClient.sendCallInvite(id, resolvedId, isVideo)
                FynxCallsStore.updateStatus(context, id, "Calling")
                calls = FynxCallsStore.load(context)
            } else permissionLauncher.launch(required)
        }
    }

    LaunchedEffect(initialName, initialOutgoing) { if (initialOutgoing && !initialName.isNullOrBlank()) beginOutgoing(initialName, initialVideo) }

    if (activeCall != null && session != null) {
        FynxActiveCallPanel(name = activeCall!!, session = session!!, realtimeState = realtimeState,
            onAnswer = { val current = session!!; val callerId = targetUserId ?: current.callerUsername; realtimeClient.sendCallAccept(current.id, callerId, video); session = FynxCallsFoundation.answer(current); FynxCallsStore.updateStatus(context, current.id, "Answered", missed = false); calls = FynxCallsStore.load(context) },
            onRetry = { targetUsername?.let { beginOutgoing(it, video) } },
            onToggleMicrophone = { session = FynxCallsFoundation.toggleMicrophone(session!!) },
            onToggleCamera = { session = FynxCallsFoundation.toggleCamera(session!!) },
            onSwitchCamera = { session = FynxCallsFoundation.switchCamera(session!!) },
            onToggleSpeaker = { session = FynxCallsFoundation.toggleSpeaker(session!!) },
            onEnd = { val current = session!!; targetUserId?.let { realtimeClient.sendCallEnd(current.id, it, video) }; val incoming = current.state == FynxCallState.RINGING; session = FynxCallsFoundation.end(current); FynxCallsStore.updateStatus(context, current.id, if (incoming) "Declined" else "Ended", missed = incoming); calls = FynxCallsStore.load(context); activeCall = null; session = null }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(FynxDesign.Background).padding(16.dp)) {
        Text("Calls", style = MaterialTheme.typography.headlineSmall); Text("Voice and video calls", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (realtimeState == FynxRealtimeClient.State.FAILED) Text("Call connection is reconnecting…", style = MaterialTheme.typography.bodySmall)
        errorMessage?.let { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.padding(top = 10.dp)) { Text(it, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer) } }
        Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(FynxCallHistoryFilter.ALL to "All", FynxCallHistoryFilter.MISSED to "Missed", FynxCallHistoryFilter.VIDEO to "Video", FynxCallHistoryFilter.VOICE to "Voice").forEach { (value, label) -> FilterChip(filter == value, onClick = { filter = value }, label = { Text(label) }) } }
        Spacer(Modifier.height(10.dp))
        val filtered = when (filter) { FynxCallHistoryFilter.ALL -> calls; FynxCallHistoryFilter.MISSED -> calls.filter { it.missed }; FynxCallHistoryFilter.VIDEO -> calls.filter { it.type == "Video call" }; FynxCallHistoryFilter.VOICE -> calls.filter { it.type == "Voice call" } }
        if (filtered.isEmpty()) Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Call, null); Spacer(Modifier.height(8.dp)); Text("No calls here", style = MaterialTheme.typography.titleMedium); Text("Your call history will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(filtered, key = { it.id }) { call -> Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (call.type == "Video call") Icons.Default.Videocam else Icons.Default.Call, null); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(call.name, style = MaterialTheme.typography.titleMedium); Text("${call.type} • ${call.time}", style = MaterialTheme.typography.bodySmall); Text(call.status, style = MaterialTheme.typography.labelSmall) }; IconButton(onClick = { beginOutgoing(call.name, call.type == "Video call") }) { Icon(if (call.type == "Video call") Icons.Default.Videocam else Icons.Default.Call, "Call ${call.name}") } } } } }
    }
}

@Composable
fun FynxActiveCallPanel(name: String, session: FynxCallSession, realtimeState: FynxRealtimeClient.State = FynxRealtimeClient.State.CONNECTED, onAnswer: () -> Unit, onRetry: () -> Unit, onToggleMicrophone: () -> Unit, onToggleCamera: () -> Unit, onSwitchCamera: () -> Unit, onToggleSpeaker: () -> Unit, onEnd: () -> Unit) {
    val incoming = session.state == FynxCallState.RINGING; val connected = session.state == FynxCallState.CONNECTED; val video = session.type == FynxCallType.VIDEO
    Column(Modifier.fillMaxSize().background(FynxDesign.Background), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(44.dp)); Text(name, style = MaterialTheme.typography.headlineSmall); Text(when (session.state) { FynxCallState.IDLE -> "Ready"; FynxCallState.RINGING -> "Incoming ${if (video) "video" else "voice"} call"; FynxCallState.CONNECTING -> if (realtimeState == FynxRealtimeClient.State.CONNECTED) "Calling…" else "Connecting…"; FynxCallState.CONNECTED -> if (video) "Video call" else "Voice call"; FynxCallState.ENDED -> "Call ended" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(30.dp)); Box(Modifier.size(190.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary) }; Spacer(Modifier.weight(1f))
        if (incoming) { Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { OutlinedButton(onClick = onEnd) { Text("Decline") }; Button(onClick = onAnswer) { Icon(Icons.Default.Call, null); Spacer(Modifier.width(6.dp)); Text("Answer") } }; Spacer(Modifier.height(24.dp)) }
        else if (session.state == FynxCallState.CONNECTING) { OutlinedButton(onClick = onRetry) { Text("Retry call") }; Spacer(Modifier.height(18.dp)) }
        if (connected) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { FilledTonalIconButton(onClick = onToggleMicrophone) { Icon(if (session.microphoneEnabled) Icons.Default.Mic else Icons.Default.MicOff, "Mute") }; if (video) { FilledTonalIconButton(onClick = onToggleCamera) { Icon(if (session.cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff, "Camera") }; FilledTonalIconButton(onClick = onSwitchCamera) { Icon(Icons.Default.Videocam, "Switch camera") } }; FilledTonalIconButton(onClick = onToggleSpeaker) { Icon(if (session.speakerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff, "Speaker") }; FloatingActionButton(onClick = onEnd) { Icon(Icons.Default.CallEnd, "End call") } }
        else if (!incoming) FloatingActionButton(onClick = onEnd) { Icon(Icons.Default.CallEnd, "End call") }
        Spacer(Modifier.height(32.dp))
    }
}