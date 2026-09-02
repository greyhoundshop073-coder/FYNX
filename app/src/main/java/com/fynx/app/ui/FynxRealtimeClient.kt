package com.fynx.app.ui

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Authenticated realtime transport for server-pushed FYNX messages. */
class FynxRealtimeClient(
    private val context: Context,
    private val onMessage: (FynxProductionMessaging.RemoteMessage) -> Unit,
    private val onStateChanged: (State) -> Unit = {}
) {
    enum class State { CONNECTING, CONNECTED, DISCONNECTED, FAILED }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var socket: WebSocket? = null

    fun connect() {
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
                onStateChanged(State.CONNECTED)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val root = JSONObject(text)
                    if (root.optString("type") != "message") return
                    val item = root.optJSONObject("message") ?: return
                    onMessage(
                        FynxProductionMessaging.RemoteMessage(
                            id = item.optString("id"),
                            senderId = item.optString("senderId"),
                            recipientId = item.optString("recipientId"),
                            text = item.optString("text"),
                            timestamp = item.optDouble("timestamp", 0.0).toLong(),
                            edited = item.optBoolean("edited"),
                            deleted = item.optBoolean("deleted"),
                            replyToId = item.optString("replyToId").takeIf { it.isNotBlank() && it != "null" }
                        )
                    )
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onStateChanged(State.DISCONNECTED)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onStateChanged(State.FAILED)
            }
        })
    }

    fun close() {
        socket?.close(1000, "FYNX conversation closed")
        socket = null
        onStateChanged(State.DISCONNECTED)
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
