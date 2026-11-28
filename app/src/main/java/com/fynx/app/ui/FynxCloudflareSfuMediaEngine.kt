package com.fynx.app.ui

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.concurrent.ConcurrentHashMap

/**
 * Cloudflare SFU transport for one authenticated group participant. Local capture
 * and the PeerConnectionFactory remain owned by FynxGroupWebRtcCallEngine; this
 * class owns only the single SFU PeerConnection and its serialized SDP lifecycle.
 */
internal class FynxCloudflareSfuMediaEngine(
    context: Context,
    private val factory: PeerConnectionFactory,
    private val audioTrack: () -> AudioTrack?,
    private val videoTrack: () -> VideoTrack?,
    private val callbacks: Callbacks
) {
    data class Callbacks(
        val onRemoteVideoTrack: (String, VideoTrack?) -> Unit,
        val onRemoteAudioTrack: (String, AudioTrack) -> Unit,
        val onConnectionState: (PeerConnection.IceConnectionState) -> Unit,
        val onError: (String) -> Unit
    )

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var iceServers: List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()
    )
    @Volatile private var peerConnection: PeerConnection? = null
    private var callId: String? = null
    private var sessionId: String? = null
    @Volatile private var gatheringSignal: CompletableDeferred<Unit>? = null
    private val ownerByMid = ConcurrentHashMap<String, String>()
    private val ownerByPublisherSession = ConcurrentHashMap<String, String>()
    private val remoteTracksByUser = ConcurrentHashMap<String, ConcurrentHashMap<String, org.webrtc.MediaStreamTrack>>()
    private val subscribedTracks = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var active = false

    fun isActive(): Boolean = active

    fun updateIceServers(servers: List<PeerConnection.IceServer>) {
        if (servers.none { server ->
                server.urls.any { it.startsWith("turn:", true) || it.startsWith("turns:", true) }
            }) {
            callbacks.onError("The call relay configuration did not contain a TURN server.")
            return
        }
        iceServers = servers.toList()
        peerConnection?.let { connection ->
            if (!connection.setConfiguration(PeerConnection.RTCConfiguration(iceServers))) {
                callbacks.onError("The relay configuration could not be applied to the Cloudflare media connection.")
            }
        }
    }

    suspend fun connect(activeCallId: String) = mutex.withLock {
        require(activeCallId.isNotBlank()) { "A valid active group call is required." }
        require(audioTrack() != null) { "The group-call microphone is unavailable." }
        if (active && callId == activeCallId && peerConnection != null) return@withLock

        // A previous cleanup may have been interrupted by network loss. Reconcile it
        // before allocating another provider session so a retry does not leak tracks.
        if (callId != null && sessionId != null) {
            FynxCloudflareSfuClient.close(appContext, callId!!, sessionId!!)
            closeLocalConnection()
            callId = null
            sessionId = null
        }

        val createdSessionId = FynxCloudflareSfuClient.createSession(appContext, activeCallId)
        callId = activeCallId
        sessionId = createdSessionId
        var createdConnection: PeerConnection? = null
        try {
            createdConnection = factory.createPeerConnection(
                PeerConnection.RTCConfiguration(iceServers),
                object : PeerConnection.Observer {
                    override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
                    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                        callbacks.onConnectionState(state)
                    }
                    override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                        if (state == PeerConnection.IceGatheringState.COMPLETE) gatheringSignal?.complete(Unit)
                    }
                    override fun onIceCandidate(candidate: IceCandidate) = Unit
                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
                    override fun onAddStream(stream: org.webrtc.MediaStream) = Unit
                    override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit
                    override fun onDataChannel(channel: org.webrtc.DataChannel) = Unit
                    override fun onRenegotiationNeeded() = Unit
                    override fun onAddTrack(
                        receiver: org.webrtc.RtpReceiver,
                        mediaStreams: Array<out org.webrtc.MediaStream>
                    ) = Unit
                    override fun onTrack(transceiver: RtpTransceiver) {
                        val mid = transceiver.mid ?: return
                        val owner = ownerByMid[mid] ?: return
                        when (val remoteTrack = transceiver.receiver.track()) {
                            is AudioTrack -> {
                                remoteTrack.setEnabled(true)
                                remoteTracksByUser.computeIfAbsent(owner) { ConcurrentHashMap() }[remoteTrack.id()] = remoteTrack
                                callbacks.onRemoteAudioTrack(owner, remoteTrack)
                            }
                            is VideoTrack -> {
                                remoteTrack.setEnabled(true)
                                remoteTracksByUser.computeIfAbsent(owner) { ConcurrentHashMap() }[remoteTrack.id()] = remoteTrack
                                callbacks.onRemoteVideoTrack(owner, remoteTrack)
                            }
                        }
                    }
                    override fun onRemoveTrack(receiver: org.webrtc.RtpReceiver) {
                        // Remote ownership is reconciled from the authenticated group roster.
                        // Avoid relying on transceiver enumeration, which is not exposed by
                        // every bundled Android WebRTC API version.
                    }
                }
            ) ?: throw IllegalStateException("Cloudflare media connection could not be created.")

            val streamIds = listOf("fynx-group-sfu")
            val microphone = audioTrack() ?: throw IllegalStateException("The group-call microphone is unavailable.")
            check(createdConnection.addTrack(microphone, streamIds) != null) {
                "Group-call audio could not be attached to the SFU."
            }
            videoTrack()?.let { camera ->
                check(createdConnection.addTrack(camera, streamIds) != null) {
                    "Group-call video could not be attached to the SFU."
                }
            }

            val offer = createDescription(createdConnection, offer = true)
            val offerSdp = setLocalDescriptionAndGather(createdConnection, offer)
            val mids = parseFynxSfuMediaMids(offerSdp)
            val localTracks = buildList {
                mids["microphone"]?.let { add(FynxCloudflareSfuClient.Track(it, "microphone")) }
                if (videoTrack() != null) mids["camera"]?.let { add(FynxCloudflareSfuClient.Track(it, "camera")) }
            }
            check(localTracks.any { it.trackName == "microphone" }) {
                "The local microphone track has no SDP media identifier."
            }
            check(videoTrack() == null || localTracks.any { it.trackName == "camera" }) {
                "The local camera track has no SDP media identifier."
            }

            val publication = FynxCloudflareSfuClient.publish(
                appContext, activeCallId, createdSessionId, offerSdp, localTracks
            )
            check(publication.sessionDescriptionType == "answer" &&
                !publication.sessionDescriptionSdp.isNullOrBlank()) {
                "Cloudflare did not return the expected publication answer."
            }
            setRemoteDescription(
                createdConnection,
                SessionDescription(SessionDescription.Type.ANSWER, publication.sessionDescriptionSdp!!)
            )
            peerConnection = createdConnection
            active = true
        } catch (error: Exception) {
            runCatching { createdConnection?.close() }
            runCatching { createdConnection?.dispose() }
            val remoteCleanup = runCatching {
                FynxCloudflareSfuClient.close(appContext, activeCallId, createdSessionId)
            }
            if (remoteCleanup.isSuccess) {
                callId = null
                sessionId = null
            }
            throw error
        }
    }

    /** Each publication's offer/answer exchange completes before another subscribe is sent. */
    suspend fun syncPublications(activeCallId: String) = mutex.withLock {
        if (!active || callId != activeCallId) return@withLock
        val connection = peerConnection ?: throw IllegalStateException("The SFU media connection is unavailable.")
        val receivingSessionId = sessionId ?: throw IllegalStateException("The SFU session is unavailable.")
        val publishers = FynxCloudflareSfuClient.discover(appContext, activeCallId)

        for (publisher in publishers) {
            val pending = publisher.trackNames.filter { name ->
                (publisher.sessionId + ":" + name) !in subscribedTracks
            }
            if (pending.isEmpty()) continue

            val operation = FynxCloudflareSfuClient.subscribe(
                appContext, activeCallId, receivingSessionId, publisher.sessionId, pending
            )
            check(operation.tracks.size == pending.size &&
                operation.tracks.all { it.trackName in pending }) {
                "Cloudflare did not confirm every requested remote media track."
            }
            ownerByPublisherSession[publisher.sessionId] = publisher.userId
            operation.tracks.forEach { ownerByMid[it.mid] = publisher.userId }

            if (operation.requiresImmediateRenegotiation || operation.sessionDescriptionType == "offer") {
                check(operation.sessionDescriptionType == "offer" &&
                    !operation.sessionDescriptionSdp.isNullOrBlank()) {
                    "Cloudflare returned an invalid remote media offer."
                }
                setRemoteDescription(
                    connection,
                    SessionDescription(SessionDescription.Type.OFFER, operation.sessionDescriptionSdp!!)
                )
                val answer = createDescription(connection, offer = false)
                val answerSdp = setLocalDescriptionAndGather(connection, answer)
                FynxCloudflareSfuClient.renegotiate(
                    appContext, activeCallId, receivingSessionId, answerSdp
                )
            }
            pending.forEach { name -> subscribedTracks += publisher.sessionId + ":" + name }
        }
    }

    fun removeParticipant(userId: String) {
        val sessions = ownerByPublisherSession.entries
            .filter { it.value == userId }
            .map { it.key }
        sessions.forEach { publisherSessionId ->
            ownerByPublisherSession.remove(publisherSessionId, userId)
            subscribedTracks.removeIf { it.startsWith(publisherSessionId + ":") }
        }
        val mids = ownerByMid.entries.filter { it.value == userId }.map { it.key }
        mids.forEach { mid -> ownerByMid.remove(mid, userId) }
        remoteTracksByUser.remove(userId)?.values?.forEach { remoteTrack ->
            remoteTrack.setEnabled(false)
            if (remoteTrack is VideoTrack) callbacks.onRemoteVideoTrack(userId, null)
        }
    }

    suspend fun stop() = mutex.withLock {
        val activeCallId = callId
        val activeSessionId = sessionId
        var cleanupFailure: Throwable? = null
        if (activeCallId != null && activeSessionId != null) {
            runCatching {
                FynxCloudflareSfuClient.close(appContext, activeCallId, activeSessionId)
            }.onFailure { cleanupFailure = it }
        }
        closeLocalConnection()
        if (cleanupFailure == null) {
            callId = null
            sessionId = null
        }
        cleanupFailure?.let { throw it }
    }

    /** Lifecycle callback with a private scope so navigation cannot cancel track teardown. */
    fun disconnect() {
        if (callId != null && sessionId != null) {
            cleanupScope.launch {
                runCatching { stop() }.onFailure {
                    callbacks.onError("Cloudflare media cleanup could not be confirmed. Check the connection and retry.")
                }
            }
        } else closeLocalConnection()
    }

    private suspend fun createDescription(
        connection: PeerConnection,
        offer: Boolean
    ): SessionDescription = withTimeout(15_000L) {
        suspendCancellableCoroutine { continuation ->
        val observer = object : SdpObserverAdapter() {
            override fun onCreateSuccess(description: SessionDescription) {
                if (continuation.isActive) continuation.resume(description)
            }
            override fun onCreateFailure(error: String) {
                if (continuation.isActive) continuation.resumeWithException(
                    IllegalStateException("Cloudflare SDP " + (if (offer) "offer" else "answer") + " failed: " + error)
                )
            }
        }
        if (offer) connection.createOffer(observer, MediaConstraints())
        else connection.createAnswer(observer, MediaConstraints())
        }
    }

    private suspend fun setLocalDescriptionAndGather(
        connection: PeerConnection,
        description: SessionDescription
    ): String {
        val signal = CompletableDeferred<Unit>()
        gatheringSignal = signal
        withTimeout(15_000L) { suspendCancellableCoroutine<Unit> { continuation ->
            connection.setLocalDescription(object : SdpObserverAdapter() {
                override fun onSetSuccess() {
                    if (continuation.isActive) continuation.resume(Unit)
                }
                override fun onSetFailure(error: String) {
                    if (continuation.isActive) continuation.resumeWithException(
                        IllegalStateException("Cloudflare local SDP failed: " + error)
                    )
                }
            }, description)
        } }
        try {
            if (connection.iceGatheringState() != PeerConnection.IceGatheringState.COMPLETE) {
                withTimeout(12_000L) { signal.await() }
            }
        } finally {
            if (gatheringSignal === signal) gatheringSignal = null
        }
        return connection.localDescription?.description?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Cloudflare local SDP was empty after ICE gathering.")
    }

    private suspend fun setRemoteDescription(
        connection: PeerConnection,
        description: SessionDescription
    ) {
        withTimeout(15_000L) { suspendCancellableCoroutine<Unit> { continuation ->
            connection.setRemoteDescription(object : SdpObserverAdapter() {
                override fun onSetSuccess() {
                    if (continuation.isActive) continuation.resume(Unit)
                }
                override fun onSetFailure(error: String) {
                    if (continuation.isActive) continuation.resumeWithException(
                        IllegalStateException("Cloudflare remote SDP failed: " + error)
                    )
                }
            }, description)
        } }
    }

    private fun closeLocalConnection() {
        active = false
        ownerByMid.clear()
        ownerByPublisherSession.clear()
        remoteTracksByUser.clear()
        subscribedTracks.clear()
        runCatching { peerConnection?.close() }
        runCatching { peerConnection?.dispose() }
        peerConnection = null
        gatheringSignal?.cancel()
        gatheringSignal = null
    }

    private open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) = Unit
        override fun onSetFailure(error: String) = Unit
    }
}

internal fun parseFynxSfuMediaMids(sdp: String): Map<String, String> {
    val result = linkedMapOf<String, String>()
    var currentMedia: String? = null
    sdp.lineSequence().forEach { raw ->
        val line = raw.trim()
        currentMedia = when {
            line.startsWith("m=audio", ignoreCase = true) -> "microphone"
            line.startsWith("m=video", ignoreCase = true) -> "camera"
            line.startsWith("m=") -> null
            else -> currentMedia
        }
        if (line.startsWith("a=mid:", ignoreCase = true) && currentMedia != null) {
            val mid = line.removePrefix("a=mid:").trim()
            if (mid.isNotBlank() && mid.length <= 16 &&
                mid.all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
                result[currentMedia!!] = mid
            }
        }
    }
    return result
}
