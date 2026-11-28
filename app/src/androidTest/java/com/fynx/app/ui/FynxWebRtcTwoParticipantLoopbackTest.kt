package com.fynx.app.ui

import android.Manifest
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.JavaI420Buffer
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Real WebRTC media-loopback acceptance test.
 *
 * Two independent native PeerConnections represent two FYNX participants on one
 * Android emulator. They exchange real SDP and ICE locally (no FYNX backend,
 * credentials, Cloudflare account or internet signaling required). Participant A
 * publishes generated video frames and a microphone track; participant B must
 * receive both tracks, reach ICE CONNECTED, and render actual remote video frames.
 *
 * This verifies the Android WebRTC/media layer only. It does not certify FYNX
 * account authorization, backend signaling, Cloudflare SFU, TURN or FCM delivery.
 */
class FynxWebRtcTwoParticipantLoopbackTest {

    private class PeerProbe {
        val iceGathered = CompletableDeferred<Unit>()
        val connected = CompletableDeferred<Unit>()
        val remoteAudio = CompletableDeferred<AudioTrack>()
        val remoteVideo = CompletableDeferred<VideoTrack>()
        val remoteVideoFrame = CompletableDeferred<Unit>()
        val videoSinkAttached = AtomicBoolean(false)
    }

    @Test
    fun twoParticipantsExchangeLiveMediaWithoutBackend() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        runCatching {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.RECORD_AUDIO
            )
        }

        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        val factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        val participantA = PeerProbe()
        val participantB = PeerProbe()
        var peerA: PeerConnection? = null
        var peerB: PeerConnection? = null
        var audioSource: AudioSource? = null
        var audioTrack: AudioTrack? = null
        var videoSource: VideoSource? = null
        var videoTrack: VideoTrack? = null
        var frameThread: Thread? = null

        try {
            audioSource = factory.createAudioSource(MediaConstraints())
            audioTrack = factory.createAudioTrack("fynx-loopback-microphone", audioSource)
            audioTrack.setEnabled(true)

            videoSource = factory.createVideoSource(false)
            videoTrack = factory.createVideoTrack("fynx-loopback-camera", videoSource)
            videoTrack.setEnabled(true)

            peerA = createPeer(factory, participantA)
            peerB = createPeer(factory, participantB)

            check(peerA.addTrack(audioTrack, listOf("fynx-loopback")))
            check(peerA.addTrack(videoTrack, listOf("fynx-loopback")))

            // Feed deterministic I420 frames into the real WebRTC video source.
            val capturerObserver = videoSource.capturerObserver
            capturerObserver.onCapturerStarted(true)
            frameThread = Thread {
                val width = 320
                val height = 240
                val yPlane = ByteArray(width * height) { 96.toByte() }
                val uPlane = ByteArray(width * height / 4) { 112.toByte() }
                val vPlane = ByteArray(width * height / 4) { 144.toByte() }
                try {
                    while (!Thread.currentThread().isInterrupted) {
                        val buffer = JavaI420Buffer.allocate(width, height)
                        buffer.dataY.put(yPlane).position(0)
                        buffer.dataU.put(uPlane).position(0)
                        buffer.dataV.put(vPlane).position(0)
                        val frame = VideoFrame(buffer, 0, System.nanoTime())
                        capturerObserver.onFrameCaptured(frame)
                        frame.release()
                        Thread.sleep(33)
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }.apply {
                name = "fynx-loopback-video-source"
                isDaemon = true
                start()
            }

            val offer = createDescription(peerA, offer = true)
            setLocalDescription(peerA, offer)
            withTimeout(15_000) { participantA.iceGathered.await() }
            val gatheredOffer = requireNotNull(peerA.localDescription)
            setRemoteDescription(peerB, gatheredOffer)

            val answer = createDescription(peerB, offer = false)
            setLocalDescription(peerB, answer)
            withTimeout(15_000) { participantB.iceGathered.await() }
            val gatheredAnswer = requireNotNull(peerB.localDescription)
            setRemoteDescription(peerA, gatheredAnswer)

            // Candidate gathering is complete before SDP exchange, so the gathered
            // candidates are embedded in each localDescription (non-trickle test).
            withTimeout(30_000) {
                participantA.connected.await()
                participantB.connected.await()
            }
            withTimeout(15_000) { participantB.remoteAudio.await() }
            withTimeout(15_000) { participantB.remoteVideo.await() }
            withTimeout(15_000) { participantB.remoteVideoFrame.await() }

            assertTrue("Participant A must have reached ICE CONNECTED", peerA.iceConnectionState() == PeerConnection.IceConnectionState.CONNECTED || peerA.iceConnectionState() == PeerConnection.IceConnectionState.COMPLETED)
            assertTrue("Participant B must have reached ICE CONNECTED", peerB.iceConnectionState() == PeerConnection.IceConnectionState.CONNECTED || peerB.iceConnectionState() == PeerConnection.IceConnectionState.COMPLETED)
            android.util.Log.i(
                "FYNX_CALL_LOOPBACK",
                "PASS: two local participants connected; remote audio and video tracks arrived; remote video frames rendered"
            )
        } finally {
            frameThread?.interrupt()
            frameThread?.join(2_000)
            videoSource?.capturerObserver?.onCapturerStopped()
            peerA?.close()
            peerB?.close()
            peerA?.dispose()
            peerB?.dispose()
            videoTrack?.dispose()
            videoSource?.dispose()
            audioTrack?.dispose()
            audioSource?.dispose()
            factory.dispose()
        }
    }

    private fun createPeer(factory: PeerConnectionFactory, probe: PeerProbe): PeerConnection {
        val configuration = PeerConnection.RTCConfiguration(emptyList())
        return requireNotNull(
            factory.createPeerConnection(configuration, object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> probe.connected.complete(Unit)
                        PeerConnection.IceConnectionState.FAILED ->
                            probe.connected.completeExceptionally(AssertionError("Local WebRTC ICE negotiation failed"))
                        else -> Unit
                    }
                }
                override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                    if (state == PeerConnection.IceGatheringState.COMPLETE) probe.iceGathered.complete(Unit)
                }
                override fun onIceCandidate(candidate: IceCandidate) = Unit
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
                override fun onAddStream(stream: org.webrtc.MediaStream) = Unit
                override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit
                override fun onDataChannel(channel: org.webrtc.DataChannel) = Unit
                override fun onRenegotiationNeeded() = Unit
                override fun onAddTrack(receiver: org.webrtc.RtpReceiver, mediaStreams: Array<out org.webrtc.MediaStream>) {
                    observeRemoteTrack(receiver.track(), probe)
                }
                override fun onTrack(transceiver: org.webrtc.RtpTransceiver) {
                    observeRemoteTrack(transceiver.receiver.track(), probe)
                }
            })
        )
    }

    private fun observeRemoteTrack(track: org.webrtc.MediaStreamTrack?, probe: PeerProbe) {
        when (track) {
            is AudioTrack -> {
                track.setEnabled(true)
                probe.remoteAudio.complete(track)
            }
            is VideoTrack -> {
                track.setEnabled(true)
                if (probe.remoteVideo.complete(track) && probe.videoSinkAttached.compareAndSet(false, true)) {
                    track.addSink(VideoSink { frame ->
                        if (frame.buffer.width > 0 && frame.buffer.height > 0) {
                            probe.remoteVideoFrame.complete(Unit)
                        }
                    })
                }
            }
        }
    }

    private suspend fun createDescription(
        peer: PeerConnection,
        offer: Boolean
    ): SessionDescription = withTimeout(10_000) {
        suspendCancellableCoroutine { continuation ->
            val observer = object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) {
                    if (continuation.isActive) continuation.resume(description)
                }
                override fun onSetSuccess() = Unit
                override fun onCreateFailure(error: String) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error))
                }
                override fun onSetFailure(error: String) = Unit
            }
            if (offer) peer.createOffer(observer, MediaConstraints())
            else peer.createAnswer(observer, MediaConstraints())
        }
    }

    private suspend fun setLocalDescription(peer: PeerConnection, description: SessionDescription) {
        withTimeout(10_000) {
            suspendCancellableCoroutine { continuation ->
                peer.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(value: SessionDescription) = Unit
                    override fun onSetSuccess() {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    override fun onCreateFailure(error: String) = Unit
                    override fun onSetFailure(error: String) {
                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error))
                    }
                }, description)
            }
        }
    }

    private suspend fun setRemoteDescription(peer: PeerConnection, description: SessionDescription) {
        withTimeout(10_000) {
            suspendCancellableCoroutine { continuation ->
                peer.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(value: SessionDescription) = Unit
                    override fun onSetSuccess() {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    override fun onCreateFailure(error: String) = Unit
                    override fun onSetFailure(error: String) {
                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error))
                    }
                }, description)
            }
        }
    }
}
