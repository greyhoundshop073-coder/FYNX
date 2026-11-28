package com.fynx.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.VideoTrack
import java.util.UUID

@Composable
fun FynxGroupCallPanel(
    groupId: String,
    groupName: String,
    currentUsername: String,
    initialInvite: FynxRealtimeClient.Event.GroupCall? = null,
    onInviteConsumed: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val normalizedUsername = currentUsername.trim().removePrefix("@").lowercase()
    var realtimeState by remember { mutableStateOf(FynxRealtimeClient.State.DISCONNECTED) }
    val groupEvents = remember(groupId) { Channel<FynxRealtimeClient.Event.GroupCall>(Channel.UNLIMITED) }
    var callId by remember(groupId, initialInvite?.callId) { mutableStateOf(initialInvite?.callId) }
    var creatorId by remember { mutableStateOf(initialInvite?.creatorId.orEmpty()) }
    var callType by remember { mutableStateOf(initialInvite?.callType ?: "video") }
    var isStarter by remember { mutableStateOf(false) }
    var isIncoming by remember { mutableStateOf(initialInvite != null) }
    var isJoined by remember { mutableStateOf(false) }
    var callEnded by remember { mutableStateOf(false) }
    var startAction by remember { mutableStateOf<String?>(null) }
    var waitingForPermission by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf(if (initialInvite == null) "Ready to start" else "Incoming group video call") }
    var localUserId by remember { mutableStateOf<String?>(null) }
    var participants by remember { mutableStateOf(initialInvite?.participants.orEmpty()) }
    var microphoneEnabled by remember { mutableStateOf(true) }
    var cameraEnabled by remember { mutableStateOf(true) }
    var speakerEnabled by remember { mutableStateOf(true) }
    val remoteVideoTracks = remember { mutableStateMapOf<String, VideoTrack>() }
    val peerConnected = remember { mutableStateMapOf<String, Boolean>() }
    var localVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    val latestCallId by rememberUpdatedState(callId)
    val latestCallType by rememberUpdatedState(callType)
    val latestIsStarter by rememberUpdatedState(isStarter)
    val latestIsIncoming by rememberUpdatedState(isIncoming)
    val latestIsJoined by rememberUpdatedState(isJoined)
    val latestCallEnded by rememberUpdatedState(callEnded)

    val realtimeClient = remember(groupId) {
        FynxRealtimeClient(
            context = context,
            onMessage = {},
            onStateChanged = { realtimeState = it },
            onEvent = { event ->
                if (event is FynxRealtimeClient.Event.GroupCall && event.groupId == groupId) {
                    groupEvents.trySend(event)
                } else if (event is FynxRealtimeClient.Event.Call && event.signalType == "error") {
                    errorMessage = event.error ?: "The group-call service reported an error."
                }
            }
        )
    }

    val engine = remember(groupId) {
        FynxGroupWebRtcCallEngine(
            context = context,
            callbacks = FynxGroupWebRtcCallEngine.Callbacks(
                onOffer = { peerId, sdp ->
                    val id = latestCallId
                    if (id == null || !realtimeClient.sendGroupCallOffer(groupId, id, peerId, sdp, latestCallType == "video")) {
                        scope.launch(Dispatchers.Main.immediate) { errorMessage = "A group offer could not be sent. Check the connection and retry." }
                    }
                },
                onAnswer = { peerId, sdp ->
                    val id = latestCallId
                    if (id == null || !realtimeClient.sendGroupCallAnswer(groupId, id, peerId, sdp, latestCallType == "video")) {
                        scope.launch(Dispatchers.Main.immediate) { errorMessage = "A group answer could not be sent. Check the connection and retry." }
                    }
                },
                onIceCandidate = { peerId, candidate ->
                    val id = latestCallId
                    if (id == null || !realtimeClient.sendGroupCallIce(groupId, id, peerId, candidate, latestCallType == "video")) {
                        scope.launch(Dispatchers.Main.immediate) { errorMessage = "Group network details could not be sent." }
                    }
                },
                onLocalVideoTrack = { track ->
                    scope.launch(Dispatchers.Main.immediate) { localVideoTrack = track }
                },
                onRemoteVideoTrack = { peerId, track ->
                    scope.launch(Dispatchers.Main.immediate) {
                        if (track == null) remoteVideoTracks.remove(peerId) else remoteVideoTracks[peerId] = track
                    }
                },
                onRemoteAudioTrack = { _, track -> track.setEnabled(true) },
                onPeerState = { peerId, state ->
                    scope.launch(Dispatchers.Main.immediate) {
                        val connected = state == PeerConnection.IceConnectionState.CONNECTED ||
                            state == PeerConnection.IceConnectionState.COMPLETED
                        peerConnected[peerId] = connected
                        if (state == PeerConnection.IceConnectionState.FAILED) {
                            errorMessage = "Connection to one group participant failed. The other participants can remain in the call."
                        }
                    }
                },
                onError = { message ->
                    scope.launch(Dispatchers.Main.immediate) { errorMessage = message }
                }
            )
        )
    }

    fun participantForCurrentUser(list: List<FynxRealtimeClient.Event.GroupCallParticipant>): FynxRealtimeClient.Event.GroupCallParticipant? =
        list.firstOrNull { it.username.trim().removePrefix("@").equals(normalizedUsername, true) }

    fun syncRoster(list: List<FynxRealtimeClient.Event.GroupCallParticipant>, createOffers: Boolean) {
        participants = list.distinctBy { it.userId }.take(4)
        val self = participantForCurrentUser(participants)
        if (self != null) localUserId = self.userId
        if (creatorId.isNotBlank() && self != null) isStarter = creatorId == self.userId
        participants.filterNot { it.userId == localUserId }.forEach { peer ->
            val created = engine.ensurePeer(peer.userId)
            // Deterministic offer ownership prevents both sides of a new mesh edge
            // from creating an offer simultaneously (WebRTC offer glare).
            if (created && createOffers && localUserId != null && localUserId!! < peer.userId) {
                engine.createOffer(peer.userId)
            }
        }
        isJoined = true
        isIncoming = false
        statusText = if (participants.size <= 1) "Waiting for group members…" else "Connecting to group members…"
    }

    fun applyGroupEvent(event: FynxRealtimeClient.Event.GroupCall) {
        if (callId != null && event.callId != callId && event.signalType !in setOf("group_invite")) return
        if (callId == null) callId = event.callId
        if (event.creatorId.isNotBlank()) {
            creatorId = event.creatorId
            isStarter = localUserId != null && localUserId == creatorId
        }
        when (event.signalType) {
            "group_invite" -> {
                if (callId == null || callId == event.callId) {
                    callId = event.callId
                    callType = event.callType
                    creatorId = event.creatorId
                    isIncoming = true
                    statusText = "Incoming group video call"
                }
            }
            "group_created" -> {
                callId = event.callId
                creatorId = event.creatorId
                isStarter = true
                isIncoming = false
                syncRoster(event.participants, createOffers = false)
                statusText = "Waiting for group members…"
            }
            "group_roster" -> {
                syncRoster(event.participants, createOffers = true)
            }
            "group_participant_joined" -> {
                val participant = event.participant ?: return
                if (participants.none { it.userId == participant.userId }) {
                    participants = (participants + participant).take(4)
                    engine.ensurePeer(participant.userId)
                }
                statusText = "Connecting to group members…"
            }
            "group_participant_left" -> {
                val participant = event.participant ?: return
                participants = participants.filterNot { it.userId == participant.userId }
                engine.removePeer(participant.userId)
                peerConnected.remove(participant.userId)
            }
            "group_offer" -> {
                val peerId = event.fromUserId
                if (peerId.isNotBlank() && !event.sdp.isNullOrBlank()) {
                    engine.ensurePeer(peerId)
                    engine.acceptOfferAndCreateAnswer(peerId, event.sdp)
                }
            }
            "group_answer" -> {
                val peerId = event.fromUserId
                if (peerId.isNotBlank() && !event.sdp.isNullOrBlank()) engine.applyAnswer(peerId, event.sdp)
            }
            "group_ice" -> {
                val peerId = event.fromUserId
                val candidate = event.candidate
                if (peerId.isNotBlank() && candidate != null) engine.addRemoteIceCandidate(peerId, candidate.toWebRtcCandidate())
            }
            "group_full", "group_busy", "error" -> {
                errorMessage = event.error ?: "This group call is unavailable."
                statusText = "Call unavailable"
                engine.disconnect()
                isJoined = false
                isIncoming = false
            }
            "group_ended" -> {
                callEnded = true
                isJoined = false
                statusText = "The group call has ended."
                engine.disconnect()
            }
            "group_left" -> {
                callEnded = true
                isJoined = false
                statusText = "You left the group call."
                engine.disconnect()
            }
        }
    }

    LaunchedEffect(groupEvents) {
        for (event in groupEvents) {
            applyGroupEvent(event)
        }
    }

    // TURN credentials are fetched only after the server has confirmed this user
    // is JOINED in the active group call. Refresh before they expire.
    LaunchedEffect(callId, isJoined, callEnded, localUserId) {
        val activeCallId = callId ?: return@LaunchedEffect
        if (!isJoined || callEnded) return@LaunchedEffect
        while (true) {
            val configuration = FynxCallIceServerProvider.fetch(context, activeCallId).getOrNull()
            if (configuration != null) {
                engine.updateIceServers(configuration.servers, localUserId)
                delay(configuration.refreshAfterSeconds.coerceIn(60L, 86_400L) * 1_000L)
            } else {
                errorMessage = "Relay setup is unavailable right now. FYNX will keep trying a direct connection."
                delay(120_000L)
            }
        }
    }

    DisposableEffect(realtimeClient, engine) {
        realtimeClient.connect()
        onDispose {
            // DisposableEffect is intentionally keyed to the long-lived client and engine.
            // Read the latest call state here so disposal never acts on the initial
            // composition snapshot (which would silently skip leave/end/reject signals).
            val activeId = latestCallId
            if (activeId != null && latestIsJoined && !latestCallEnded) {
                if (latestIsStarter) realtimeClient.sendGroupCallEnd(groupId, activeId)
                else realtimeClient.sendGroupCallLeave(groupId, activeId)
            } else if (activeId != null && latestIsIncoming && !latestIsJoined) {
                realtimeClient.sendGroupCallReject(groupId, activeId)
            }
            engine.disconnect()
            realtimeClient.close()
            groupEvents.close()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        waitingForPermission = false
        val required = if (callType == "video") listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO) else listOf(Manifest.permission.RECORD_AUDIO)
        val granted = required.all { results[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (!granted) {
            errorMessage = if (callType == "video") "Camera and microphone permissions are required for a group video call." else "Microphone permission is required for a group call."
            statusText = "Permission required"
            startAction = null
            return@rememberLauncherForActivityResult
        }
        engine.start(callType == "video")
        if (!engine.isReadyForCall()) {
            errorMessage = "The microphone or camera could not be prepared. Check device permissions and try again."
            statusText = "Media unavailable"
            engine.disconnect()
            startAction = null
            return@rememberLauncherForActivityResult
        }
        if (realtimeState != FynxRealtimeClient.State.CONNECTED) {
            statusText = "Waiting for the secure call connection…"
        } else {
            statusText = "Preparing the group call…"
        }
    }

    LaunchedEffect(realtimeState, startAction, waitingForPermission) {
        if (realtimeState != FynxRealtimeClient.State.CONNECTED || waitingForPermission) return@LaunchedEffect
        if (!engine.isReadyForCall()) return@LaunchedEffect
        when (startAction) {
            "create" -> {
                val id = "call-${System.currentTimeMillis().toString(36)}-${UUID.randomUUID().toString().take(8)}"
                callId = id
                isStarter = true
                if (realtimeClient.sendGroupCallCreate(groupId, id, callType == "video")) {
                    statusText = "Starting group call…"
                } else {
                    errorMessage = "The group call request could not reach the call service. Reconnecting…"
                    engine.disconnect()
                }
                startAction = null
            }
            "join" -> {
                val id = callId
                if (id != null && realtimeClient.sendGroupCallJoin(groupId, id)) {
                    statusText = "Joining group call…"
                } else {
                    errorMessage = "The group call invitation could not be accepted. Please reconnect and retry."
                    engine.disconnect()
                }
                startAction = null
            }
            else -> Unit
        }
    }

    fun requestMedia(action: String) {
        if (waitingForPermission) return
        startAction = action
        waitingForPermission = true
        val permissions = if (callType == "video") arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO) else arrayOf(Manifest.permission.RECORD_AUDIO)
        permissionLauncher.launch(permissions)
    }

    fun endOrLeave() {
        val id = callId
        if (id != null) {
            if (isStarter) realtimeClient.sendGroupCallEnd(groupId, id)
            else realtimeClient.sendGroupCallLeave(groupId, id)
        }
        callEnded = true
        isJoined = false
        engine.disconnect()
        onDismiss()
    }

    val hasConnection = peerConnected.values.any { it }
    val visibleParticipants = participants.filter { it.userId != localUserId }
    val callDescription = when {
        errorMessage != null -> "Call needs attention"
        isIncoming -> "Incoming group video call"
        callEnded -> statusText
        hasConnection -> "Connected"
        isJoined && participants.size > 1 -> "Connecting…"
        isJoined -> "Waiting for participants…"
        else -> statusText
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(groupName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(callDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onDismiss, enabled = !isJoined) { Text("Close") }
            }

            if (errorMessage != null) {
                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
                    Text(errorMessage.orEmpty(), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                }
            }

            if (isIncoming && !isJoined && !callEnded) {
                Spacer(Modifier.weight(1f))
                Text("A member has started a group video call.", style = MaterialTheme.typography.titleMedium)
                Text("Join to connect with up to three other people in this call.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            callId?.let { realtimeClient.sendGroupCallReject(groupId, it) }
                            callEnded = true
                            isIncoming = false
                            onDismiss()
                        }
                    ) { Icon(Icons.Default.CallEnd, null); Spacer(Modifier.width(6.dp)); Text("Decline") }
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = realtimeState == FynxRealtimeClient.State.CONNECTED && !waitingForPermission,
                        onClick = { requestMedia("join") }
                    ) { Icon(Icons.Default.Videocam, null); Spacer(Modifier.width(6.dp)); Text("Join call") }
                }
            } else if (!isJoined && !callEnded) {
                Spacer(Modifier.weight(1f))
                Text("Start a video call with the members of $groupName.", style = MaterialTheme.typography.titleMedium)
                Text("Up to four participants can join at the same time on this peer-to-peer call.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (realtimeState != FynxRealtimeClient.State.CONNECTED) {
                    Text("Connecting to FYNX call service…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = realtimeState == FynxRealtimeClient.State.CONNECTED && !waitingForPermission,
                    onClick = { requestMedia("create") }
                ) { Icon(Icons.Default.Videocam, null); Spacer(Modifier.width(8.dp)); Text("Start group video call") }
            } else if (callEnded) {
                Spacer(Modifier.weight(1f))
                Text(statusText, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Return to group chat") }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Participants", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${participants.size}/4", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                val tiles = listOf<Pair<String, VideoTrack?>>(
                    ("You" to localVideoTrack)
                ) + visibleParticipants.map { member -> (member.username.ifBlank { "Member" } to remoteVideoTracks[member.userId]) }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tiles) { tile ->
                        FynxGroupCallVideoTile(tile.first, tile.second, Modifier.fillMaxWidth().height(190.dp))
                    }
                }
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FynxGroupCallControl(
                            label = if (microphoneEnabled) "Mute" else "Unmute",
                            icon = if (microphoneEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                            onClick = { microphoneEnabled = !microphoneEnabled; engine.setMicrophoneEnabled(microphoneEnabled) }
                        )
                        FynxGroupCallControl(
                            label = if (cameraEnabled) "Camera" else "Camera off",
                            icon = if (cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            onClick = { cameraEnabled = !cameraEnabled; engine.setCameraEnabled(cameraEnabled) }
                        )
                        FynxGroupCallControl("Flip", Icons.Default.Cameraswitch, onClick = engine::switchCamera)
                        FynxGroupCallControl(
                            label = if (speakerEnabled) "Speaker" else "Earpiece",
                            icon = if (speakerEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            onClick = { speakerEnabled = !speakerEnabled; engine.setSpeakerEnabled(speakerEnabled) }
                        )
                        FynxGroupCallControl(if (isStarter) "End call" else "Leave", Icons.Default.CallEnd, destructive = true, onClick = ::endOrLeave)
                    }
                }
            }
        }
    }
}

@Composable
private fun FynxGroupCallVideoTile(name: String, track: VideoTrack?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (track != null) {
            FynxCallVideoSurface(track, Modifier.fillMaxSize(), mirror = name == "You")
        } else {
            Text(name.take(24), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(Modifier.align(Alignment.BottomStart).padding(7.dp), shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)) {
            Text(name, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.inverseOnSurface)
        }
    }
}

@Composable
private fun FynxGroupCallControl(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            colors = if (destructive) IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ) else IconButtonDefaults.filledTonalIconButtonColors()
        ) { Icon(icon, label) }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
