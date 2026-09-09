package com.fynx.app.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.IceCandidate
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Authenticated realtime transport for messages and ephemeral chat/call signals. */
class FynxRealtimeClient(
    private val context: Context,
    private val onMessage: (FynxProductionMessaging.RemoteMessage) -> Unit,
    private val onStateChanged: (State) -> Unit = {},
    private val onEvent: (Event) -> Unit = {}
) {
    enum class State { CONNECTING, CONNECTED, DISCONNECTED, FAILED }

    interface Event {
        data class MessageStatus(val messageId: String, val status: Status) : Event
        data class Typing(val userId: String, val isTyping: Boolean) : Event
        data class Presence(val userId: String, val online: Boolean) : Event
        data class Call(val callId: String, val callType: String, val fromUserId: String, val toUserId: String, val signalType: String, val sdp: String? = null, val candidate: IceCandidatePayload? = null, val fromUsername: String? = null, val error: String? = null) : Event
    }

    data class IceCandidatePayload(val candidate: String, val sdpMid: String?, val sdpMLineIndex: Int?, val usernameFragment: String?) {
        fun toWebRtcCandidate(): IceCandidate = IceCandidate(sdpMid, sdpMLineIndex ?: 0, candidate)
    }

    enum class Status { SENT, DELIVERED, READ }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private val pendingLock = Any()
    private val pendingPayloads = ArrayDeque<String>()
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var socket: WebSocket? = null
    private var manuallyClosed = false
    private var reconnectAttempt = 0

    fun connect() {
        manuallyClosed = false
        reconnectAttempt = 0
        reconnectHandler.removeCallbacksAndMessages(null)
        registerNetworkCallback()
        connectInternal()
    }

    private fun registerNetworkCallback() {
        val manager = connectivityManager ?: return
        if (networkCallback != null) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (manuallyClosed) return
                reconnectHandler.removeCallbacksAndMessages(null)
                reconnectAttempt = 0
                if (socket == null) connectInternal()
            }

            override fun onLost(network: Network) {
                if (manuallyClosed) return
                if (!hasUsableNetwork()) {
                    socket?.cancel()
                    socket = null
                    onStateChanged(State.DISCONNECTED)
                }
            }
        }
        runCatching { manager.registerNetworkCallback(NetworkRequestFactory.create(), callback) }
            .onSuccess { networkCallback = callback }
    }

    private fun unregisterNetworkCallback() {
        val manager = connectivityManager
        val callback = networkCallback ?: return
        networkCallback = null
        runCatching { manager?.unregisterNetworkCallback(callback) }
    }

    private fun hasUsableNetwork(): Boolean = runCatching {
        val manager = connectivityManager ?: return@runCatching true
        val network = manager.activeNetwork ?: return@runCatching false
        val capabilities = manager.getNetworkCapabilities(network) ?: return@runCatching false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(true)

    private fun connectInternal() {
        if (manuallyClosed) return
        if (!hasUsableNetwork()) {
            onStateChanged(State.DISCONNECTED)
            scheduleReconnect()
            return
        }
        val token = FynxBackendClient.accessToken(context)
        if (token.isNullOrBlank()) { onStateChanged(State.FAILED); return }
        val httpBase = FynxBackendClient.baseUrl(context)
        if (!httpBase.startsWith("https://")) { onStateChanged(State.FAILED); return }
        val encodedToken = URLEncoder.encode(token, Charsets.UTF_8.name())
        val wsUrl = "wss://${httpBase.removePrefix("https://")}/realtime?token=$encodedToken"
        onStateChanged(State.CONNECTING)
        socket?.cancel()
        socket = client.newWebSocket(Request.Builder().url(wsUrl).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (manuallyClosed) { webSocket.close(1000, "FYNX conversation closed"); return }
                reconnectAttempt = 0
                onStateChanged(State.CONNECTED)
                flushPending(webSocket)
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { val root = JSONObject(text); when (root.optString("type")) {
                    "message" -> root.optJSONObject("message")?.let { onMessage(FynxProductionMessaging.fromJson(it)) }
                    "message_status" -> onEvent(Event.MessageStatus(root.optString("messageId"), when (root.optString("status")) { "read" -> Status.READ; "delivered" -> Status.DELIVERED; else -> Status.SENT }))
                    "typing" -> onEvent(Event.Typing(root.optString("userId"), root.optBoolean("isTyping")))
                    "presence" -> onEvent(Event.Presence(root.optString("userId"), root.optBoolean("online")))
                    "call" -> parseCallEvent(root)?.let { callEvent ->
                        if (callEvent.signalType == "invite") {
                            val caller = callEvent.fromUsername?.removePrefix("@").orEmpty().ifBlank { callEvent.fromUserId }
                            val kind = if (callEvent.callType == "video") "Video call" else "Voice call"
                            FynxNotificationFoundation.show(
                                context,
                                FynxNotificationFoundation.MESSAGES_CHANNEL,
                                callEvent.callId.hashCode(),
                                "Incoming $kind 📞",
                                "@$caller is calling you.",
                                stableKey = "incoming-call:${callEvent.callId}"
                            )
                        }
                        onEvent(callEvent)
                    }
                } }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                val current = socket === webSocket
                if (current) socket = null
                if (!current || manuallyClosed) return
                onStateChanged(State.DISCONNECTED)
                if (FynxCallTransportHardening.shouldRetrySocket(code)) scheduleReconnect()
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val current = socket === webSocket
                if (current) socket = null
                if (!current || manuallyClosed) return
                if (FynxCallTransportHardening.isAuthFailure(response?.code)) {
                    FynxBackendClient.saveAccessToken(context, null)
                    onStateChanged(State.FAILED)
                    return
                }
                onStateChanged(State.FAILED)
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (manuallyClosed) return
        reconnectHandler.removeCallbacksAndMessages(null)
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(6)
        val exponentialDelay = (1000L shl (reconnectAttempt - 1)).coerceAtMost(30_000L)
        val jitter = Random.nextLong(0L, 501L)
        reconnectHandler.postDelayed({ connectInternal() }, exponentialDelay + jitter)
    }

    private fun parseCallEvent(root: JSONObject): Event.Call? {
        val rawCallId = root.optString("callId").trim()
        val callId = when {
            rawCallId.startsWith("call_") -> rawCallId.replaceFirst("call_", "call-")
            else -> rawCallId
        }
        val callType = root.optString("callType", "voice").trim().lowercase()
        val signalType = root.optString("signalType").trim().lowercase()
        val fromUserId = root.optString("fromUserId").trim()
        val toUserId = root.optString("toUserId").trim()
        if (!FynxCallTransportHardening.isValidCallId(callId) ||
            !FynxCallTransportHardening.isValidCallSignal(signalType) ||
            !FynxCallTransportHardening.isValidCallType(callType) ||
            fromUserId.isBlank() || toUserId.isBlank() || fromUserId == toUserId) return null
        val c = root.optJSONObject("candidate")?.let {
            val candidate = it.optString("candidate").trim()
            if (candidate.isBlank() || candidate.length > 20_000) return@let null
            IceCandidatePayload(candidate, if (it.isNull("sdpMid")) null else it.optString("sdpMid").take(100), if (it.isNull("sdpMLineIndex")) null else it.optInt("sdpMLineIndex"), if (it.isNull("usernameFragment")) null else it.optString("usernameFragment").take(200))
        }
        val sdp = root.optString("sdp").takeIf { it.isNotBlank() && it.length <= 200_000 }
        if ((signalType == "offer" || signalType == "answer") && sdp == null) return null
        if (signalType == "ice" && c == null) return null
        return Event.Call(callId, callType, fromUserId, toUserId, signalType, sdp, c, root.optString("fromUsername").takeIf { it.isNotBlank() }, root.optString("error").takeIf { it.isNotBlank() })
    }

    fun sendCallInvite(callId: String, targetUserId: String, video: Boolean) = sendCall(callId, targetUserId, if (video) "video" else "voice", "invite")
    fun sendCallAccept(callId: String, targetUserId: String, video: Boolean) = sendCall(callId, targetUserId, if (video) "video" else "voice", "accept")
    fun sendCallReject(callId: String, targetUserId: String, video: Boolean) = sendCall(callId, targetUserId, if (video) "video" else "voice", "reject")
    fun sendCallEnd(callId: String, targetUserId: String, video: Boolean) = sendCall(callId, targetUserId, if (video) "video" else "voice", "end")
    fun sendCallOffer(callId: String, targetUserId: String, sdp: String, video: Boolean) = sendCall(callId, targetUserId, if (video) "video" else "voice", "offer", JSONObject().put("sdp", sdp))
    fun sendCallAnswer(callId: String, targetUserId: String, sdp: String, video: Boolean) = sendCall(callId, targetUserId, if (video) "video" else "voice", "answer", JSONObject().put("sdp", sdp))
    fun sendCallIce(callId: String, targetUserId: String, candidate: IceCandidate, video: Boolean) = sendCall(callId, targetUserId, if (video) "video" else "voice", "ice", JSONObject().put("candidate", JSONObject().apply { put("candidate", candidate.sdp); put("sdpMid", candidate.sdpMid); put("sdpMLineIndex", candidate.sdpMLineIndex) }))
    private fun sendCall(callId: String, targetUserId: String, callType: String, signalType: String, extra: JSONObject? = null) { sendJson(JSONObject().apply { put("type", "call"); put("callId", callId.replaceFirst("call-", "call_").take(80)); put("toUserId", targetUserId); put("callType", callType); put("signalType", signalType); extra?.keys()?.forEach { put(it, extra.get(it)) } }) }
    fun sendTyping(recipientId: String, isTyping: Boolean) = sendJson(JSONObject().apply { put("type", "typing"); put("recipientId", recipientId); put("isTyping", isTyping) })
    fun sendRead(messageIds: List<String>) { val ids = messageIds.mapNotNull { it.toLongOrNull() }.take(100); if (ids.isNotEmpty()) sendJson(JSONObject().apply { put("type", "read"); put("messageIds", JSONArray(ids)) }) }
    fun acknowledgeMessage(messageId: String) { messageId.toLongOrNull()?.let { sendJson(JSONObject().apply { put("type", "message_ack"); put("messageId", it) }) } }
    private fun sendJson(payload: JSONObject) { val value = payload.toString(); if (socket?.send(value) == true) return; synchronized(pendingLock) { if (pendingPayloads.size >= 100) pendingPayloads.removeFirst(); pendingPayloads.addLast(value) } }
    private fun flushPending(webSocket: WebSocket) { while (true) { val next = synchronized(pendingLock) { if (pendingPayloads.isEmpty()) null else pendingPayloads.removeFirst() } ?: break; if (!webSocket.send(next)) { synchronized(pendingLock) { pendingPayloads.addFirst(next) }; break } } }
    fun close() { manuallyClosed = true; reconnectHandler.removeCallbacksAndMessages(null); unregisterNetworkCallback(); synchronized(pendingLock) { pendingPayloads.clear() }; socket?.close(1000, "FYNX conversation closed"); socket = null; onStateChanged(State.DISCONNECTED); client.connectionPool.evictAll() }
}

private object NetworkRequestFactory {
    fun create(): android.net.NetworkRequest = android.net.NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()
}
