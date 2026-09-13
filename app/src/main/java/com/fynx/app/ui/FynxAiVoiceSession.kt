package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

/**
 * Contract for FYNX's realtime AI voice mode.
 *
 * The permanent OpenAI credential never belongs in this class or the APK.
 * The backend owns authorization, realtime session configuration and tool execution.
 */
object FynxAiVoiceSession {
    enum class State { IDLE, CONNECTING, LISTENING, THINKING, SPEAKING, ERROR }

    data class Config(
        val endpoint: String = "/api/assistant/realtime-session",
        val toolEndpoint: String = "/api/assistant/realtime-tool",
        val preferredVoice: String = "marin"
    )

    suspend fun requestSession(context: Context, sdpOffer: String): Result<String> = runCatching {
        require(sdpOffer.isNotBlank()) { "SDP offer is required" }
        val body = JSONObject().put("sdp", sdpOffer).toString()
        FynxBackendClient.postJson(context, Config().endpoint, body).getOrThrow()
    }

    suspend fun executeTool(context: Context, name: String, argumentsJson: String): Result<String> = runCatching {
        require(name.isNotBlank()) { "AI tool name is required" }
        val body = JSONObject()
            .put("name", name)
            .put("arguments", argumentsJson)
            .toString()
        FynxBackendClient.postJson(context, Config().toolEndpoint, body).getOrThrow()
    }
}
