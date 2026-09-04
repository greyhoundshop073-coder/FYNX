package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

/** Authenticated client for the FYNX AI backend. */
object AiAssistantClient {
    suspend fun sendMessage(context: Context, message: String): Result<String> = runCatching {
        val body = JSONObject().put("message", message).toString()
        val response = FynxBackendClient.postJson(context, "/api/assistant", body).getOrThrow()
        JSONObject(response).optString("reply").ifBlank {
            throw IllegalStateException("Assistant returned an empty response")
        }
    }
}
