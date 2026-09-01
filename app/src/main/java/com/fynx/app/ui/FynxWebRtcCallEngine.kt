package com.fynx.app.ui

import android.content.Context
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.EglBase
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/** Real WebRTC media/peer-connection engine. Signaling remains transport-injected. */
class FynxWebRtcCallEngine(
    context: Context,
    private val iceServers: List<PeerConnection.IceServer> = emptyList()
) : FynxCallMediaEngine {
    private val appContext = context.applicationContext
    private val eglBase = EglBase.create()
    private val factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var cameraCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    override fun connect(session: FynxCallSession) {
        val configuration = PeerConnection.RTCConfiguration(iceServers)
        peerConnection = factory.createPeerConnection(configuration, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidate(candidate: org.webrtc.IceCandidate) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out org.webrtc.IceCandidate>) = Unit
            override fun onAddStream(stream: org.webrtc.MediaStream) = Unit
            override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit
            override fun onDataChannel(channel: org.webrtc.DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver, mediaStreams: Array<out org.webrtc.MediaStream>) = Unit
        }) ?: return
        createLocalAudio()
        if (session.type == FynxCallType.VIDEO) createLocalVideo()
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
        videoTrack?.let { peerConnection?.addTrack(it) }
        surfaceTextureHelper = SurfaceTextureHelper.create("FYNX-Camera", eglBase.eglBaseContext)
        cameraCapturer?.initialize(surfaceTextureHelper, appContext, videoSource?.capturerObserver)
        cameraCapturer?.startCapture(1280, 720, 30)
    }

    override fun setMicrophoneEnabled(enabled: Boolean) { audioTrack?.setEnabled(enabled) }
    override fun setCameraEnabled(enabled: Boolean) { videoTrack?.setEnabled(enabled) }
    override fun switchCamera() { cameraCapturer?.switchCamera(null) }
    override fun setSpeakerEnabled(enabled: Boolean) = Unit

    override fun disconnect() {
        runCatching { cameraCapturer?.stopCapture() }
        cameraCapturer?.dispose()
        cameraCapturer = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        audioTrack?.dispose()
        audioSource?.dispose()
        videoTrack?.dispose()
        videoSource?.dispose()
        audioTrack = null
        audioSource = null
        videoTrack = null
        videoSource = null
        eglBase.release()
    }
}
