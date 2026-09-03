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
import java.util.concurrent.TimeUnit

/** Authenticated realtime transport for messages and ephemeral chat signals. */
class FynxRealtimeClient(
    private val context: Context,
    private val onMessage: (FynxProductionMessaging.RemoteMessage) -> Unit,
    private val onStateChanged: (State) -> Unit = {},
    private val onEvent: (Event) -> Unit = {}
) {
    enum class State { CONNECTING, CONNECTED, DISCONNECTED, FAILED }

    sealed interface Event {
        data class MessageStatus(val messageId: String, val status: Status) : Event
        data class Typing(val userId: String, val isTyping: Boolean) : Event
        data class Presence(val userId: String, val online: Boolean) : Event
    }

    enum class Status { SENT, DELIVERED, READ }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
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
        if (token.isNullOrBlank()) {
            onStateChanged(State.FAILED)
            return
        }
        val httpBase = FynxBackendClient.baseUrl(context)
        if (!httpBase.startsWith("https://")) {
            onStateChanged(State.FAILED)
            return
        }
        val wsUrl = "wss://${httpBase.removePrefix("https://")}/realtime"
        onStateChanged(State.CONNECTING)
        val request = Request.Builder()
            .url(wsUrl)
            .header("Authorization", "Bearer $token")
            .build()
        socket?.cancel()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                reconnectAttempt = 0
                onStateChanged(State.CONNECTED)
                flushPending(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val root = JSONObject(text)
                    when (root.optString("type")) {
                        "message" -> root.optJSONObject("message")?.let { onMessage(FynxProductionMessaging.fromJson(it)) }
                        "message_status" -> {
                            val status = when (root.optString("status")) {
                                "read" -> Status.READ
                                "delivered" -> Status.DELIVERED
                                else -> Status.SENT
                            }
                            onEvent(Event.MessageStatus(root.optString("messageId"), status))
                        }
                        "typing" -> onEvent(Event.Typing(root.optString("userId"), root.optBoolean("isTyping")))
                        "presence" -> onEvent(Event.Presence(root.optString("userId"), root.optBoolean("online")))
                    }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (socket === webSocket) socket = null
                onStateChanged(State.DISCONNECTED)
                if (code != 1000 && code != 1008 && code != 1003) scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (socket === webSocket) socket = null
                if (response?.code == 401 || response?.code == 403) {
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
        val delayMs = (1000L shl (reconnectAttempt - 1)).coerceAtMost(30_000L)
        reconnectHandler.postDelayed({ connectInternal() }, delayMs)
    }

    fun sendTyping(recipientId: String, isTyping: Boolean) {
        sendJson(JSONObject().apply {
            put("type", "typing")
            put("recipientId", recipientId)
            put("isTyping", isTyping)
        })
    }

    fun sendRead(messageIds: List<String>) {
        val ids = messageIds.mapNotNull { it.toLongOrNull() }.take(100)
        if (ids.isEmpty()) return
        sendJson(JSONObject().apply {
            put("type", "read")
            put("messageIds", JSONArray(ids))
        })
    }

    fun acknowledgeMessage(messageId: String) {
        val id = messageId.toLongOrNull() ?: return
        sendJson(JSONObject().apply {
            put("type", "message_ack")
            put("messageId", id)
        })
    }

    private fun sendJson(payload: JSONObject) {
        val value = payload.toString()
        val active = socket
        if (active?.send(value) == true) return
        synchronized(pendingLock) {
            if (pendingPayloads.size >= 100) pendingPayloads.removeFirst()
            pendingPayloads.addLast(value)
        }
    }

    private fun flushPending(webSocket: WebSocket) {
        while (true) {
            val next = synchronized(pendingLock) {
                if (pendingPayloads.isEmpty()) null else pendingPayloads.removeFirst()
            } ?: break
            if (!webSocket.send(next)) {
                synchronized(pendingLock) {
                    pendingPayloads.addFirst(next)
                }
                break
            }
        }
    }

    fun close() {
        manuallyClosed = true
        reconnectHandler.removeCallbacksAndMessages(null)
        synchronized(pendingLock) { pendingPayloads.clear() }
        socket?.close(1000, "FYNX conversation closed")
        socket = null
        onStateChanged(State.DISCONNECTED)
        client.connectionPool.evictAll()
    }
}
