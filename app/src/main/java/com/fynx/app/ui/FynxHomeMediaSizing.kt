package com.fynx.app.ui

import android.graphics.BitmapFactory
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

    fun sampleSize(width: Int, height: Int, maxDimension: Int = 2048): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (width / sample > maxDimension || height / sample > maxDimension) {
            sample *= 2
        }
        return sample
    }
}
