package com.fynx.app.ui

import android.content.Context
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Secure network boundary for the real FYNX backend. */
object FynxBackendClient {
    private const val PREFS = "fynx_backend"
    private const val KEY_BASE_URL = "base_url"
        private const val PRODUCTION_BASE_URL = "https://fynx-ai-backend.onrender.com"
    private const val MAX_IDEMPOTENT_RETRIES = 2
    private const val RETRY_DELAY_MS = 500L

    fun availability(context: Context): FynxBackendAvailability =
        if (baseUrl(context).isBlank()) FynxBackendAvailability.DISABLED else FynxBackendAvailability.CONFIGURED

    fun baseUrl(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_BASE_URL, PRODUCTION_BASE_URL)?.trim()?.trimEnd('/') ?: PRODUCTION_BASE_URL

    fun configureBaseUrl(context: Context, value: String) {
        val normalized = value.trim().trimEnd('/')
        require(normalized.isBlank() || normalized.startsWith("https://")) { "FYNX backend must use HTTPS." }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_BASE_URL, normalized).apply()
    }

    fun saveAccessToken(context: Context, token: String?) = FynxSecureTokenStore.save(context, token)

    fun accessToken(context: Context): String? = FynxSecureTokenStore.load(context)

    fun hasAccessToken(context: Context): Boolean = accessToken(context) != null

    fun isUnauthorizedFailure(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.any { it is FynxUnauthorizedException }

    suspend fun get(context: Context, path: String): Result<String> = request(context, "GET", path, null)
    suspend fun postJson(context: Context, path: String, body: String): Result<String> = request(context, "POST", path, body)
    suspend fun patchJson(context: Context, path: String, body: String): Result<String> = request(context, "PATCH", path, body)
    suspend fun delete(context: Context, path: String): Result<String> = request(context, "DELETE", path, null)

    suspend fun currentUserId(context: Context): Result<String> =
        get(context, "/api/me").mapCatching { raw -> JSONObject(raw).getJSONObject("user").getString("id") }

    private suspend fun request(context: Context, method: String, path: String, body: String?): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = baseUrl(context)
                require(root.isNotBlank()) { "FYNX backend is not configured." }
                require(path.startsWith("/")) { "Backend path must start with /." }
                var attempt = 0
                var response: String? = null
                while (response == null) {
                    try { response = executeRequest(context, root, method, path, body) }
                    catch (error: Exception) {
                        val retryable = method == "GET" || method == "DELETE"
                        if (!retryable || !isTransientNetworkFailure(error) || attempt >= MAX_IDEMPOTENT_RETRIES) throw error
                        attempt++; delay(RETRY_DELAY_MS * attempt)
                    }
                }
                response
            }
        }

    private fun executeRequest(context: Context, root: String, method: String, path: String, body: String?): String {
        val connection = (URL(root + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 10_000; readTimeout = 20_000; useCaches = false
            setRequestProperty("Accept", "application/json")
            accessToken(context)?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED) { saveAccessToken(context, null); throw FynxUnauthorizedException() }
            if (status !in 200..299) throw IllegalStateException("FYNX backend returned HTTP $status")
            return response
        } finally { connection.disconnect() }
    }

    private fun isTransientNetworkFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is SocketTimeoutException || current is ConnectException || current is UnknownHostException || current is IOException) return true
            current = current.cause
        }
        return false
    }

    private class FynxUnauthorizedException : IOException("FYNX session expired")
}
