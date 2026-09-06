package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

/**
 * Contract for FYNX's realtime AI voice mode.
 *
 * The permanent OpenAI credential never belongs in this class or the APK.
 * The backend owns authorization and the realtime session configuration.
 */
object FynxAiVoiceSession {
    enum class State { IDLE, CONNECTING, LISTENING, THINKING, SPEAKING, ERROR }

    data class Config(
        val endpoint: String = "/api/assistant/realtime-session",
        val preferredVoice: String = "marin"
    )

    suspend fun requestSession(context: Context, sdpOffer: String): Result<String> = runCatching {
        require(sdpOffer.isNotBlank()) { "SDP offer is required" }
        val body = JSONObject().put("sdp", sdpOffer).toString()
        FynxBackendClient.postJson(context, Config().endpoint, body).getOrThrow()
    }
}
