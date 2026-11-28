package com.fynx.app.ui

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/**
 * Small-mesh group media engine. One microphone and (for video) one camera
 * track are captured locally and shared with up to four authenticated peers.
 * Signaling identity is always supplied by the server-authorized group roster.
 */
class FynxGroupWebRtcCallEngine(
    context: Context,
    private val callbacks: Callbacks
) {
    data class Callbacks(
        val onOffer: (String, String) -> Unit,
        val onAnswer: (String, String) -> Unit,
        val onIceCandidate: (String, IceCandidate) -> Unit,
        val onLocalVideoTrack: (VideoTrack?) -> Unit,
        val onRemoteVideoTrack: (String, VideoTrack?) -> Unit,
        val onRemoteAudioTrack: (String, AudioTrack) -> Unit,
        val onPeerState: (String, PeerConnection.IceConnectionState) -> Unit,
        val onError: (String) -> Unit
    )

    private data class PeerState(
        val connection: PeerConnection,
        val pendingCandidates: MutableList<IceCandidate> = mutableListOf(),
        var remoteDescriptionReady: Boolean = false,
        var iceRestartPending: Boolean = false
    )

    private val appContext = context.applicationContext
    private val audioRouter = FynxCallAudioRouter(appContext)
    private val handler = Handler(Looper.getMainLooper())
    private val factory: PeerConnectionFactory
    private val peers = linkedMapOf<String, PeerState>()
    private val iceAttempts = mutableMapOf<String, Int>()
    private val iceRecovery = mutableMapOf<String, Runnable>()
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var cameraCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var eglBase: org.webrtc.EglBase? = null
    private var started = false
    private var videoCallEnabled = false
    private var microphoneEnabled = true
    private var speakerEnabled = true

    private var iceServers: List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun.cloudflare.com:3478").createIceServer()
    )

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    fun start(video: Boolean) {
        disconnect()
        started = true
        videoCallEnabled = video
        audioRouter.start(speakerEnabled)
        createLocalAudio()
        if (video) createLocalVideo()
        if (audioTrack == null) {
            callbacks.onError("Group-call microphone could not be initialized. Check microphone permission.")
            disconnect()
        }
    }

    fun isReadyForCall(): Boolean = started && audioTrack != null && (!videoCallEnabled || videoTrack != null)
    internal fun sfuPeerConnectionFactory(): PeerConnectionFactory = factory
    internal fun sfuLocalAudioTrack(): AudioTrack? = audioTrack
    internal fun sfuLocalVideoTrack(): VideoTrack? = videoTrack

    /**
     * Replace the initial STUN-only configuration with server-issued short-lived TURN
     * credentials. A deterministic offer owner restarts each mesh edge to avoid glare.
     */
    fun updateIceServers(servers: List<PeerConnection.IceServer>, localUserId: String?) {
        if (servers.none { server ->
                server.urls.any { it.startsWith("turn:", true) || it.startsWith("turns:", true) }
            }) {
            callbacks.onError("The call relay configuration did not contain a TURN server.")
            return
        }
        iceServers = servers.toList()
        peers.forEach { (peerId, peer) ->
            if (!peer.connection.setConfiguration(PeerConnection.RTCConfiguration(iceServers))) {
                callbacks.onError("The relay configuration could not be applied to one group connection.")
                return@forEach
            }
            if (!localUserId.isNullOrBlank() && localUserId < peerId) {
                peer.iceRestartPending = true
                restartPeerForUpdatedIce(peerId, peer)
            }
        }
    }

    fun hasPeer(userId: String): Boolean = peers.containsKey(userId)

    fun ensurePeer(userId: String): Boolean {
        val peerId = userId.trim()
        // This return value means a new peer was created, not merely that it exists.
        // Repeated roster/join events must not restart SDP negotiation on the same edge.
        if (!started || peerId.isBlank() || peers.containsKey(peerId) || peers.size >= 3) return false
        val pc = factory.createPeerConnection(PeerConnection.RTCConfiguration(iceServers), object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                callbacks.onPeerState(peerId, state)
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        iceAttempts[peerId] = 0
                        iceRecovery.remove(peerId)?.let(handler::removeCallbacks)
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.FAILED -> scheduleIceRecovery(peerId)
                    else -> Unit
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidate(candidate: IceCandidate) { callbacks.onIceCandidate(peerId, candidate) }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onAddStream(stream: org.webrtc.MediaStream) {
                stream.audioTracks.firstOrNull()?.let { it.setEnabled(true); callbacks.onRemoteAudioTrack(peerId, it) }
                stream.videoTracks.firstOrNull()?.let { it.setEnabled(true); callbacks.onRemoteVideoTrack(peerId, it) }
            }
            override fun onRemoveStream(stream: org.webrtc.MediaStream) {
                if (stream.videoTracks.isNotEmpty()) callbacks.onRemoteVideoTrack(peerId, null)
            }
            override fun onDataChannel(channel: org.webrtc.DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver, mediaStreams: Array<out org.webrtc.MediaStream>) {
                when (val track = receiver.track()) {
                    is AudioTrack -> { track.setEnabled(true); callbacks.onRemoteAudioTrack(peerId, track) }
                    is VideoTrack -> { track.setEnabled(true); callbacks.onRemoteVideoTrack(peerId, track) }
                }
            }
        }) ?: run {
            callbacks.onError("A group media connection could not be created.")
            return false
        }
        val streamIds = listOf("fynx-group")
        val audio = audioTrack
        val video = videoTrack
        if (audio == null || pc.addTrack(audio, streamIds) == null) {
            pc.close()
            pc.dispose()
            callbacks.onError("Group-call audio could not be attached to a participant.")
            return false
        }
        if (FynxGroupCallMediaPolicy.shouldAttachVideoTrack(videoCallEnabled, video != null) && pc.addTrack(video!!, streamIds) == null) {
            pc.close()
            pc.dispose()
            callbacks.onError("Group-call video could not be attached to a participant.")
            return false
        }
        peers[peerId] = PeerState(pc)
        return true
    }

    fun createOffer(userId: String) {
        val peerId = userId.trim()
        val peer = peers[peerId] ?: if (ensurePeer(peerId)) peers[peerId] else null
        val pc = peer?.connection ?: return callbacks.onError("The group participant is no longer available.")
        pc.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(description: SessionDescription) {
                pc.setLocalDescription(object : SdpObserverAdapter() {
                    override fun onSetSuccess() { callbacks.onOffer(peerId, description.description) }
                    override fun onSetFailure(error: String) { callbacks.onError("Group offer could not be prepared: $error") }
                }, description)
            }
            override fun onCreateFailure(error: String) { callbacks.onError("Group offer could not be created: $error") }
        }, MediaConstraints())
    }

    fun acceptOfferAndCreateAnswer(userId: String, sdp: String) {
        val peerId = userId.trim()
        val peer = peers[peerId] ?: if (ensurePeer(peerId)) peers[peerId] else null
        val pc = peer?.connection ?: return callbacks.onError("The group participant is no longer available.")
        pc.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                peer.remoteDescriptionReady = true
                flushRemoteCandidates(peerId, peer)
                pc.createAnswer(object : SdpObserverAdapter() {
                    override fun onCreateSuccess(description: SessionDescription) {
                        pc.setLocalDescription(object : SdpObserverAdapter() {
                            override fun onSetSuccess() {
                                callbacks.onAnswer(peerId, description.description)
                                if (peer.iceRestartPending) handler.post { restartPeerForUpdatedIce(peerId, peer) }
                            }
                            override fun onSetFailure(error: String) { callbacks.onError("Group answer could not be prepared: $error") }
                        }, description)
                    }
                    override fun onCreateFailure(error: String) { callbacks.onError("Group answer could not be created: $error") }
                }, MediaConstraints())
            }
            override fun onSetFailure(error: String) { callbacks.onError("Group offer could not be applied: $error") }
        }, SessionDescription(SessionDescription.Type.OFFER, sdp))
    }

    fun applyAnswer(userId: String, sdp: String) {
        val peerId = userId.trim()
        val peer = peers[peerId] ?: return callbacks.onError("The group participant is no longer available.")
        peer.connection.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                peer.remoteDescriptionReady = true
                flushRemoteCandidates(peerId, peer)
                if (peer.iceRestartPending) restartPeerForUpdatedIce(peerId, peer)
            }
            override fun onSetFailure(error: String) { callbacks.onError("Group answer could not be applied: $error") }
        }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    fun addRemoteIceCandidate(userId: String, candidate: IceCandidate) {
        val peerId = userId.trim()
        val peer = peers[peerId] ?: if (ensurePeer(peerId)) peers[peerId] else null
        if (peer == null) return callbacks.onError("The group participant is no longer available.")
        if (!peer.remoteDescriptionReady) {
            if (peer.pendingCandidates.size < 128) peer.pendingCandidates += candidate
            return
        }
        if (!peer.connection.addIceCandidate(candidate)) callbacks.onError("A group network candidate could not be applied.")
    }

    private fun flushRemoteCandidates(userId: String, peer: PeerState) {
        val pending = peer.pendingCandidates.toList()
        peer.pendingCandidates.clear()
        pending.forEach {
            if (!peer.connection.addIceCandidate(it)) callbacks.onError("A queued group network candidate could not be applied.")
        }
    }

    fun removePeer(userId: String) {
        val peerId = userId.trim()
        iceRecovery.remove(peerId)?.let(handler::removeCallbacks)
        iceAttempts.remove(peerId)
        val peer = peers.remove(peerId) ?: return
        runCatching { peer.connection.close() }
        runCatching { peer.connection.dispose() }
        callbacks.onRemoteVideoTrack(peerId, null)
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        microphoneEnabled = enabled
        audioTrack?.setEnabled(enabled)
    }

    fun setCameraEnabled(enabled: Boolean) {
        // Camera mute controls the existing track only. The call remains a video
        // call so peer connections created later still receive this track.
        videoTrack?.setEnabled(enabled)
    }

    fun switchCamera() { cameraCapturer?.switchCamera(null) }

    fun setSpeakerEnabled(enabled: Boolean) {
        speakerEnabled = enabled
        audioRouter.setSpeakerEnabled(enabled)
    }

    private fun restartPeerForUpdatedIce(userId: String, peer: PeerState) {
        if (!started || peers[userId] !== peer || !peer.remoteDescriptionReady) return
        if (peer.connection.signalingState() != PeerConnection.SignalingState.STABLE) {
            peer.iceRestartPending = true
            return
        }
        peer.iceRestartPending = false
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        }
        peer.connection.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(description: SessionDescription) {
                peer.connection.setLocalDescription(object : SdpObserverAdapter() {
                    override fun onSetSuccess() { callbacks.onOffer(userId, description.description) }
                    override fun onSetFailure(error: String) { callbacks.onError("Group relay restart offer failed: $error") }
                }, description)
            }
            override fun onCreateFailure(error: String) { callbacks.onError("Group relay restart could not start: $error") }
        }, constraints)
    }

    private fun scheduleIceRecovery(userId: String) {
        if (!peers.containsKey(userId) || iceRecovery.containsKey(userId)) return
        val attempt = (iceAttempts[userId] ?: 0)
        if (attempt >= 2) return
        val task = Runnable {
            iceRecovery.remove(userId)
            val peer = peers[userId] ?: return@Runnable
            if (peer.connection.iceConnectionState() == PeerConnection.IceConnectionState.CONNECTED ||
                peer.connection.iceConnectionState() == PeerConnection.IceConnectionState.COMPLETED) return@Runnable
            iceAttempts[userId] = attempt + 1
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            }
            peer.connection.createOffer(object : SdpObserverAdapter() {
                override fun onCreateSuccess(description: SessionDescription) {
                    peer.connection.setLocalDescription(object : SdpObserverAdapter() {
                        override fun onSetSuccess() { callbacks.onOffer(userId, description.description) }
                        override fun onSetFailure(error: String) { callbacks.onError("Group reconnection offer failed: $error") }
                    }, description)
                }
                override fun onCreateFailure(error: String) { callbacks.onError("Group reconnection could not start: $error") }
            }, constraints)
        }
        iceRecovery[userId] = task
        handler.postDelayed(task, if (attempt == 0) 1_500L else 5_000L)
    }

    private fun createLocalAudio() {
        try {
            audioSource = factory.createAudioSource(MediaConstraints())
            audioTrack = factory.createAudioTrack("fynx-group-audio", audioSource)
            audioTrack?.setEnabled(microphoneEnabled)
        } catch (error: Exception) {
            callbacks.onError("Group microphone could not be initialized: ${error.message ?: error.javaClass.simpleName}")
            audioTrack?.dispose()
            audioTrack = null
            audioSource?.dispose()
            audioSource = null
        }
    }

    private fun createLocalVideo() {
        try {
            val enumerator = Camera2Enumerator(appContext)
            val deviceName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: enumerator.deviceNames.firstOrNull()
            if (deviceName == null) {
                callbacks.onError("No camera is available for this group video call.")
                return
            }
            cameraCapturer = enumerator.createCapturer(deviceName, FynxCallCameraEvents(callbacks.onError))
                ?: throw IllegalStateException("Camera could not be opened.")
            eglBase = org.webrtc.EglBase.create()
            surfaceTextureHelper = SurfaceTextureHelper.create("FYNX-Group-Camera", eglBase!!.eglBaseContext)
            videoSource = factory.createVideoSource(false)
            cameraCapturer?.initialize(surfaceTextureHelper, appContext, videoSource?.capturerObserver)
            cameraCapturer?.startCapture(1280, 720, 24)
            videoTrack = factory.createVideoTrack("fynx-group-video", videoSource)
            videoTrack?.setEnabled(true)
            callbacks.onLocalVideoTrack(videoTrack)
        } catch (error: Exception) {
            releaseLocalVideo()
            callbacks.onError("Group camera could not be started: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun releaseLocalVideo() {
        runCatching { videoTrack?.setEnabled(false) }
        runCatching { cameraCapturer?.stopCapture() }
        runCatching { cameraCapturer?.dispose() }
        cameraCapturer = null
        runCatching { surfaceTextureHelper?.dispose() }
        surfaceTextureHelper = null
        runCatching { eglBase?.release() }
        eglBase = null
        runCatching { videoTrack?.dispose() }
        videoTrack = null
        runCatching { videoSource?.dispose() }
        videoSource = null
        callbacks.onLocalVideoTrack(null)
    }

    fun disconnect() {
        started = false
        iceRecovery.values.forEach(handler::removeCallbacks)
        iceRecovery.clear()
        iceAttempts.clear()
        peers.keys.toList().forEach(::removePeer)
        peers.clear()
        releaseLocalVideo()
        videoCallEnabled = false
        runCatching { audioTrack?.setEnabled(false) }
        runCatching { audioTrack?.dispose() }
        audioTrack = null
        runCatching { audioSource?.dispose() }
        audioSource = null
        runCatching { audioRouter.stop() }
    }

    private open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) = Unit
        override fun onSetFailure(error: String) = Unit
    }
}
