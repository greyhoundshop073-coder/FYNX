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

    suspend fun improvePostCaption(context: Context, caption: String): Result<String> {
        val clean = caption.trim().take(4000)
        if (clean.isBlank()) return Result.failure(IllegalArgumentException("Caption is empty"))
        return sendMessage(
            context,
            "Improve this social-media post caption. Keep the user's original meaning and facts, make it natural, clear and engaging, and do not add invented personal details. Return only the finished caption.\n\nCaption:\n$clean"
        )
    }
}
