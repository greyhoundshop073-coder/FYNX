package com.fynx.app.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import org.json.JSONObject

object FynxBackendClient {
    private const val PREFS = "fynx_backend"
    private const val KEY_BASE_URL = "base_url"
    private const val LEGACY_ACCESS_TOKEN = "access_token"
    private const val PRODUCTION_BASE_URL = "https://fynx-ai-backend.onrender.com"
    private const val MAX_IDEMPOTENT_RETRIES = 2
    private const val RETRY_DELAY_MS = 750L
    private const val NETWORK_VALIDATION_WAIT_MS = 6_000L
    private const val NETWORK_VALIDATION_POLL_MS = 500L
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val WEAK_CONNECT_TIMEOUT_MS = 20_000
    private const val WEAK_READ_TIMEOUT_MS = 45_000
    private const val MAX_RESPONSE_BYTES = 4 * 1024 * 1024
    private const val MAX_CONCURRENT_REQUESTS = 6
    private const val MAX_WEAK_CONCURRENT_REQUESTS = 2

    private val requestSemaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
    private val weakRequestSemaphore = Semaphore(MAX_WEAK_CONCURRENT_REQUESTS)
    data class DownloadedMedia(val contentType: String?, val byteCount: Long)

    fun availability(context: Context): FynxBackendAvailability = if (baseUrl(context).isBlank()) FynxBackendAvailability.DISABLED else FynxBackendAvailability.CONFIGURED
    fun baseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_BASE_URL, null)?.trim()?.trimEnd()
        return stored?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: PRODUCTION_BASE_URL
    }

    private suspend fun downloadOnce(context: Context, target: URL, temporary: File, maxBytes: Long): DownloadedMedia {
        val weakNetwork = FynxNetworkQuality.current(context) == FynxNetworkQuality.Level.WEAK
        val connection = (target.openConnection() as HttpURLConnection).apply { connectTimeout = if (weakNetwork) WEAK_CONNECT_TIMEOUT_MS else CONNECT_TIMEOUT_MS; readTimeout = if (weakNetwork) WEAK_READ_TIMEOUT_MS else READ_TIMEOUT_MS; useCaches = false; instanceFollowRedirects = false; setRequestProperty("Accept", "image/*,video/*,audio/*,*/*"); setRequestProperty("Accept-Encoding", "identity"); setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0"); setRequestProperty("Pragma", "no-cache"); setRequestProperty("Connection", "close"); setRequestProperty("User-Agent", "FYNX-Android/1"); accessToken(context)?.let { setRequestProperty("Authorization", "Bearer $it") } }
        val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion { connection.disconnect() }
        try { val status = connection.responseCode; if (status == HttpURLConnection.HTTP_UNAUTHORIZED) { FynxAuthStore.clear(context); throw FynxUnauthorizedException() }; val stream = if (status in 200..299) connection.inputStream else connection.errorStream; if (status !in 200..299) throw FynxHttpException(status, stream?.use { it.bufferedReader().readText().take(600) }.orEmpty()); val declaredLength = connection.contentLengthLong; require(declaredLength < 0L || declaredLength <= maxBytes) { "FYNX media is too large" }; temporary.delete(); var total = 0L; stream?.use { input -> temporary.outputStream().use { output -> val buffer = ByteArray(32 * 1024); while (true) { val count = input.read(buffer); if (count < 0) break; total += count; if (total > maxBytes) throw IOException("FYNX media is too large"); output.write(buffer, 0, count) }; output.flush() } } ?: throw IOException("FYNX media response was empty"); if (total <= 0L || temporary.length() != total) throw IOException("FYNX media download was incomplete"); return DownloadedMedia(connection.contentType?.substringBefore(';')?.trim()?.lowercase(), total) } finally { cancellationHandle.dispose(); connection.disconnect() }
    }

    private suspend fun request(context: Context, method: String, path: String, body: String?): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val root = baseUrl(context); require(root.isNotBlank()) { "FYNX backend is not configured." }; require(root.startsWith("https://")) { "FYNX backend must use HTTPS." }; require(path.startsWith("/")) { "Backend path must start with /." }; awaitValidatedNetwork(context)
                var attempt = 0; var completed: Result<String>? = null
                while (completed == null) {
                    try {
                        val response: String = requestSemaphore.withPermit { if (FynxNetworkQuality.current(context) == FynxNetworkQuality.Level.WEAK) weakRequestSemaphore.withPermit { executeRequest(context, root, method, path, body) } else executeRequest(context, root, method, path, body) }
                        completed = Result.success(response)
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        val retryable = method == "GET" || method == "DELETE"
                        if (!retryable || !isRetryableFailure(error) || attempt >= MAX_IDEMPOTENT_RETRIES) completed = Result.failure(error) else { attempt++; delay(RETRY_DELAY_MS * attempt); awaitValidatedNetwork(context) }
                    }
                }
                checkNotNull(completed)
            } catch (error: Exception) { if (error is CancellationException) throw error; Result.failure(error) }
        }
    }

    private suspend fun awaitValidatedNetwork(context: Context) { if (hasNetwork(context)) return; var waited = 0L; while (waited < NETWORK_VALIDATION_WAIT_MS) { delay(NETWORK_VALIDATION_POLL_MS); waited += NETWORK_VALIDATION_POLL_MS; if (hasNetwork(context)) return }; throw FynxNetworkUnavailableException() }
    private fun isPublicAuthPath(path: String): Boolean = path == "/api/auth/login" || path == "/api/auth/register"
    private suspend fun executeRequest(context: Context, root: String, method: String, path: String, body: String?): String {
        val weakNetwork = FynxNetworkQuality.current(context) == FynxNetworkQuality.Level.WEAK
        val requiresAuthentication = !isPublicAuthPath(path)
        val connection = (URL(root + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = if (weakNetwork) WEAK_CONNECT_TIMEOUT_MS else CONNECT_TIMEOUT_MS
            readTimeout = if (weakNetwork) WEAK_READ_TIMEOUT_MS else READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
            setRequestProperty("Pragma", "no-cache")
            setRequestProperty("Connection", "close")
            setRequestProperty("User-Agent", "FYNX-Android/1")
            if (requiresAuthentication) accessToken(context)?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion { connection.disconnect() }
        try {
            if (body != null) { val payload = body.toByteArray(Charsets.UTF_8); connection.doOutput = true; connection.setFixedLengthStreamingMode(payload.size); connection.setRequestProperty("Content-Type", "application/json; charset=utf-8"); connection.outputStream.use { it.write(payload) } }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use { input -> val output = StringBuilder(); val buffer = ByteArray(16 * 1024); var total = 0; while (true) { val count = input.read(buffer); if (count < 0) break; total += count; if (total > MAX_RESPONSE_BYTES) throw IOException("FYNX backend response is too large"); output.append(String(buffer, 0, count, Charsets.UTF_8)) }; output.toString() }.orEmpty()
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                if (requiresAuthentication) FynxAuthStore.clear(context)
                throw FynxUnauthorizedException()
            }
            if (status !in 200..299) throw FynxHttpException(status, response)
            return response
        } finally { cancellationHandle.dispose(); connection.disconnect() }
    }

    /** INTERNET means a usable transport may exist while Android is still validating it. Let the request itself prove reachability. */
    private fun hasNetwork(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val networks = manager.allNetworks
        if (networks.isEmpty()) return false
        return networks.any { network ->
            val capabilities = manager.getNetworkCapabilities(network) ?: return@any false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    private fun isRetryableFailure(error: Throwable): Boolean { var current: Throwable? = error; while (current != null) { if (current is SocketTimeoutException || current is ConnectException || current is UnknownHostException || current is IOException) return true; if (current is FynxHttpException && current.status in setOf(408, 425, 429, 500, 502, 503, 504)) return true; current = current.cause }; return false }
    private fun migrateLegacyAccessToken(context: Context): String? { val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); val legacy = prefs.getString(LEGACY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null; return runCatching { FynxSecureTokenStore.save(context, legacy); prefs.edit().remove(LEGACY_ACCESS_TOKEN).apply(); legacy }.getOrNull() }
    private class FynxNetworkUnavailableException : IOException("FYNX network connection is unavailable")
    private class FynxUnauthorizedException : IOException("FYNX session expired")
    private class FynxHttpException(val status: Int, body: String) : IOException("FYNX backend returned HTTP $status${body.takeIf { it.isNotBlank() }?.let { ": ${it.take(600)}" } ?: ""}")
}