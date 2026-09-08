package com.fynx.app.ui

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SessionDescription
import java.nio.charset.StandardCharsets

/** WebRTC transport for FYNX AI realtime voice. The provider credential remains server-side. */
class FynxAiWebRtcEngine(
    context: Context,
    private val iceServers: List<PeerConnection.IceServer> = emptyList()
) {
    enum class State { IDLE, CONNECTING, CONNECTED, FAILED, CLOSED }

    private val appContext = context.applicationContext
    private val factory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var remoteAudioTrack: AudioTrack? = null
    private var eventsChannel: DataChannel? = null
    private var localDescriptionReady: CompletableDeferred<String>? = null
    private var iceGatheringReady: CompletableDeferred<Unit>? = null
    private var state: State = State.IDLE
    private var onStateChanged: ((State, String?) -> Unit)? = null
    private var onEvent: ((String) -> Unit)? = null

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
    }

    suspend fun connect(
        onStateChanged: (State, String?) -> Unit = { _, _ -> },
        onEvent: (String) -> Unit = {}
    ): Result<Unit> = runCatching {
        require(state != State.CONNECTING && state != State.CONNECTED) { "FYNX AI voice is already connected" }
        this.onStateChanged = onStateChanged
        this.onEvent = onEvent
        setState(State.CONNECTING, null)

        val connection = factory.createPeerConnection(
            PeerConnection.RTCConfiguration(iceServers),
            observer()
        ) ?: error("Unable to create FYNX AI peer connection")
        peerConnection = connection

        audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("fynx-ai-microphone", audioSource)
        audioTrack?.setEnabled(true)
        audioTrack?.let { connection.addTrack(it) }

        eventsChannel = connection.createDataChannel("oai-events", DataChannel.Init()).also { channel ->
            channel.registerObserver(dataChannelObserver())
        }

        val offer = createOffer(connection)
        iceGatheringReady = CompletableDeferred()
        connection.setLocalDescriptionAwait(offer)
        awaitLocalDescription()
        iceGatheringReady?.awaitWithTimeout()
        val localSdp = connection.localDescription?.description
            ?: error("FYNX AI local SDP is unavailable")

        val answerSdp = FynxAiVoiceSession.requestSession(appContext, localSdp).getOrThrow()
        require(answerSdp.isNotBlank()) { "FYNX AI returned an empty SDP answer" }
        connection.setRemoteDescriptionAwait(
            SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        )

        setState(State.CONNECTED, null)
        sendEvent("{\"type\":\"response.create\"}")
    }.onFailure { error ->
        setState(State.FAILED, error.message ?: "FYNX AI voice connection failed")
        close()
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        audioTrack?.setEnabled(enabled)
    }

    fun sendEvent(json: String): Boolean {
        val channel = eventsChannel ?: return false
        if (channel.state() != DataChannel.State.OPEN) return false
        return channel.send(DataChannel.Buffer(java.nio.ByteBuffer.wrap(json.toByteArray(StandardCharsets.UTF_8)), false))
    }

    fun close() {
        localDescriptionReady?.cancel()
        localDescriptionReady = null
        iceGatheringReady?.cancel()
        iceGatheringReady = null
        eventsChannel?.dispose()
        eventsChannel = null
        remoteAudioTrack?.setEnabled(false)
        remoteAudioTrack = null
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        audioTrack?.dispose()
        audioTrack = null
        audioSource?.dispose()
        audioSource = null
        if (state != State.FAILED) setState(State.CLOSED, null)
    }

    private suspend fun createOffer(connection: PeerConnection): SessionDescription =
        CompletableDeferred<SessionDescription>().also { deferred ->
            connection.createOffer(object : SdpObserverAdapter() {
                override fun onCreateSuccess(description: SessionDescription) { deferred.complete(description) }
                override fun onCreateFailure(error: String) { deferred.completeExceptionally(IllegalStateException(error)) }
            }, MediaConstraints())
        }.awaitWithTimeout()

    private suspend fun awaitLocalDescription() {
        localDescriptionReady?.awaitWithTimeout() ?: error("Local FYNX AI SDP was not prepared")
    }

    private fun PeerConnection.setLocalDescriptionAwait(description: SessionDescription) {
        localDescriptionReady = CompletableDeferred()
        setLocalDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() { localDescriptionReady?.complete(description.description) }
            override fun onSetFailure(error: String) { localDescriptionReady?.completeExceptionally(IllegalStateException(error)) }
        }, description)
    }

    private suspend fun PeerConnection.setRemoteDescriptionAwait(description: SessionDescription) {
        CompletableDeferred<Unit>().also { deferred ->
            setRemoteDescription(object : SdpObserverAdapter() {
                override fun onSetSuccess() { deferred.complete(Unit) }
                override fun onSetFailure(error: String) { deferred.completeExceptionally(IllegalStateException(error)) }
            }, description)
        }.awaitWithTimeout()
    }

    private fun dataChannelObserver() = object : DataChannel.Observer {
        override fun onBufferedAmountChange(previousAmount: Long) = Unit
        override fun onStateChange() = Unit
        override fun onMessage(buffer: DataChannel.Buffer) {
            if (buffer.binary) return
            val bytes = ByteArray(buffer.data.remaining())
            buffer.data.get(bytes)
            val event = String(bytes, StandardCharsets.UTF_8)
            if (event.isNotBlank()) onEvent?.invoke(event)
        }
    }

    private suspend fun <T> CompletableDeferred<T>.awaitWithTimeout(): T = withTimeout(15_000) { await() }

    private fun observer() = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            if (state == PeerConnection.IceConnectionState.FAILED) setState(State.FAILED, "FYNX AI network connection failed")
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
            if (state == PeerConnection.IceGatheringState.COMPLETE) iceGatheringReady?.complete(Unit)
        }
        override fun onIceCandidate(candidate: org.webrtc.IceCandidate) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out org.webrtc.IceCandidate>) = Unit
        override fun onAddStream(stream: org.webrtc.MediaStream) = Unit
        override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit
        override fun onDataChannel(channel: DataChannel) {
            if (eventsChannel == null) {
                eventsChannel = channel
                channel.registerObserver(dataChannelObserver())
            }
        }
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out org.webrtc.MediaStream>) {
            (receiver.track() as? AudioTrack)?.let { track ->
                remoteAudioTrack = track
                track.setEnabled(true)
            }
        }
    }

    private fun setState(next: State, error: String?) {
        state = next
        onStateChanged?.invoke(next, error)
    }

    private open class SdpObserverAdapter : org.webrtc.SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String) = Unit
        override fun onSetFailure(error: String) = Unit
    }
}
