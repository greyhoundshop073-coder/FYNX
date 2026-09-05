package com.fynx.app.ui

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val FYNX_MEDIA_CACHE_DIR = "fynx_media_cache_v1"
private const val MAX_FYNX_MEDIA_CACHE_BYTES = 100L * 1024L * 1024L
private const val MAX_FYNX_MEDIA_FILE_BYTES = 12 * 1024 * 1024

internal object FynxMediaCache {
    fun getOrDownload(context: Context, path: String, type: String?): File? {
        if (path.isBlank()) return null
        val directory = File(context.cacheDir, FYNX_MEDIA_CACHE_DIR).apply { mkdirs() }
        val extension = when (type) { "video" -> ".mp4"; "audio" -> ".m4a"; else -> ".jpg" }
        val file = File(directory, "${key(path, type)}$extension")
        if (file.isFile && file.length() in 1..MAX_FYNX_MEDIA_FILE_BYTES) {
            file.setLastModified(System.currentTimeMillis())
            return file
        }
        file.delete()
        return download(context, path, file)?.also { trim(directory, it) }
    }

    private fun download(context: Context, path: String, destination: File): File? = runCatching {
        val baseUrl = FynxBackendClient.baseUrl(context)
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 15000
            setRequestProperty("Authorization", "Bearer ${FynxBackendClient.accessToken(context) ?: ""}")
            setRequestProperty("Connection", "keep-alive")
        }
        try {
            if (connection.responseCode !in 200..299) return null
            val temporary = File(destination.parentFile, ".${destination.name}.part")
            temporary.delete()
            var total = 0
            val buffer = ByteArray(32 * 1024)
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_FYNX_MEDIA_FILE_BYTES) {
                            temporary.delete()
                            return null
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (total <= 0) { temporary.delete(); return null }
            if (!temporary.renameTo(destination)) { temporary.delete(); return null }
            destination
        } finally { connection.disconnect() }
    }.getOrNull()

    private fun trim(directory: File, newest: File) {
        val files = directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_FYNX_MEDIA_CACHE_BYTES) return
        files.sortedBy { if (it == newest) Long.MAX_VALUE else it.lastModified() }.forEach { file ->
            if (total <= MAX_FYNX_MEDIA_CACHE_BYTES) return@forEach
            if (file != newest && file.delete()) total -= file.length()
        }
    }

    private fun key(path: String, type: String?): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("${type ?: "unknown"}:$path".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
