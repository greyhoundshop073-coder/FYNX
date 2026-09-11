package com.fynx.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val FYNX_MEDIA_CACHE_DIR = "fynx_media_cache_v2"
private const val MAX_FYNX_MEDIA_CACHE_BYTES = 100L * 1024L * 1024L
private const val MAX_FYNX_MEDIA_FILE_BYTES = 12 * 1024 * 1024
private const val MAX_IMAGE_DIMENSION = 1600

internal object FynxMediaCache {
    private val downloadLocks = mutableMapOf<String, Mutex>()

    suspend fun getOrDownload(context: Context, path: String, type: String?): File? {
        if (path.isBlank()) return null
        val normalizedPath = path.trim()
        if (!normalizedPath.startsWith("/api/media/")) return null
        val accountKey = FynxAuthStore.accountStorageKey(context) ?: return null
        if (!FynxBackendClient.hasAccessToken(context)) return null

        val directory = File(context.cacheDir, "$FYNX_MEDIA_CACHE_DIR/${accountCacheKey(accountKey)}").apply { mkdirs() }
        val extension = when (type) { "video" -> ".mp4"; "audio" -> ".m4a"; else -> ".jpg" }
        val file = File(directory, "${key(normalizedPath, type)}$extension")
        if (file.isFile && file.length() in 1..MAX_FYNX_MEDIA_FILE_BYTES) {
            file.setLastModified(System.currentTimeMillis())
            return file
        }
        if (FynxNetworkQuality.current(context) == FynxNetworkQuality.Level.OFFLINE) return null

        val lock = synchronized(downloadLocks) { downloadLocks.getOrPut(file.absolutePath) { Mutex() } }
        return try {
            lock.withLock {
                if (file.isFile && file.length() in 1..MAX_FYNX_MEDIA_FILE_BYTES) {
                    file.setLastModified(System.currentTimeMillis())
                    return@withLock file
                }
                if (FynxNetworkQuality.current(context) == FynxNetworkQuality.Level.OFFLINE) return@withLock null
                download(context, normalizedPath, file)?.also { trim(directory, it) }
            }
        } finally {
            synchronized(downloadLocks) {
                if (!lock.isLocked) downloadLocks.remove(file.absolutePath)
            }
        }
    }

    private suspend fun download(context: Context, path: String, destination: File): File? {
        val rawFile = File(destination.parentFile, ".${destination.name}.raw")
        rawFile.delete()
        val result = FynxBackendClient.downloadToFile(
            context = context,
            mediaUrl = path,
            destination = rawFile,
            maxBytes = MAX_FYNX_MEDIA_FILE_BYTES.toLong()
        )
        if (result.isFailure) {
            rawFile.delete()
            return null
        }

        return runCatching {
            optimizeImageIfNeeded(rawFile, destination)
            if (!destination.exists()) {
                if (!rawFile.renameTo(destination)) throw IllegalStateException("Unable to finalize media cache")
            } else {
                rawFile.delete()
            }
            if (destination.length() !in 1..MAX_FYNX_MEDIA_FILE_BYTES) {
                destination.delete()
                null
            } else destination
        }.getOrElse {
            rawFile.delete()
            destination.delete()
            null
        }
    }

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
        val files = directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".raw") } ?: return
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

    private fun key(path: String, type: String?): String = sha256("${type ?: "unknown"}:$path")

    private fun accountCacheKey(account: String): String = sha256("account:$account")

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
