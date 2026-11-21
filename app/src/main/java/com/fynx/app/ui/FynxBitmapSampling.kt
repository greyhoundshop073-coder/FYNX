package com.fynx.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shared bounded bitmap decoding for feed, profile and media preview surfaces.
 * Images are inspected first; a full-size pixel buffer is not allocated when
 * the source dimensions exceed the configured preview bound.
 */
internal object FynxBitmapSampling {
    const val DEFAULT_MAX_DIMENSION = 1600
    const val FEED_MAX_DIMENSION = 2048
    const val THUMBNAIL_MAX_DIMENSION = 512

    fun sampledDimension(original: Int, sampleSize: Int): Int {
        if (original <= 0) return 0
        val safeSample = sampleSize.coerceAtLeast(1)
        return ((original.toLong() + safeSample.toLong() - 1L) / safeSample.toLong())
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    }

    fun computeInSampleSize(outWidth: Int, outHeight: Int, maxDimension: Int = DEFAULT_MAX_DIMENSION): Int {
        if (outWidth <= 0 || outHeight <= 0 || maxDimension <= 0) return 1
        var sample = 1
        while (sampledDimension(outWidth, sample) > maxDimension || sampledDimension(outHeight, sample) > maxDimension) {
            if (sample > Int.MAX_VALUE / 2) break
            sample *= 2
        }
        return sample
    }

    fun fitWithinBounds(width: Int, height: Int, maxDimension: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0 || maxDimension <= 0) return 1 to 1
        val largest = max(width, height)
        if (largest <= maxDimension) return width to height
        val scale = maxDimension.toFloat() / largest.toFloat()
        return (width * scale).roundToInt().coerceIn(1, maxDimension) to
            (height * scale).roundToInt().coerceIn(1, maxDimension)
    }

    fun decodeFileSampled(
        file: File,
        maxDimension: Int = FEED_MAX_DIMENSION,
        preferredConfig: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap? {
        if (!file.isFile || file.length() <= 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = preferredConfig
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    fun decodeUriSampled(
        context: Context,
        uri: Uri,
        maxDimension: Int = DEFAULT_MAX_DIMENSION,
        preferredConfig: Bitmap.Config = Bitmap.Config.ARGB_8888
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openUriStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = preferredConfig
        }
        return openUriStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun openUriStream(context: Context, uri: Uri): InputStream? =
        if (uri.scheme.equals("file", ignoreCase = true)) {
            uri.path?.let(::File)?.takeIf { it.isFile }?.inputStream()
        } else {
            context.contentResolver.openInputStream(uri)
        }
}
