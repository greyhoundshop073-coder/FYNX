package com.fynx.app.ui

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

object AiAssistantClient {
    // Render's public HTTPS URL. No AI provider secret is stored in the Android app.
    private const val BASE_URL = "https://ai-creative-studio-572v.onrender.com"

    fun sendMessage(message: String): Result<String> = runCatching {
        val connection = (URL("$BASE_URL/api/assistant").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        connection.outputStream.use { output ->
            output.write(JSONObject().put("message", message).toString().toByteArray(Charsets.UTF_8))
        }

        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()

        if (responseCode !in 200..299) {
            throw IllegalStateException("Assistant request failed ($responseCode)")
        }

        JSONObject(body).optString("reply").ifBlank {
            throw IllegalStateException("Assistant returned an empty response")
        }
    }
}
