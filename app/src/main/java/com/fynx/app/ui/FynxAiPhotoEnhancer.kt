package com.fynx.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/** Local, private smart photo enhancement used by the FYNX AI Creation layer. */
object FynxAiPhotoEnhancer {
    fun enhance(context: Context, source: Uri): Result<Uri> = runCatching {
        val decoded = if (source.scheme == "file") {
            File(source.path ?: error("Photo path missing")).inputStream().use(BitmapFactory::decodeStream)
        } else {
            context.contentResolver.openInputStream(source)?.use(BitmapFactory::decodeStream)
        } ?: error("Photo could not be opened")
        val bitmap = decoded.copy(Bitmap.Config.ARGB_8888, false)
        decoded.recycle()

        var luminanceTotal = 0L
        var samples = 0L
        val stepX = (bitmap.width / 32).coerceAtLeast(1)
        val stepY = (bitmap.height / 32).coerceAtLeast(1)
        for (y in 0 until bitmap.height step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                val pixel = bitmap.getPixel(x, y)
                luminanceTotal += (0.299 * android.graphics.Color.red(pixel) + 0.587 * android.graphics.Color.green(pixel) + 0.114 * android.graphics.Color.blue(pixel)).toLong()
                samples++
            }
        }
        val average = if (samples == 0L) 128f else luminanceTotal.toFloat() / samples
        val brightness = when {
            average < 70f -> 0.10f
            average < 105f -> 0.06f
            average > 205f -> -0.05f
            else -> 0.02f
        }
        val contrast = if (average < 75f || average > 190f) 1.06f else 1.03f

        val matrix = ColorMatrix().apply {
            setSaturation(1.08f)
            postConcat(ColorMatrix(floatArrayOf(
                contrast, 0f, 0f, 0f, brightness * 255f,
                0f, contrast, 0f, 0f, brightness * 255f,
                0f, 0f, contrast, 0f, brightness * 255f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(bitmap, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
        bitmap.recycle()

        val file = File(context.cacheDir, "fynx_smart_enhanced_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { stream ->
            check(output.compress(Bitmap.CompressFormat.JPEG, 94, stream)) { "Enhanced photo could not be saved" }
        }
        output.recycle()
        Uri.fromFile(file)
    }
}
