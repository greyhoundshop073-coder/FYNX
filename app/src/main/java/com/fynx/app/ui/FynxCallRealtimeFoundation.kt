package com.fynx.app.ui

/** Transport-neutral realtime call contracts; a WebRTC/signaling implementation can attach here. */
enum class FynxCallConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, FAILED }

data class FynxCallConnection(
    val sessionId: String,
    val state: FynxCallConnectionState = FynxCallConnectionState.DISCONNECTED,
    val attempt: Int = 0,
    val lastError: String? = null
)

object FynxCallRealtimeFoundation {
    fun connect(connection: FynxCallConnection): FynxCallConnection =
        connection.copy(state = FynxCallConnectionState.CONNECTING, lastError = null)

    fun connected(connection: FynxCallConnection): FynxCallConnection =
        connection.copy(state = FynxCallConnectionState.CONNECTED, lastError = null)

    fun reconnect(connection: FynxCallConnection): FynxCallConnection =
        connection.copy(state = FynxCallConnectionState.RECONNECTING, attempt = connection.attempt + 1)

    fun failed(connection: FynxCallConnection, error: String): FynxCallConnection =
        connection.copy(state = FynxCallConnectionState.FAILED, lastError = error)

    fun disconnect(connection: FynxCallConnection): FynxCallConnection =
        connection.copy(state = FynxCallConnectionState.DISCONNECTED)
}
