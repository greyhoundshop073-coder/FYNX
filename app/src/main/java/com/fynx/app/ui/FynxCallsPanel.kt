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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import org.webrtc.AudioTrack
import org.webrtc.PeerConnection
import org.webrtc.VideoTrack

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
    var mediaConnected by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<FynxCallSession?>(null) }
    var localVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var remoteVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }

    lateinit var realtimeClient: FynxRealtimeClient
    val mediaEngine = remember {
        FynxWebRtcCallEngine(
            context = context,
            callbacks = FynxWebRtcCallEngine.CallCallbacks(
                onOffer = { sdp -> val current = session; val target = targetUserId; if (current != null && target != null) realtimeClient.sendCallOffer(current.id, target, sdp, current.type == FynxCallType.VIDEO) },
                onAnswer = { sdp -> val current = session; val target = targetUserId; if (current != null && target != null) realtimeClient.sendCallAnswer(current.id, target, sdp, current.type == FynxCallType.VIDEO) },
                onIceCandidate = { candidate -> val current = session; val target = targetUserId; if (current != null && target != null) realtimeClient.sendCallIce(current.id, target, candidate, current.type == FynxCallType.VIDEO) },
                onLocalVideoTrack = { track -> localVideoTrack = track.apply { setEnabled(true) } },
                onRemoteAudioTrack = { track: AudioTrack -> track.setEnabled(true) },
                onRemoteVideoTrack = { track: VideoTrack -> remoteVideoTrack = track.apply { setEnabled(true) } },
                onConnectionState = { state ->
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> session?.let { current -> session = current.copy(state = FynxCallState.CONNECTED); FynxCallsStore.updateStatus(context, current.id, "Connected", missed = false); calls = FynxCallsStore.load(context) }
                        PeerConnection.IceConnectionState.FAILED -> errorMessage = "The call connection failed. Please try again."
                        PeerConnection.IceConnectionState.DISCONNECTED -> if (session?.state == FynxCallState.CONNECTED) errorMessage = "The call connection was interrupted."
                        else -> Unit
                    }
                },
                onError = { message -> errorMessage = message }
            )
        )
    }

    realtimeClient = remember {
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
                            val current = session!!
                            if (!mediaConnected) { mediaEngine.connect(current); mediaConnected = true }
                            session = FynxCallsFoundation.start(current)
                            FynxCallsStore.updateStatus(context, event.callId, "Accepted", missed = false)
                            calls = FynxCallsStore.load(context)
                            mediaEngine.createOffer()
                        }
                        "offer" -> if (session?.id == event.callId && !event.sdp.isNullOrBlank()) {
                            val current = session!!
                            if (!mediaConnected) { mediaEngine.connect(current); mediaConnected = true }
                            mediaEngine.acceptOfferAndCreateAnswer(event.sdp)
                            session = current.copy(state = FynxCallState.CONNECTING)
                        }
                        "answer" -> if (session?.id == event.callId && !event.sdp.isNullOrBlank()) mediaEngine.applyAnswer(event.sdp)
                        "ice" -> if (session?.id == event.callId && event.candidate != null) mediaEngine.addRemoteIceCandidate(event.candidate.toWebRtcCandidate())
                        "reject", "end" -> if (session?.id == event.callId) {
                            FynxCallsStore.updateStatus(context, event.callId, if (event.signalType == "reject") "Declined" else "Ended", missed = false)
                            calls = FynxCallsStore.load(context)
                            mediaEngine.disconnect(); mediaConnected = false; localVideoTrack = null; remoteVideoTrack = null; session = null; activeCall = null
                        }
                        "busy" -> if (session?.id == event.callId) {
                            errorMessage = "@$targetUsername is already on another call."
                            FynxCallsStore.updateStatus(context, event.callId, "Busy", missed = false)
                            calls = FynxCallsStore.load(context)
                            mediaEngine.disconnect(); mediaConnected = false; localVideoTrack = null; remoteVideoTrack = null; session = null; activeCall = null
                        }
                    }
                }
            }
        )
    }

    DisposableEffect(Unit) {
        realtimeClient.connect()
        onDispose { mediaEngine.disconnect(); realtimeClient.close() }
    }

    suspend fun resolveUser(username: String): String? = FynxSocialClient.searchUsers(context, username).getOrNull()?.firstOrNull { it.username.equals(username, true) }?.id

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val current = session ?: return@rememberLauncherForActivityResult
        val required = if (video) listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else listOf(Manifest.permission.RECORD_AUDIO)
        if (required.all { result[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            if (!mediaConnected) { mediaEngine.connect(current); mediaConnected = true }
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
        mediaEngine.disconnect(); mediaConnected = false; localVideoTrack = null; remoteVideoTrack = null
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
                mediaEngine.connect(newSession); mediaConnected = true
                realtimeClient.sendCallInvite(id, resolvedId, isVideo)
                FynxCallsStore.updateStatus(context, id, "Calling")
                calls = FynxCallsStore.load(context)
            } else permissionLauncher.launch(required)
        }
    }

    LaunchedEffect(initialName, initialOutgoing) { if (initialOutgoing && !initialName.isNullOrBlank()) beginOutgoing(initialName, initialVideo) }

    if (activeCall != null && session != null) {
        FynxActiveCallPanel(name = activeCall!!, session = session!!, realtimeState = realtimeState, localVideoTrack = localVideoTrack, remoteVideoTrack = remoteVideoTrack,
            onAnswer = {
                val current = session!!
                val callerId = targetUserId ?: current.callerUsername
                if (!mediaConnected) { mediaEngine.connect(current); mediaConnected = true }
                realtimeClient.sendCallAccept(current.id, callerId, video)
                session = current.copy(state = FynxCallState.CONNECTING)
                FynxCallsStore.updateStatus(context, current.id, "Answered", missed = false)
                calls = FynxCallsStore.load(context)
            },
            onRetry = { targetUsername?.let { beginOutgoing(it, video) } },
            onToggleMicrophone = { session = FynxCallsFoundation.toggleMicrophone(session!!); mediaEngine.setMicrophoneEnabled(session!!.microphoneEnabled) },
            onToggleCamera = { session = FynxCallsFoundation.toggleCamera(session!!); mediaEngine.setCameraEnabled(session!!.cameraEnabled) },
            onSwitchCamera = { session = FynxCallsFoundation.switchCamera(session!!); mediaEngine.switchCamera() },
            onToggleSpeaker = { session = FynxCallsFoundation.toggleSpeaker(session!!); mediaEngine.setSpeakerEnabled(session!!.speakerEnabled) },
            onEnd = {
                val current = session!!; targetUserId?.let { realtimeClient.sendCallEnd(current.id, it, video) }
                val incoming = current.state == FynxCallState.RINGING
                mediaEngine.disconnect(); mediaConnected = false; localVideoTrack = null; remoteVideoTrack = null
                FynxCallsStore.updateStatus(context, current.id, if (incoming) "Declined" else "Ended", missed = incoming)
                calls = FynxCallsStore.load(context); activeCall = null; session = null
            }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(FynxDesign.Background).padding(16.dp)) {
        Text("Calls", style = MaterialTheme.typography.headlineSmall)
        Text("Voice and video calls", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (realtimeState == FynxRealtimeClient.State.FAILED) Text("Call connection is reconnecting…", style = MaterialTheme.typography.bodySmall)
        errorMessage?.let { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.padding(top = 10.dp)) { Text(it, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer) } }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(FynxCallHistoryFilter.ALL to "All", FynxCallHistoryFilter.MISSED to "Missed", FynxCallHistoryFilter.VIDEO to "Video", FynxCallHistoryFilter.VOICE to "Voice").forEach { (value, label) -> FilterChip(filter == value, onClick = { filter = value }, label = { Text(label) }) } }
        Spacer(Modifier.height(10.dp))
        val filtered = when (filter) { FynxCallHistoryFilter.ALL -> calls; FynxCallHistoryFilter.MISSED -> calls.filter { it.missed }; FynxCallHistoryFilter.VIDEO -> calls.filter { it.type == "Video call" }; FynxCallHistoryFilter.VOICE -> calls.filter { it.type == "Voice call" } }
        if (filtered.isEmpty()) Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Call, null); Spacer(Modifier.height(8.dp)); Text("No calls here", style = MaterialTheme.typography.titleMedium); Text("Your call history will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(filtered, key = { it.id }) { call -> Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(if (call.type == "Video call") Icons.Default.Videocam else Icons.Default.Call, null); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(call.name, style = MaterialTheme.typography.titleMedium); Text("${call.type} • ${call.time}", style = MaterialTheme.typography.bodySmall); Text(call.status, style = MaterialTheme.typography.labelSmall) }; IconButton(onClick = { beginOutgoing(call.name, call.type == "Video call") }) { Icon(if (call.type == "Video call") Icons.Default.Videocam else Icons.Default.Call, "Call ${call.name}") } } } } }
    }
}

@Composable
fun FynxActiveCallPanel(
    name: String,
    session: FynxCallSession,
    realtimeState: FynxRealtimeClient.State = FynxRealtimeClient.State.CONNECTED,
    localVideoTrack: VideoTrack? = null,
    remoteVideoTrack: VideoTrack? = null,
    onAnswer: () -> Unit,
    onRetry: () -> Unit,
    onToggleMicrophone: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEnd: () -> Unit
) {
    val incoming = session.state == FynxCallState.RINGING
    val connected = session.state == FynxCallState.CONNECTED
    val video = session.type == FynxCallType.VIDEO
    val callStatus = when (session.state) {
        FynxCallState.IDLE -> "Ready"
        FynxCallState.RINGING -> "Incoming ${if (video) "video" else "voice"} call"
        FynxCallState.CONNECTING -> if (realtimeState == FynxRealtimeClient.State.CONNECTED) "Connecting…" else "Reconnecting…"
        FynxCallState.CONNECTED -> "${if (video) "Video" else "Voice"} call"
        FynxCallState.ENDED -> "Call ended"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(FynxDesign.Background)
            .safeDrawingPadding()
    ) {
        if (video && connected) {
            Box(Modifier.fillMaxSize()) {
                if (remoteVideoTrack != null) {
                    FynxCallVideoSurface(remoteVideoTrack, Modifier.fillMaxSize(), mirror = false)
                } else {
                    FynxCallRemotePlaceholder(name, Modifier.fillMaxSize())
                }

                // Local camera preview stays in a small floating window, matching the
                // familiar two-person video-call pattern used by major messengers.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 12.dp)
                        .size(width = 112.dp, height = 158.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    if (localVideoTrack != null && session.cameraEnabled) {
                        FynxCallVideoSurface(localVideoTrack, Modifier.fillMaxSize(), mirror = true)
                    } else {
                        FynxCallRemotePlaceholder("You", Modifier.fillMaxSize())
                    }
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(52.dp))
                Box(
                    Modifier
                        .size(108.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(name.take(1).uppercase(), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(18.dp))
                Text(name, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(callStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (video && !incoming) {
                    Spacer(Modifier.height(28.dp))
                    Text("Your camera is ready", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (video && connected) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Text(callStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (incoming) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("${if (video) "Video" else "Voice"} call from $name", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FynxCallActionButton(onClick = onEnd, icon = Icons.Default.CallEnd, label = "Decline", destructive = true)
                    FynxCallActionButton(onClick = onAnswer, icon = Icons.Default.Call, label = "Answer")
                }
            }
        } else if (!connected) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(callStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                if (session.state == FynxCallState.CONNECTING) {
                    FynxCallActionButton(onClick = onEnd, icon = Icons.Default.CallEnd, label = "Cancel", destructive = true)
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 8.dp
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FynxCallActionButton(
                            onClick = onToggleMicrophone,
                            icon = if (session.microphoneEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                            label = if (session.microphoneEnabled) "Mute" else "Unmute"
                        )
                        if (video) {
                            FynxCallActionButton(
                                onClick = onToggleCamera,
                                icon = if (session.cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                label = if (session.cameraEnabled) "Camera" else "Camera off"
                            )
                            FynxCallActionButton(onClick = onSwitchCamera, icon = Icons.Default.Cameraswitch, label = "Flip")
                        }
                        FynxCallActionButton(
                            onClick = onToggleSpeaker,
                            icon = if (session.speakerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            label = if (session.speakerEnabled) "Speaker" else "Earpiece"
                        )
                        FynxCallActionButton(onClick = onEnd, icon = Icons.Default.CallEnd, label = "End", destructive = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun FynxCallRemotePlaceholder(name: String, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(116.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(name.take(1).uppercase(), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(10.dp))
            Text("Waiting for video…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FynxCallActionButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    destructive: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(58.dp),
            colors = if (destructive) {
                IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            } else {
                IconButtonDefaults.filledTonalIconButtonColors()
            }
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.height(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
