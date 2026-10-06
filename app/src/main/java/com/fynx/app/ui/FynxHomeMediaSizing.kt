package com.fynx.app.ui

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.File

/** Shared Home media sizing rules. Keeps unusual source ratios instead of forcing social-media crops. */
internal object FynxHomeMediaSizing {
    fun imageAspect(file: File): Float = runCatching {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth.toFloat() / options.outHeight.toFloat()
        } else 1f
    }.getOrDefault(1f).coerceIn(0.05f, 20f)

    fun videoAspect(file: File): Float = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 1f
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 1f
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) height / width else width / height
        } finally {
            retriever.release()
        }
    }.getOrDefault(1f).coerceIn(0.05f, 20f)

    fun aspect(file: File, type: String): Float =
        if (type.equals("video", ignoreCase = true)) videoAspect(file) else imageAspect(file)

    fun sampleSize(width: Int, height: Int, maxDimension: Int = 2048): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (width / sample > maxDimension || height / sample > maxDimension) {
            sample *= 2
        }
        return sample
    }
}
