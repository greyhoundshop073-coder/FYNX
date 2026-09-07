package com.fynx.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val FYNX_MEDIA_CACHE_DIR = "fynx_media_cache_v2"
private const val MAX_FYNX_MEDIA_CACHE_BYTES = 100L * 1024L * 1024L
private const val MAX_FYNX_MEDIA_FILE_BYTES = 12 * 1024 * 1024
private const val MAX_IMAGE_DIMENSION = 1600
private const val MAX_DOWNLOAD_ATTEMPTS = 3

internal object FynxMediaCache {
    private val downloadLocks = mutableMapOf<String, Any>()

    fun getOrDownload(context: Context, path: String, type: String?): File? {
        if (path.isBlank()) return null
        val normalizedPath = path.trim()
        if (!normalizedPath.startsWith("/api/media/")) return null
        val directory = File(context.cacheDir, FYNX_MEDIA_CACHE_DIR).apply { mkdirs() }
        val extension = when (type) { "video" -> ".mp4"; "audio" -> ".m4a"; else -> ".jpg" }
        val file = File(directory, "${key(normalizedPath, type)}$extension")
        if (file.isFile && file.length() in 1..MAX_FYNX_MEDIA_FILE_BYTES) {
            file.setLastModified(System.currentTimeMillis())
            return file
        }
        if (FynxNetworkQuality.current(context) == FynxNetworkQuality.Level.OFFLINE) return null
        val lock = synchronized(downloadLocks) { downloadLocks.getOrPut(file.absolutePath) { Any() } }
        return synchronized(lock) {
            if (file.isFile && file.length() in 1..MAX_FYNX_MEDIA_FILE_BYTES) {
                file.setLastModified(System.currentTimeMillis())
                return@synchronized file
            }
            if (FynxNetworkQuality.current(context) == FynxNetworkQuality.Level.OFFLINE) return@synchronized null
            file.delete()
            download(context, normalizedPath, file)?.also { trim(directory, it) }
        }.also {
            synchronized(downloadLocks) { downloadLocks.remove(file.absolutePath) }
        }
    }

    private fun download(context: Context, path: String, destination: File): File? {
        val networkLevel = FynxNetworkQuality.current(context)
        val attempts = when (networkLevel) {
            FynxNetworkQuality.Level.WEAK -> 1
            FynxNetworkQuality.Level.GOOD -> MAX_DOWNLOAD_ATTEMPTS
            FynxNetworkQuality.Level.OFFLINE -> 0
        }
        repeat(attempts) { attempt ->
            val result = downloadOnce(context, path, destination, networkLevel)
            if (result != null) return result
            if (attempt + 1 < attempts) Thread.sleep(300L * (1L shl attempt))
        }
        return null
    }

    private fun downloadOnce(context: Context, path: String, destination: File, networkLevel: FynxNetworkQuality.Level): File? = runCatching {
        val baseUrl = FynxBackendClient.baseUrl(context).trimEnd('/')
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = if (networkLevel == FynxNetworkQuality.Level.WEAK) 10000 else 8000
            readTimeout = if (networkLevel == FynxNetworkQuality.Level.WEAK) 20000 else 15000
            instanceFollowRedirects = false
            setRequestProperty("Authorization", "Bearer ${FynxBackendClient.accessToken(context) ?: ""}")
            setRequestProperty("Accept", "image/*,video/*,audio/*")
            setRequestProperty("Connection", "keep-alive")
        }
        val temporary = File(destination.parentFile, ".${destination.name}.part")
        try {
            val status = connection.responseCode
            if (status !in 200..299) return null
            temporary.delete()
            var total = 0L
            val declaredLength = connection.contentLengthLong
            if (declaredLength > MAX_FYNX_MEDIA_FILE_BYTES) return null
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
                    output.fd.sync()
                }
            }
            if (total <= 0L || temporary.length() != total) {
                temporary.delete()
                return null
            }
            optimizeImageIfNeeded(temporary, destination)
            if (!destination.exists()) {
                if (!temporary.renameTo(destination)) { temporary.delete(); return null }
            } else temporary.delete()
            if (destination.length() !in 1..MAX_FYNX_MEDIA_FILE_BYTES) {
                destination.delete()
                return null
            }
            destination
        } finally {
            temporary.delete()
            connection.disconnect()
        }
    }.getOrNull()

    private fun optimizeImageIfNeeded(source: File, destination: File) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, options)
        val width = options.outWidth
        val height = options.outHeight
        if (width <= 0 || height <= 0 || (width <= MAX_IMAGE_DIMENSION && height <= MAX_IMAGE_DIMENSION)) return
        var sample = 1
        while (width / sample > MAX_IMAGE_DIMENSION || height / sample > MAX_IMAGE_DIMENSION) sample *= 2
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }
        val bitmap = BitmapFactory.decodeFile(source.absolutePath, decodeOptions) ?: return
        try {
            FileOutputStream(destination).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) destination.delete()
                else output.fd.sync()
            }
        } finally { bitmap.recycle() }
        if (destination.exists()) source.delete()
    }

    private fun trim(directory: File, newest: File) {
        val files = directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_FYNX_MEDIA_CACHE_BYTES) return
        files.sortedBy { if (it == newest) Long.MAX_VALUE else it.lastModified() }.forEach { file ->
            if (total <= MAX_FYNX_MEDIA_CACHE_BYTES) return@forEach
            if (file != newest) {
                val size = file.length()
                if (file.delete()) total -= size
            }
        }
    }

    private fun key(path: String, type: String?): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("${type ?: "unknown"}:$path".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}