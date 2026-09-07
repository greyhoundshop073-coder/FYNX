package com.fynx.app.ui

import android.content.Context
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
import java.util.concurrent.TimeUnit

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
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(30, TimeUnit.SECONDS).build()
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private val pendingLock = Any()
    private val pendingPayloads = ArrayDeque<String>()
    private var socket: WebSocket? = null
    private var manuallyClosed = false
    private var reconnectAttempt = 0

    fun connect() {
        manuallyClosed = false
        reconnectAttempt = 0
        reconnectHandler.removeCallbacksAndMessages(null)
        connectInternal()
    }

    private fun connectInternal() {
        if (manuallyClosed) return
        val token = FynxBackendClient.accessToken(context)
        if (token.isNullOrBlank()) { onStateChanged(State.FAILED); return }
        val httpBase = FynxBackendClient.baseUrl(context)
        if (!httpBase.startsWith("https://")) { onStateChanged(State.FAILED); return }
        val encodedToken = java.net.URLEncoder.encode(token, Charsets.UTF_8.name())
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
                    "call" -> parseCallEvent(root)?.let(onEvent)
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
        reconnectHandler.postDelayed({ connectInternal() }, (1000L shl (reconnectAttempt - 1)).coerceAtMost(30_000L))
    }

    private fun parseCallEvent(root: JSONObject): Event.Call? {
        val callId = root.optString("callId").trim()
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
    private fun sendCall(callId: String, targetUserId: String, callType: String, signalType: String, extra: JSONObject? = null) { sendJson(JSONObject().apply { put("type", "call"); put("callId", callId); put("toUserId", targetUserId); put("callType", callType); put("signalType", signalType); extra?.keys()?.forEach { put(it, extra.get(it)) } }) }
    fun sendTyping(recipientId: String, isTyping: Boolean) = sendJson(JSONObject().apply { put("type", "typing"); put("recipientId", recipientId); put("isTyping", isTyping) })
    fun sendRead(messageIds: List<String>) { val ids = messageIds.mapNotNull { it.toLongOrNull() }.take(100); if (ids.isNotEmpty()) sendJson(JSONObject().apply { put("type", "read"); put("messageIds", JSONArray(ids)) }) }
    fun acknowledgeMessage(messageId: String) { messageId.toLongOrNull()?.let { sendJson(JSONObject().apply { put("type", "message_ack"); put("messageId", it) }) } }
    private fun sendJson(payload: JSONObject) { val value = payload.toString(); if (socket?.send(value) == true) return; synchronized(pendingLock) { if (pendingPayloads.size >= 100) pendingPayloads.removeFirst(); pendingPayloads.addLast(value) } }
    private fun flushPending(webSocket: WebSocket) { while (true) { val next = synchronized(pendingLock) { if (pendingPayloads.isEmpty()) null else pendingPayloads.removeFirst() } ?: break; if (!webSocket.send(next)) { synchronized(pendingLock) { pendingPayloads.addFirst(next) }; break } } }
    fun close() { manuallyClosed = true; reconnectHandler.removeCallbacksAndMessages(null); synchronized(pendingLock) { pendingPayloads.clear() }; socket?.close(1000, "FYNX conversation closed"); socket = null; onStateChanged(State.DISCONNECTED); client.connectionPool.evictAll() }
}