package com.fynx.app.ui

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SessionDescription
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

/** WebRTC transport for FYNX AI realtime voice. The provider credential remains server-side. */
class FynxAiWebRtcEngine(
    context: Context,
    private val iceServers: List<PeerConnection.IceServer> = emptyList()
) {
    enum class State { IDLE, CONNECTING, CONNECTED, FAILED, CLOSED }
    private val appContext = context.applicationContext
    private val factory: PeerConnectionFactory
    private var toolJob = SupervisorJob()
    private var toolScope = CoroutineScope(toolJob + Dispatchers.IO)
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var remoteAudioTrack: AudioTrack? = null
    private var eventsChannel: DataChannel? = null
    private var localDescriptionReady: CompletableDeferred<String>? = null
    private var iceGatheringReady: CompletableDeferred<Unit>? = null
    private var dataChannelReady: CompletableDeferred<Unit>? = null
    private val handledToolCalls = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val realtimeToolCallCount = AtomicInteger(0)
    private val maxRealtimeToolCalls = 8
    private var state: State = State.IDLE
    private var onStateChanged: ((State, String?) -> Unit)? = null
    private var onEvent: ((String) -> Unit)? = null
    init { PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions()); factory = PeerConnectionFactory.builder().createPeerConnectionFactory() }
    suspend fun connect(onStateChanged: (State, String?) -> Unit = { _, _ -> }, onEvent: (String) -> Unit = {}): Result<Unit> = runCatching {
        require(state != State.CONNECTING && state != State.CONNECTED) { "FYNX AI voice is already connected" }
        if (toolJob.isCancelled) {
            toolJob = SupervisorJob()
            toolScope = CoroutineScope(toolJob + Dispatchers.IO)
        }
        handledToolCalls.clear()
        realtimeToolCallCount.set(0)
        this.onStateChanged = onStateChanged; this.onEvent = onEvent; setState(State.CONNECTING, null)
        val connection = factory.createPeerConnection(PeerConnection.RTCConfiguration(iceServers), observer()) ?: error("Unable to create FYNX AI peer connection")
        peerConnection = connection
        audioSource = factory.createAudioSource(MediaConstraints()); audioTrack = factory.createAudioTrack("fynx-ai-microphone", audioSource); audioTrack?.setEnabled(true); audioTrack?.let { connection.addTrack(it) }
        dataChannelReady = CompletableDeferred()
        eventsChannel = connection.createDataChannel("oai-events", DataChannel.Init()).also { it.registerObserver(dataChannelObserver()) }
        val offer = createOffer(connection); iceGatheringReady = CompletableDeferred(); connection.setLocalDescriptionAwait(offer); awaitLocalDescription(); iceGatheringReady?.awaitWithTimeout()
        val localSdp = connection.localDescription?.description ?: error("FYNX AI local SDP is unavailable")
        val answerSdp = FynxAiVoiceSession.requestSession(appContext, localSdp).getOrThrow(); require(answerSdp.isNotBlank()) { "FYNX AI returned an empty SDP answer" }
        connection.setRemoteDescriptionAwait(SessionDescription(SessionDescription.Type.ANSWER, answerSdp)); setState(State.CONNECTED, null); dataChannelReady?.awaitWithTimeout(); if (!sendEvent("{\"type\":\"response.create\"}")) error("FYNX AI voice event channel is unavailable"); Unit
    }.onFailure { error -> setState(State.FAILED, error.message ?: "FYNX AI voice connection failed"); close() }
    fun setMicrophoneEnabled(enabled: Boolean) { audioTrack?.setEnabled(enabled) }
    fun sendEvent(json: String): Boolean { val channel = eventsChannel ?: return false; if (channel.state() != DataChannel.State.OPEN) return false; return channel.send(DataChannel.Buffer(java.nio.ByteBuffer.wrap(json.toByteArray(StandardCharsets.UTF_8)), false)) }
    fun close() {
        localDescriptionReady?.cancel(); localDescriptionReady = null; iceGatheringReady?.cancel(); iceGatheringReady = null; eventsChannel?.dispose(); eventsChannel = null; remoteAudioTrack?.setEnabled(false); remoteAudioTrack = null; peerConnection?.close(); peerConnection?.dispose(); peerConnection = null; audioTrack?.dispose(); audioTrack = null; audioSource?.dispose(); audioSource = null; toolJob.cancel(); if (state != State.FAILED) setState(State.CLOSED, null)
    }
    private suspend fun createOffer(connection: PeerConnection): SessionDescription = CompletableDeferred<SessionDescription>().also { deferred -> connection.createOffer(object : SdpObserverAdapter() { override fun onCreateSuccess(description: SessionDescription) { deferred.complete(description) }; override fun onCreateFailure(error: String) { deferred.completeExceptionally(IllegalStateException(error)) } }, MediaConstraints()) }.awaitWithTimeout()
    private suspend fun awaitLocalDescription() { localDescriptionReady?.awaitWithTimeout() ?: error("Local FYNX AI SDP was not prepared") }
    private fun PeerConnection.setLocalDescriptionAwait(description: SessionDescription) { localDescriptionReady = CompletableDeferred(); setLocalDescription(object : SdpObserverAdapter() { override fun onSetSuccess() { localDescriptionReady?.complete(description.description) }; override fun onSetFailure(error: String) { localDescriptionReady?.completeExceptionally(IllegalStateException(error)) } }, description) }
    private suspend fun PeerConnection.setRemoteDescriptionAwait(description: SessionDescription) = CompletableDeferred<Unit>().also { deferred -> setRemoteDescription(object : SdpObserverAdapter() { override fun onSetSuccess() { deferred.complete(Unit) }; override fun onSetFailure(error: String) { deferred.completeExceptionally(IllegalStateException(error)) } }, description) }.awaitWithTimeout()
    private fun dataChannelObserver() = object : DataChannel.Observer { override fun onBufferedAmountChange(previousAmount: Long) = Unit; override fun onStateChange() { if (eventsChannel?.state() == DataChannel.State.OPEN) dataChannelReady?.complete(Unit) }; override fun onMessage(buffer: DataChannel.Buffer) { if (buffer.binary) return; val bytes = ByteArray(buffer.data.remaining()); buffer.data.get(bytes); val event = String(bytes, StandardCharsets.UTF_8); if (event.isNotBlank()) { onEvent?.invoke(event); handleRealtimeToolEvent(event) } } }
    private fun handleRealtimeToolEvent(event: String) {
        val json = runCatching { JSONObject(event) }.getOrNull() ?: return
        if (json.optString("type") != "response.function_call_arguments.done") return
        val callId = json.optString("call_id").trim()
        val name = json.optString("name").trim()
        val arguments = json.optString("arguments", "{}")
        if (callId.isBlank() || name.isBlank()) return
        if (!handledToolCalls.add(callId)) return
        if (realtimeToolCallCount.incrementAndGet() > maxRealtimeToolCalls) {
            val limitOutput = JSONObject().put("error", "realtime AI tool-processing limit reached").toString()
            val limitResponse = JSONObject()
                .put("type", "conversation.item.create")
                .put("item", JSONObject().put("type", "function_call_output").put("call_id", callId).put("output", limitOutput))
            sendEvent(limitResponse.toString())
            onStateChanged?.invoke(State.FAILED, "FYNX AI realtime tool-processing limit reached")
            return
        }
        toolScope.launch {
            val result = FynxAiVoiceSession.executeTool(appContext, name, arguments)
            val output = result.getOrElse { error -> JSONObject().put("error", error.message ?: "tool request failed").toString() }
            val response = JSONObject()
                .put("type", "conversation.item.create")
                .put("item", JSONObject().put("type", "function_call_output").put("call_id", callId).put("output", output))
            sendEvent(response.toString())
            sendEvent("{\"type\":\"response.create\"}")
        }
    }
    private suspend fun <T> CompletableDeferred<T>.awaitWithTimeout(): T = withTimeout(15_000) { await() }
    private fun observer() = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) { if (state == PeerConnection.IceConnectionState.FAILED) setState(State.FAILED, "FYNX AI network connection failed") }
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) { if (state == PeerConnection.IceGatheringState.COMPLETE) iceGatheringReady?.complete(Unit) }
        override fun onIceCandidate(candidate: org.webrtc.IceCandidate) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out org.webrtc.IceCandidate>) = Unit
        override fun onAddStream(stream: org.webrtc.MediaStream) = Unit
        override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit
        override fun onDataChannel(channel: DataChannel) { if (eventsChannel == null) { eventsChannel = channel; channel.registerObserver(dataChannelObserver()) } }
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out org.webrtc.MediaStream>) { (receiver.track() as? AudioTrack)?.let { remoteAudioTrack = it; it.setEnabled(true) } }
    }
    private fun setState(next: State, error: String?) { state = next; onStateChanged?.invoke(next, error) }
    private open class SdpObserverAdapter : org.webrtc.SdpObserver { override fun onCreateSuccess(description: SessionDescription) = Unit; override fun onSetSuccess() = Unit; override fun onCreateFailure(error: String) = Unit; override fun onSetFailure(error: String) = Unit }
}
