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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.AudioTrack
import org.webrtc.PeerConnection
import org.webrtc.VideoTrack

enum class FynxCallHistoryFilter { ALL, MISSED, VIDEO, VOICE }

@Composable
fun FynxCallsPanel(initialName: String? = null, initialVideo: Boolean = false, initialOutgoing: Boolean = false, initialIncomingCallId: String? = null, initialIncomingUserId: String? = null, initialListingId: String? = null, initialProductTitle: String? = null, initialSellerUsername: String? = null, currentUsername: String? = null, onIncomingCallConsumed: (String) -> Unit = {}, onOutgoingCallConsumed: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeCall by remember { mutableStateOf(initialName?.removePrefix("@").orEmpty().ifBlank { initialName }) }
    var video by remember { mutableStateOf(initialVideo) }
    var targetUserId by remember { mutableStateOf(initialIncomingUserId) }
    var callListingId by remember(initialIncomingCallId, initialListingId) { mutableStateOf(initialListingId) }
    var callProductTitle by remember(initialIncomingCallId, initialProductTitle) { mutableStateOf(initialProductTitle) }
    var callSellerUsername by remember(initialIncomingCallId, initialSellerUsername) { mutableStateOf(initialSellerUsername) }
    var targetUsername by remember { mutableStateOf(initialName?.removePrefix("@")?.trim()) }
    var targetAvatarUsername by remember { mutableStateOf(initialName?.removePrefix("@")?.trim()?.takeIf { it.isNotBlank() && it != initialIncomingUserId }) }
    var endingCall by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var calls by remember { mutableStateOf(FynxCallsStore.load(context)) }
    var newCallUsername by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(FynxCallHistoryFilter.ALL) }
    var realtimeState by remember { mutableStateOf(FynxRealtimeClient.State.DISCONNECTED) }
    var mediaConnected by remember { mutableStateOf(false) }
    var directCallIsInitiator by remember { mutableStateOf(initialIncomingCallId == null && initialOutgoing) }
    var relaySetupComplete by remember { mutableStateOf(false) }
    var relayRefreshSeconds by remember { mutableLongStateOf(0L) }
    var session by remember {
        mutableStateOf<FynxCallSession?>(
            initialIncomingCallId?.let { callId ->
                val incomingName = initialName?.removePrefix("@")?.trim().orEmpty().ifBlank { initialIncomingUserId.orEmpty() }
                FynxCallSession(
                    id = callId,
                    callerUsername = incomingName,
                    participantUsernames = listOf(incomingName),
                    type = if (initialVideo) FynxCallType.VIDEO else FynxCallType.VOICE,
                    state = FynxCallState.RINGING
                )
            }
        )
    }
    var localVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var remoteVideoTrack by remember { mutableStateOf<VideoTrack?>(null) }
    var pendingIncomingAccept by remember { mutableStateOf(false) }
    var pendingIncomingAcceptSignal by remember { mutableStateOf(false) }
    var pendingOutgoingInvite by remember { mutableStateOf(false) }
    val latestSession by rememberUpdatedState(session)
    val latestTargetUserId by rememberUpdatedState(targetUserId)
    val mediaEngineForCleanup = remember { arrayOfNulls<FynxWebRtcCallEngine>(1) }

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
                onRemoteVideoTrack = { track: VideoTrack? -> remoteVideoTrack = track?.apply { setEnabled(true) } },
                onConnectionState = { state ->
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED, PeerConnection.IceConnectionState.COMPLETED -> session?.let { current -> session = current.copy(state = FynxCallState.CONNECTED); FynxCallsStore.updateStatus(context, current.id, "Connected", missed = false); calls = FynxCallsStore.load(context) }
                        PeerConnection.IceConnectionState.FAILED -> scope.launch {
                            val failed = session ?: return@launch
                            errorMessage = "The call connection failed. Please try again."
                            mediaEngineForCleanup[0]?.disconnect()
                            mediaConnected = false
                            localVideoTrack = null
                            remoteVideoTrack = null
                            FynxCallsStore.updateStatus(context, failed.id, "Failed", missed = false)
                            calls = FynxCallsStore.load(context)
                            activeCall = null
                            session = null
                            pendingIncomingAccept = false
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> if (session?.state == FynxCallState.CONNECTED) errorMessage = "The call connection was interrupted."
                        else -> Unit
                    }
                },
                onError = { message -> errorMessage = message }
            )
        )
    }
    mediaEngineForCleanup[0] = mediaEngine

    suspend fun configureCallRelay(callId: String, restartOfferOwner: Boolean): Long {
        val configuration = FynxCallIceServerProvider.fetch(context, callId).getOrNull()
        if (session?.id != callId) return 0L
        return if (configuration != null) {
            mediaEngine.updateIceServers(configuration.servers, restartOfferOwner)
            configuration.refreshAfterSeconds
        } else {
            errorMessage = "Call relay is not available yet. FYNX is trying a direct connection and will retry."
            120L
        }
    }

    fun beginCallRelaySetup(callId: String, restartOfferOwner: Boolean, onReady: (() -> Unit)? = null) {
        scope.launch {
            val refreshAfter = configureCallRelay(callId, restartOfferOwner)
            if (session?.id != callId || refreshAfter <= 0L) return@launch
            relayRefreshSeconds = refreshAfter
            relaySetupComplete = true
            onReady?.invoke()
        }
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
                            directCallIsInitiator = false
                            relaySetupComplete = false
                            relayRefreshSeconds = 0L
                            targetUserId = event.fromUserId
                            targetUsername = incomingName
                            targetAvatarUsername = event.fromUsername?.removePrefix("@")?.trim()?.takeIf { it.isNotBlank() }
                            activeCall = incomingName
                            session = FynxCallSession(event.callId, event.fromUsername ?: event.fromUserId, listOf(incomingName), if (video) FynxCallType.VIDEO else FynxCallType.VOICE, FynxCallState.RINGING)
                            FynxCallsStore.add(context, FynxCallRecord(event.callId, "@$incomingName", if (video) "Video call" else "Voice call", "Just now", missed = true, status = "Incoming", username = event.fromUsername?.removePrefix("@")?.trim()?.takeIf { it.isNotBlank() }))
                            calls = FynxCallsStore.load(context)
                        } else if (session != null && event.callId != session?.id) {
                            // Do not silently drop a second incoming invite while this user is busy.
                            realtimeClient.sendCallBusy(event.callId, event.fromUserId, event.callType.equals("video", true))
                        }
                        "accept" -> if (session?.id == event.callId) {
                            session?.let { current ->
                                if (!mediaConnected) { mediaEngine.connect(current); mediaConnected = true }
                                session = FynxCallsFoundation.start(current)
                                FynxCallsStore.updateStatus(context, event.callId, "Accepted", missed = false)
                                calls = FynxCallsStore.load(context)
                                // Do not send the first offer until the relay configuration is applied.
                                beginCallRelaySetup(current.id, restartOfferOwner = true) {
                                    if (session?.id == current.id) mediaEngine.createOffer()
                                }
                            }
                        }
                        "offer" -> if (session?.id == event.callId && !event.sdp.isNullOrBlank()) {
                            session?.let { current ->
                            if (!mediaConnected) { mediaEngine.connect(current); mediaConnected = true }
                            mediaEngine.acceptOfferAndCreateAnswer(event.sdp)
                            session = current.copy(state = FynxCallState.CONNECTING)
                            }
                        }
                        "answer" -> if (session?.id == event.callId && !event.sdp.isNullOrBlank()) mediaEngine.applyAnswer(event.sdp)
                        "ice" -> if (session?.id == event.callId && event.candidate != null) mediaEngine.addRemoteIceCandidate(event.candidate.toWebRtcCandidate())
                        "reject", "end" -> if (session?.id == event.callId) {
                            FynxCallsStore.updateStatus(context, event.callId, if (event.signalType == "reject") "Declined" else "Ended", missed = false)
                            calls = FynxCallsStore.load(context)
                            mediaEngine.disconnect(); mediaConnected = false; localVideoTrack = null; remoteVideoTrack = null; session = null; activeCall = null; pendingIncomingAccept = false; pendingIncomingAcceptSignal = false; pendingOutgoingInvite = false
                        }
                        "busy" -> if (session?.id == event.callId) {
                            errorMessage = "@$targetUsername is already on another call."
                            FynxCallsStore.updateStatus(context, event.callId, "Busy", missed = false)
                            calls = FynxCallsStore.load(context)
                            mediaEngine.disconnect(); mediaConnected = false; localVideoTrack = null; remoteVideoTrack = null; session = null; activeCall = null; pendingIncomingAccept = false
                        }
                        "unavailable" -> if (session?.id == event.callId) {
                            errorMessage = "@${targetUsername ?: "This user"} is unavailable right now."
                            FynxCallsStore.updateStatus(context, event.callId, "Unavailable", missed = false)
                            calls = FynxCallsStore.load(context)
                            mediaEngine.disconnect(); mediaConnected = false; localVideoTrack = null; remoteVideoTrack = null; session = null; activeCall = null; pendingIncomingAccept = false; pendingIncomingAcceptSignal = false; pendingOutgoingInvite = false
                        }
                        "error" -> if (session?.id == event.callId) {
                            errorMessage = event.error ?: "The call could not be started."
                            FynxCallsStore.updateStatus(context, event.callId, "Failed", missed = false)
                            calls = FynxCallsStore.load(context)
                            mediaEngine.disconnect(); mediaConnected = false; localVideoTrack = null; remoteVideoTrack = null; session = null; activeCall = null; pendingIncomingAccept = false; pendingIncomingAcceptSignal = false; pendingOutgoingInvite = false
                        }
                    }
                }
            }
        )
    }

    DisposableEffect(Unit) {
        realtimeClient.connect()
        onDispose {
            // The realtime client lives for this panel's lifetime; disposal must
            // inspect the current call, not the initial composition snapshot.
            val active = latestSession
            val peerId = latestTargetUserId
            if (active != null && !peerId.isNullOrBlank()) {
                val isRinging = active.state == FynxCallState.RINGING
                val isVideo = active.type == FynxCallType.VIDEO
                if (isRinging) realtimeClient.sendCallReject(active.id, peerId, isVideo)
                else realtimeClient.sendCallEnd(active.id, peerId, isVideo)
                FynxCallsStore.updateStatus(
                    context, active.id, if (isRinging) "Declined" else "Ended", missed = false
                )
            }
            mediaEngine.disconnect()
            realtimeClient.close()
        }
    }

    suspend fun resolveUser(username: String): String? = FynxSocialClient.searchUsers(context, username).getOrNull()?.firstOrNull { it.username.equals(username, true) }?.id

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val current = session ?: return@rememberLauncherForActivityResult
        val required = if (video) listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else listOf(Manifest.permission.RECORD_AUDIO)
        if (required.all { result[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            if (pendingIncomingAccept) {
                pendingIncomingAccept = false
                if (!mediaConnected) { mediaEngine.connect(current); mediaConnected = true }
                pendingIncomingAcceptSignal = true
                session = current.copy(state = FynxCallState.CONNECTING)
                FynxCallsStore.updateStatus(context, current.id, "Connecting", missed = false)
                calls = FynxCallsStore.load(context)
            } else {
                if (!mediaConnected) { mediaEngine.connect(current); mediaConnected = true }
                pendingOutgoingInvite = true
                FynxCallsStore.updateStatus(context, current.id, "Connecting", missed = false)
                calls = FynxCallsStore.load(context)
            }
        } else {
            val declinedIncoming = pendingIncomingAccept
            pendingIncomingAccept = false
            if (declinedIncoming) {
                targetUserId?.let { realtimeClient.sendCallReject(current.id, it, video) }
            }
            errorMessage = if (video) "Camera and microphone access are needed for video calls." else "Microphone access is needed for voice calls."
            FynxCallsStore.updateStatus(context, current.id, "Permission denied", missed = false)
            activeCall = null; session = null; calls = FynxCallsStore.load(context)
        }
    }

    fun beginOutgoing(name: String, isVideo: Boolean) {
        val username = name.removePrefix("@").trim()
        if (username.isBlank()) return
        if (isSelfCallTarget(username, currentUsername)) {
            errorMessage = "You cannot call your own account."
            return
        }
        mediaEngine.disconnect(); mediaConnected = false; localVideoTrack = null; remoteVideoTrack = null; pendingIncomingAccept = false; pendingIncomingAcceptSignal = false; pendingOutgoingInvite = false
        directCallIsInitiator = true
        relaySetupComplete = false
        relayRefreshSeconds = 0L
        video = isVideo; activeCall = username; targetUsername = username; targetAvatarUsername = username; errorMessage = null
        scope.launch {
            val resolvedId = resolveUser(username)
            if (resolvedId == null) { errorMessage = "We couldn't find @$username. Please check the username and try again."; activeCall = null; session = null; return@launch }
            targetUserId = resolvedId
            val id = "call-${System.currentTimeMillis()}"
            val newSession = FynxCallSession(id, "me", listOf("@$username"), if (isVideo) FynxCallType.VIDEO else FynxCallType.VOICE, FynxCallState.CONNECTING)
            session = newSession
            FynxCallsStore.add(context, FynxCallRecord(id, "@$username", if (isVideo) "Video call" else "Voice call", "Just now", status = "Outgoing", username = username))
            calls = FynxCallsStore.load(context)
            val required = if (isVideo) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO)
            if (required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
                mediaEngine.connect(newSession); mediaConnected = true
                pendingOutgoingInvite = true
                FynxCallsStore.updateStatus(context, id, "Connecting")
                calls = FynxCallsStore.load(context)
            } else permissionLauncher.launch(required)
        }
    }

    LaunchedEffect(initialIncomingCallId) {
        val callId = initialIncomingCallId ?: return@LaunchedEffect
        val incomingName = initialName?.removePrefix("@")?.trim().orEmpty().ifBlank { initialIncomingUserId.orEmpty() }
        FynxCallsStore.add(
            context,
            FynxCallRecord(
                callId,
                "@$incomingName",
                if (initialVideo) "Video call" else "Voice call",
                "Just now",
                missed = true,
                status = "Incoming",
                username = initialName?.removePrefix("@")?.trim()?.takeIf { it.isNotBlank() && it != initialIncomingUserId }
            )
        )
        calls = FynxCallsStore.load(context)
        onIncomingCallConsumed(callId)
    }

    LaunchedEffect(initialName, initialOutgoing) {
        if (shouldConsumeOutgoingCallTrigger(initialOutgoing, initialName)) {
            onOutgoingCallConsumed()
            beginOutgoing(initialName!!, initialVideo)
        }
    }

    LaunchedEffect(realtimeState, pendingOutgoingInvite, pendingIncomingAcceptSignal, session?.id) {
        val current = session ?: return@LaunchedEffect
        if (realtimeState != FynxRealtimeClient.State.CONNECTED) return@LaunchedEffect
        if (pendingIncomingAcceptSignal) {
            val peerId = targetUserId ?: current.callerUsername
            if (realtimeClient.sendCallAccept(current.id, peerId, video)) {
                pendingIncomingAcceptSignal = false
                // The accepted signal makes the call eligible for short-lived TURN credentials.
                beginCallRelaySetup(current.id, restartOfferOwner = false)
                FynxCallsStore.updateStatus(context, current.id, "Answered", missed = false)
                calls = FynxCallsStore.load(context)
                errorMessage = null
            } else {
                errorMessage = "The secure call connection is reconnecting. Your answer will retry automatically."
            }
        } else if (pendingOutgoingInvite) {
            val peerId = targetUserId
            if (peerId.isNullOrBlank()) {
                pendingOutgoingInvite = false
                errorMessage = "The selected caller could not be resolved. Please try again."
            } else if (realtimeClient.sendCallInvite(current.id, peerId, video, callListingId)) {
                pendingOutgoingInvite = false
                FynxCallsStore.updateStatus(context, current.id, "Calling", missed = false)
                calls = FynxCallsStore.load(context)
                errorMessage = null
            } else {
                errorMessage = "The secure call connection is reconnecting. Your call will retry automatically."
            }
        }
    }

    LaunchedEffect(session?.id, relaySetupComplete) {
        val activeId = session?.id ?: return@LaunchedEffect
        if (!relaySetupComplete || relayRefreshSeconds <= 0L) return@LaunchedEffect
        var refreshAfter = relayRefreshSeconds
        while (session?.id == activeId && relaySetupComplete) {
            delay(refreshAfter.coerceIn(60L, 86_400L) * 1_000L)
            val refreshed = FynxCallIceServerProvider.fetch(context, activeId).getOrNull()
            if (session?.id != activeId) return@LaunchedEffect
            if (refreshed != null) {
                mediaEngine.updateIceServers(refreshed.servers, restartOfferOwner = directCallIsInitiator)
                refreshAfter = refreshed.refreshAfterSeconds
            } else {
                errorMessage = "Call relay refresh failed. The current connection will be kept and FYNX will retry."
                refreshAfter = 120L
            }
            relayRefreshSeconds = refreshAfter
        }
    }

    LaunchedEffect(session?.id, session?.state) {
        val current = session ?: return@LaunchedEffect
        val waitingForAnswer = current.state == FynxCallState.RINGING || current.state == FynxCallState.CONNECTING
        if (!waitingForAnswer) return@LaunchedEffect
        delay(60_000L)
        if (session?.id == current.id && (session?.state == FynxCallState.RINGING || session?.state == FynxCallState.CONNECTING)) {
            targetUserId?.let { realtimeClient.sendCallEnd(current.id, it, video) }
            mediaEngine.disconnect(); mediaConnected = false; localVideoTrack = null; remoteVideoTrack = null
            val incoming = current.state == FynxCallState.RINGING
            FynxCallsStore.updateStatus(context, current.id, if (incoming) "Missed" else "No answer", missed = incoming)
            calls = FynxCallsStore.load(context)
            activeCall = null; session = null; pendingIncomingAccept = false; pendingIncomingAcceptSignal = false; pendingOutgoingInvite = false
        }
    }

    val activeSession = session
    val activePeerName = activeCall
    if (activePeerName != null && activeSession != null) {
        if (!callProductTitle.isNullOrBlank() && !callListingId.isNullOrBlank()) {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("MARKETPLACE LIVE PROOF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(callProductTitle.orEmpty(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text("Listing #${callListingId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    callSellerUsername?.let { Text("Seller: @${it.removePrefix("@")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer) }
                }
            }
        }
        FynxActiveCallPanel(name = activePeerName, session = activeSession, currentUsername = currentUsername, avatarUsername = targetAvatarUsername, realtimeState = realtimeState, localVideoTrack = localVideoTrack, remoteVideoTrack = remoteVideoTrack, errorMessage = errorMessage,
            onAnswer = {
                val current = session ?: return@FynxActiveCallPanel
                val required = if (video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA) else arrayOf(Manifest.permission.RECORD_AUDIO)
                if (required.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
                    if (!mediaConnected) { mediaEngine.connect(current); mediaConnected = true }
                    pendingIncomingAcceptSignal = true
                    session = current.copy(state = FynxCallState.CONNECTING)
                    FynxCallsStore.updateStatus(context, current.id, "Connecting", missed = false)
                    calls = FynxCallsStore.load(context)
                } else {
                    pendingIncomingAccept = true
                    permissionLauncher.launch(required)
                }
            },
            onRetry = { targetUsername?.let { beginOutgoing(it, video) } },
            onToggleMicrophone = { session?.let { current -> val next = FynxCallsFoundation.toggleMicrophone(current); session = next; mediaEngine.setMicrophoneEnabled(next.microphoneEnabled) } },
            onToggleCamera = { session?.let { current -> val next = FynxCallsFoundation.toggleCamera(current); session = next; mediaEngine.setCameraEnabled(next.cameraEnabled) } },
            onSwitchCamera = { session?.let { current -> session = FynxCallsFoundation.switchCamera(current); mediaEngine.switchCamera() } },
            onToggleSpeaker = { session?.let { current -> val next = FynxCallsFoundation.toggleSpeaker(current); session = next; mediaEngine.setSpeakerEnabled(next.speakerEnabled) } },
            onEnd = {
                if (endingCall) return@FynxActiveCallPanel
                endingCall = true
                val current = session
                val incoming = current?.state == FynxCallState.RINGING
                if (current != null) {
                    targetUserId?.let { peer ->
                        if (incoming) realtimeClient.sendCallReject(current.id, peer, video)
                        else realtimeClient.sendCallEnd(current.id, peer, video)
                    }
                    FynxCallsStore.updateStatus(context, current.id, if (incoming) "Declined" else "Ended", missed = false)
                }
                mediaEngine.disconnect(); mediaConnected = false; localVideoTrack = null; remoteVideoTrack = null
                calls = FynxCallsStore.load(context); activeCall = null; session = null; pendingIncomingAccept = false; pendingIncomingAcceptSignal = false; pendingOutgoingInvite = false; endingCall = false
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
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Start a call", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = newCallUsername,
                    onValueChange = { newCallUsername = it.removePrefix("@").take(60) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("FYNX username") },
                    placeholder = { Text("Enter username") },
                    singleLine = true
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { beginOutgoing(newCallUsername, false) },
                        enabled = newCallUsername.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Icon(Icons.Default.Call, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Voice") }
                    OutlinedButton(
                        onClick = { beginOutgoing(newCallUsername, true) },
                        enabled = newCallUsername.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Icon(Icons.Default.Videocam, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Video") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(FynxCallHistoryFilter.ALL to "All", FynxCallHistoryFilter.MISSED to "Missed", FynxCallHistoryFilter.VIDEO to "Video", FynxCallHistoryFilter.VOICE to "Voice").forEach { (value, label) -> FilterChip(filter == value, onClick = { filter = value }, label = { Text(label) }) } }
        Spacer(Modifier.height(10.dp))
        val filtered = when (filter) { FynxCallHistoryFilter.ALL -> calls; FynxCallHistoryFilter.MISSED -> calls.filter { it.missed }; FynxCallHistoryFilter.VIDEO -> calls.filter { it.type == "Video call" }; FynxCallHistoryFilter.VOICE -> calls.filter { it.type == "Voice call" } }
        if (filtered.isEmpty()) Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.Call, null); Spacer(Modifier.height(8.dp)); Text("No calls here", style = MaterialTheme.typography.titleMedium); Text("Your call history will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(filtered, key = { it.id }) { call -> Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) { FynxRemoteProfileAvatar(mediaId = null, contentDescription = call.name, modifier = Modifier.size(44.dp).clip(CircleShape), ownerUsername = call.username); Spacer(Modifier.width(10.dp)); Icon(if (call.type == "Video call") Icons.Default.Videocam else Icons.Default.Call, null); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(call.name, style = MaterialTheme.typography.titleMedium); Text("${call.type} • ${call.time}", style = MaterialTheme.typography.bodySmall); Text(call.status, style = MaterialTheme.typography.labelSmall) }; IconButton(onClick = { beginOutgoing(call.username ?: call.name, call.type == "Video call") }) { Icon(if (call.type == "Video call") Icons.Default.Videocam else Icons.Default.Call, "Call ${call.name}") } } } } }
    }
}
