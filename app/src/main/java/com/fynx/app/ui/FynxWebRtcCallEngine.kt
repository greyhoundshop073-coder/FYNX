package com.fynx.app.ui

import android.content.Context
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class FynxWebRtcCallEngine(
    context: Context,
    private val iceServers: List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
    ),
    private val callbacks: CallCallbacks = CallCallbacks()
) : FynxCallMediaEngine {
    data class CallCallbacks(
        val onOffer: (String) -> Unit = {},
        val onAnswer: (String) -> Unit = {},
        val onIceCandidate: (IceCandidate) -> Unit = {},
        val onLocalVideoTrack: (VideoTrack) -> Unit = {},
        val onRemoteAudioTrack: (AudioTrack) -> Unit = {},
        val onRemoteVideoTrack: (VideoTrack) -> Unit = {},
        val onConnectionState: (PeerConnection.IceConnectionState) -> Unit = {},
        val onError: (String) -> Unit = {}
    )

    private val appContext = context.applicationContext
    private val audioRouter = FynxCallAudioRouter(appContext)
    private val factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var cameraCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var eglBase: org.webrtc.EglBase? = null
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false

    init {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions())
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    override fun connect(session: FynxCallSession) {
        disconnect()
        remoteDescriptionSet = false
        pendingRemoteCandidates.clear()
        audioRouter.start(session.type == FynxCallType.VIDEO)
        peerConnection = factory.createPeerConnection(
            PeerConnection.RTCConfiguration(iceServers),
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) { callbacks.onConnectionState(state) }
                override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
                override fun onIceCandidate(candidate: IceCandidate) { callbacks.onIceCandidate(candidate) }
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
                override fun onAddStream(stream: MediaStream) {
                    stream.audioTracks.firstOrNull()?.let(callbacks.onRemoteAudioTrack)
                    stream.videoTracks.firstOrNull()?.let(callbacks.onRemoteVideoTrack)
                }
                override fun onRemoveStream(stream: MediaStream) = Unit
                override fun onDataChannel(channel: org.webrtc.DataChannel) = Unit
                override fun onRenegotiationNeeded() = Unit
                override fun onAddTrack(receiver: org.webrtc.RtpReceiver, mediaStreams: Array<out MediaStream>) {
                    when (val track = receiver.track()) {
                        is AudioTrack -> callbacks.onRemoteAudioTrack(track)
                        is VideoTrack -> callbacks.onRemoteVideoTrack(track)
                    }
                }
            }
        )
        createLocalAudio()
        if (session.type == FynxCallType.VIDEO) createLocalVideo()
    }

    fun createOffer() {
        val pc = peerConnection ?: return callbacks.onError("call media is not connected")
        pc.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(description: SessionDescription) {
                pc.setLocalDescription(object : SdpObserverAdapter() {
                    override fun onSetSuccess() { callbacks.onOffer(description.description) }
                    override fun onSetFailure(error: String) { callbacks.onError(error) }
                }, description)
            }
            override fun onCreateFailure(error: String) { callbacks.onError(error) }
        }, MediaConstraints())
    }

    fun acceptOfferAndCreateAnswer(sdp: String) {
        val pc = peerConnection ?: return callbacks.onError("call media is not connected")
        pc.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                flushRemoteCandidates(pc)
                pc.createAnswer(object : SdpObserverAdapter() {
                    override fun onCreateSuccess(description: SessionDescription) {
                        pc.setLocalDescription(object : SdpObserverAdapter() {
                            override fun onSetSuccess() { callbacks.onAnswer(description.description) }
                            override fun onSetFailure(error: String) { callbacks.onError(error) }
                        }, description)
                    }
                    override fun onCreateFailure(error: String) { callbacks.onError(error) }
                }, MediaConstraints())
            }
            override fun onSetFailure(error: String) { callbacks.onError(error) }
        }, SessionDescription(SessionDescription.Type.OFFER, sdp))
    }

    fun applyAnswer(sdp: String) {
        val pc = peerConnection ?: return callbacks.onError("call media is not connected")
        pc.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                flushRemoteCandidates(pc)
            }
            override fun onSetFailure(error: String) { callbacks.onError(error) }
        }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    fun addRemoteIceCandidate(candidate: IceCandidate) {
        val pc = peerConnection ?: return callbacks.onError("call media is not connected")
        if (!remoteDescriptionSet) {
            pendingRemoteCandidates += candidate
            return
        }
        if (!pc.addIceCandidate(candidate)) callbacks.onError("failed to add remote ICE candidate")
    }

    private fun flushRemoteCandidates(pc: PeerConnection) {
        val pending = pendingRemoteCandidates.toList()
        pendingRemoteCandidates.clear()
        pending.forEach { if (!pc.addIceCandidate(it)) callbacks.onError("failed to add remote ICE candidate") }
    }

    private fun createLocalAudio() {
        audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("fynx-audio", audioSource)
        audioTrack?.setEnabled(true)
        audioTrack?.let { peerConnection?.addTrack(it) }
    }

    private fun createLocalVideo() {
        val enumerator = Camera2Enumerator(appContext)
        val deviceName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()
            ?: return
        cameraCapturer = enumerator.createCapturer(deviceName, null)
        videoSource = factory.createVideoSource(false)
        videoTrack = factory.createVideoTrack("fynx-video", videoSource)
        videoTrack?.setEnabled(true)
        videoTrack?.let {
            peerConnection?.addTrack(it)
            callbacks.onLocalVideoTrack(it)
        }
        eglBase = org.webrtc.EglBase.create()
        surfaceTextureHelper = SurfaceTextureHelper.create("FYNX-Camera", eglBase!!.eglBaseContext)
        cameraCapturer?.initialize(surfaceTextureHelper, appContext, videoSource?.capturerObserver)
        cameraCapturer?.startCapture(1280, 720, 30)
    }

    override fun setMicrophoneEnabled(enabled: Boolean) { audioTrack?.setEnabled(enabled) }
    override fun setCameraEnabled(enabled: Boolean) { videoTrack?.setEnabled(enabled) }
    override fun switchCamera() { cameraCapturer?.switchCamera(null) }
    override fun setSpeakerEnabled(enabled: Boolean) { audioRouter.setSpeakerEnabled(enabled) }

    override fun disconnect() {
        runCatching { cameraCapturer?.stopCapture() }
        cameraCapturer?.dispose()
        cameraCapturer = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        eglBase?.release()
        eglBase = null
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        pendingRemoteCandidates.clear()
        remoteDescriptionSet = false
        audioTrack?.dispose()
        audioSource?.dispose()
        videoTrack?.dispose()
        videoSource?.dispose()
        audioTrack = null
        audioSource = null
        videoTrack = null
        videoSource = null
        audioRouter.stop()
    }

    private open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) = Unit
        override fun onSetFailure(error: String) = Unit
    }
}
