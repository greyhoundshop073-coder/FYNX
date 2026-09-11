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
    private const val LEGACY_PRODUCTION_BASE_URL = "https://ai-creative-studio-572v.onrender.com"
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
    fun baseUrl(context: Context): String { val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); val stored = prefs.getString(KEY_BASE_URL, null)?.trim()?.trimEnd(); if (stored.equals(LEGACY_PRODUCTION_BASE_URL, ignoreCase = true)) { prefs.edit().putString(KEY_BASE_URL, PRODUCTION_BASE_URL).apply(); return PRODUCTION_BASE_URL }; return stored?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: PRODUCTION_BASE_URL }
    fun configureBaseUrl(context: Context, value: String) { val normalized = value.trim().trimEnd('/'); require(normalized.isBlank() || normalized.startsWith("https://")) { "FYNX backend must use HTTPS." }; context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_BASE_URL, normalized).apply() }
    fun saveAccessToken(context: Context, token: String?) { if (token.isNullOrBlank()) { FynxAuthStore.clear(context); return }; FynxSecureTokenStore.save(context, token) }
    fun accessToken(context: Context): String? = FynxSecureTokenStore.load(context) ?: migrateLegacyAccessToken(context)
    private fun migrateLegacyAccessToken(context: Context): String? { val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); val legacy = prefs.getString(LEGACY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null; return runCatching { FynxSecureTokenStore.save(context, legacy); prefs.edit().remove(LEGACY_ACCESS_TOKEN).apply(); legacy }.getOrNull() }
    fun hasAccessToken(context: Context): Boolean = accessToken(context) != null
    fun isNetworkAvailable(context: Context): Boolean = hasNetwork(context)
    fun isUnauthorizedFailure(error: Throwable): Boolean = generateSequence(error) { it.cause }.any { it is FynxUnauthorizedException }
    suspend fun health(context: Context): Result<String> = get(context, "/health")
    suspend fun get(context: Context, path: String): Result<String> = request(context, "GET", path, null)
    suspend fun postJson(context: Context, path: String, body: String): Result<String> = request(context, "POST", path, body)
    suspend fun patchJson(context: Context, path: String, body: String): Result<String> = request(context, "PATCH", path, body)
    suspend fun delete(context: Context, path: String): Result<String> = request(context, "DELETE", path, null)
    suspend fun currentUserId(context: Context): Result<String> = get(context, "/api/me").mapCatching { raw -> JSONObject(raw).getJSONObject("user").getString("id") }

    suspend fun downloadToFile(context: Context, mediaUrl: String, destination: File, maxBytes: Long = 12L * 1024L * 1024L): Result<DownloadedMedia> {
        return withContext(Dispatchers.IO) {
            try {
                val root = baseUrl(context).trimEnd('/'); require(root.startsWith("https://")) { "FYNX backend must use HTTPS." }; val candidate = mediaUrl.trim(); require(candidate.isNotBlank()) { "Media URL is empty." }
                val absoluteUrl = if (candidate.startsWith("http://") || candidate.startsWith("https://")) candidate else { require(candidate.startsWith("/")) { "Media path must start with /." }; root + candidate }
                val target = URL(absoluteUrl); val configured = URL(root); require(target.protocol.equals("https", true)) { "FYNX media must use HTTPS." }; require(target.host.equals(configured.host, true)) { "FYNX media host is not trusted." }
                awaitValidatedNetwork(context); val parent = destination.parentFile ?: throw IOException("Media destination has no parent directory"); if (!parent.exists() && !parent.mkdirs() && !parent.isDirectory) throw IOException("Unable to create media destination directory")
                val temporary = File(parent, ".${destination.name}.part"); var attempt = 0; var completed: Result<DownloadedMedia>? = null
                while (completed == null) {
                    try {
                        val result: DownloadedMedia = requestSemaphore.withPermit { if (FynxNetworkQuality.current(context) == FynxNetworkQuality.Level.WEAK) weakRequestSemaphore.withPermit { downloadOnce(context, target, temporary, maxBytes) } else downloadOnce(context, target, temporary, maxBytes) }
                        if (destination.exists() && !destination.delete()) throw IOException("Unable to replace downloaded media"); if (!temporary.renameTo(destination)) throw IOException("Unable to finalize downloaded media"); completed = Result.success(result)
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        temporary.delete(); if (!isRetryableFailure(error) || attempt >= MAX_IDEMPOTENT_RETRIES) completed = Result.failure(error) else { attempt++; awaitValidatedNetwork(context); delay(RETRY_DELAY_MS * attempt) }
                    }
                }
                checkNotNull(completed)
            } catch (error: Exception) { if (error is CancellationException) throw error; Result.failure(error) }
        }
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
    private suspend fun executeRequest(context: Context, root: String, method: String, path: String, body: String?): String { val weakNetwork = FynxNetworkQuality.current(context) == FynxNetworkQuality.Level.WEAK; val connection = (URL(root + path).openConnection() as HttpURLConnection).apply { requestMethod = method; connectTimeout = if (weakNetwork) WEAK_CONNECT_TIMEOUT_MS else CONNECT_TIMEOUT_MS; readTimeout = if (weakNetwork) WEAK_READ_TIMEOUT_MS else READ_TIMEOUT_MS; useCaches = false; setRequestProperty("Accept", "application/json"); setRequestProperty("Accept-Encoding", "identity"); setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0"); setRequestProperty("Pragma", "no-cache"); setRequestProperty("Connection", "close"); setRequestProperty("User-Agent", "FYNX-Android/1"); accessToken(context)?.let { setRequestProperty("Authorization", "Bearer $it") } }; val cancellationHandle = currentCoroutineContext().job.invokeOnCompletion { connection.disconnect() }; try { if (body != null) { val payload = body.toByteArray(Charsets.UTF_8); connection.doOutput = true; connection.setFixedLengthStreamingMode(payload.size); connection.setRequestProperty("Content-Type", "application/json; charset=utf-8"); connection.outputStream.use { it.write(payload) } }; val status = connection.responseCode; val stream = if (status in 200..299) connection.inputStream else connection.errorStream; val response = stream?.use { input -> val output = StringBuilder(); val buffer = ByteArray(16 * 1024); var total = 0; while (true) { val count = input.read(buffer); if (count < 0) break; total += count; if (total > MAX_RESPONSE_BYTES) throw IOException("FYNX backend response is too large"); output.append(String(buffer, 0, count, Charsets.UTF_8)) }; output.toString() }.orEmpty(); if (status == HttpURLConnection.HTTP_UNAUTHORIZED) { FynxAuthStore.clear(context); throw FynxUnauthorizedException() }; if (status !in 200..299) throw FynxHttpException(status, response); return response } finally { cancellationHandle.dispose(); connection.disconnect() } }
    private fun hasNetwork(context: Context): Boolean { val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true; return manager.allNetworks.any { network -> val capabilities = manager.getNetworkCapabilities(network) ?: return@any false; capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } }
    private fun isRetryableFailure(error: Throwable): Boolean { var current: Throwable? = error; while (current != null) { if (current is SocketTimeoutException || current is ConnectException || current is UnknownHostException || current is IOException) return true; if (current is FynxHttpException && current.status in setOf(408, 425, 429, 500, 502, 503, 504)) return true; current = current.cause }; return false }
    private class FynxNetworkUnavailableException : IOException("FYNX network connection is unavailable")
    private class FynxUnauthorizedException : IOException("FYNX session expired")
    private class FynxHttpException(val status: Int, body: String) : IOException("FYNX backend returned HTTP $status${body.takeIf { it.isNotBlank() }?.let { ": ${it.take(600)}" } ?: ""}")
}