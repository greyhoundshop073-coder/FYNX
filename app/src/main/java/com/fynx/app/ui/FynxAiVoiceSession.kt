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
        val hangupEndpoint: String = "/api/assistant/realtime-session/hangup",
        val preferredVoice: String = "marin"
    )

    suspend fun requestSession(context: Context, sdpOffer: String, mode: String = "assistant", voice: String = "marin"): Result<String> = runCatching {
        require(sdpOffer.isNotBlank()) { "SDP offer is required" }
        require(mode in setOf("assistant", "transcription", "speech")) { "Unsupported FYNX voice session mode" }
        val body = JSONObject().put("sdp", sdpOffer).put("mode", mode).put("voice", voice).toString()
        FynxBackendClient.postJson(context, Config().endpoint, body).getOrThrow()
    }

    /** Ends the authenticated user's active Realtime call and settles its provider usage. */
    suspend fun endSession(context: Context): Result<String> = runCatching {
        FynxBackendClient.postJson(
            context,
            Config().hangupEndpoint,
            JSONObject().put("reason", "client_closed").toString()
        ).getOrThrow()
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
