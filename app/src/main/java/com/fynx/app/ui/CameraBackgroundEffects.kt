package com.fynx.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * Photo backgrounds rendered locally on the device. Subject segmentation and compositing stay
 * on-device; these preset scenes are deterministic backgrounds, not generated AI photographs.
 */
enum class CameraBackgroundEffect(val label: String) {
    OFF("Off"),
    PORTRAIT_BLUR("Portrait Blur"),
    BLUE_STUDIO("Blue"),
    SUNSET_COLOUR("Sunset"),
    MINT_COLOUR("Mint"),
    STUDIO_SCENE("Studio Scene"),
    BEACH_SCENE("Beach Scene")
}

/**
 * Replaces or blurs the background using ML Kit's on-device selfie segmentation.
 * The working image is bounded to 1440px on its longest edge to keep memory use manageable on
 * entry-level devices. The original file is replaced only after segmentation/composition succeeds.
 */
internal fun applyCameraBackgroundEffectToPhotoFile(file: File, effect: CameraBackgroundEffect) {
    if (effect == CameraBackgroundEffect.OFF) return

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("Unable to read the captured photo")

    val longest = max(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (longest / sample > 1800) sample *= 2
    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        ?: error("Unable to decode the captured photo for background effects")
    val longestDecoded = max(decoded.width, decoded.height)
    val source = if (longestDecoded > 1440) {
        val scale = 1440f / longestDecoded.toFloat()
        Bitmap.createScaledBitmap(
            decoded,
            max(1, (decoded.width * scale).toInt()),
            max(1, (decoded.height * scale).toInt()),
            true
        ).also { if (it !== decoded) decoded.recycle() }
    } else decoded

    val segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
    )
    var backdrop: Bitmap? = null
    try {
        val segmentation = Tasks.await(segmenter.process(InputImage.fromBitmap(source, 0)))
        val maskWidth = segmentation.width
        val maskHeight = segmentation.height
        require(maskWidth > 0 && maskHeight > 0) { "Subject mask was empty" }
        val mask = segmentation.buffer.duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer()
        val sourceWidth = source.width
        val sourceHeight = source.height
        val pixelCount = sourceWidth * sourceHeight
        val sourcePixels = IntArray(pixelCount)
        source.getPixels(sourcePixels, 0, sourceWidth, 0, 0, sourceWidth, sourceHeight)

        backdrop = if (effect == CameraBackgroundEffect.PORTRAIT_BLUR) {
            createBlurredBackdrop(source)
        } else {
            createPresetBackdrop(effect, sourceWidth, sourceHeight)
        }
        val backgroundPixels = IntArray(pixelCount)
        backdrop.getPixels(backgroundPixels, 0, sourceWidth, 0, 0, sourceWidth, sourceHeight)
        val outputPixels = IntArray(pixelCount)
        var confidentForeground = 0

        for (y in 0 until sourceHeight) {
            val maskY = min(maskHeight - 1, ((y.toLong() * maskHeight) / sourceHeight).toInt())
            for (x in 0 until sourceWidth) {
                val maskX = min(maskWidth - 1, ((x.toLong() * maskWidth) / sourceWidth).toInt())
                val confidence = mask.get(maskY * maskWidth + maskX).coerceIn(0f, 1f)
                if (confidence >= 0.5f) confidentForeground++
                val foregroundAlpha = ((confidence - 0.22f) / 0.56f).coerceIn(0f, 1f)
                val inverseAlpha = 1f - foregroundAlpha
                val index = y * sourceWidth + x
                val foreground = sourcePixels[index]
                val background = backgroundPixels[index]
                val red = (Color.red(foreground) * foregroundAlpha + Color.red(background) * inverseAlpha).toInt()
                val green = (Color.green(foreground) * foregroundAlpha + Color.green(background) * inverseAlpha).toInt()
                val blue = (Color.blue(foreground) * foregroundAlpha + Color.blue(background) * inverseAlpha).toInt()
                outputPixels[index] = Color.rgb(red, green, blue)
            }
        }

        val foregroundRatio = confidentForeground.toFloat() / pixelCount.toFloat()
        require(foregroundRatio in 0.015f..0.985f) {
            "The subject could not be separated clearly. Try brighter lighting and a clearer pose."
        }

        val output = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888)
        try {
            output.setPixels(outputPixels, 0, sourceWidth, 0, 0, sourceWidth, sourceHeight)
            FileOutputStream(file).use { stream ->
                check(output.compress(Bitmap.CompressFormat.JPEG, 94, stream)) {
                    "Unable to save the background effect"
                }
            }
        } finally {
            output.recycle()
        }
    } finally {
        backdrop?.recycle()
        source.recycle()
        segmenter.close()
    }
}

private fun createPresetBackdrop(effect: CameraBackgroundEffect, width: Int, height: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val colours = when (effect) {
        CameraBackgroundEffect.BLUE_STUDIO -> intArrayOf(0xFF071C52.toInt(), 0xFF1F72B8.toInt(), 0xFF9EDCF4.toInt())
        CameraBackgroundEffect.SUNSET_COLOUR -> intArrayOf(0xFF42235E.toInt(), 0xFFDA686F.toInt(), 0xFFFFC88B.toInt())
        CameraBackgroundEffect.MINT_COLOUR -> intArrayOf(0xFF123E43.toInt(), 0xFF3C9D8A.toInt(), 0xFFC4F0D1.toInt())
        CameraBackgroundEffect.STUDIO_SCENE -> intArrayOf(0xFF1F2937.toInt(), 0xFF9CA3AF.toInt(), 0xFFF1F5F9.toInt())
        CameraBackgroundEffect.BEACH_SCENE -> intArrayOf(0xFF195F9B.toInt(), 0xFF71C8E8.toInt(), 0xFFFFD99A.toInt())
        CameraBackgroundEffect.OFF, CameraBackgroundEffect.PORTRAIT_BLUR ->
            intArrayOf(Color.DKGRAY, Color.GRAY, Color.LTGRAY)
    }
    paint.shader = LinearGradient(0f, 0f, width * 0.18f, height.toFloat(), colours, null, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.shader = null

    when (effect) {
        CameraBackgroundEffect.STUDIO_SCENE -> {
            paint.color = 0x30FFFFFF
            canvas.drawOval(RectF(width * 0.06f, height * 0.07f, width * 0.94f, height * 0.66f), paint)
            paint.color = 0x267B8798
            canvas.drawRoundRect(RectF(width * 0.05f, height * 0.66f, width * 0.95f, height * 0.98f), width * 0.04f, width * 0.04f, paint)
        }
        CameraBackgroundEffect.BEACH_SCENE -> {
            paint.color = 0xFF319EC1.toInt()
            canvas.drawRect(0f, height * 0.56f, width.toFloat(), height * 0.76f, paint)
            paint.shader = LinearGradient(0f, height * 0.70f, 0f, height.toFloat(), 0xFFFFD99A.toInt(), 0xFFB9784C.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, height * 0.72f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
            paint.color = 0xFFFFF0B0.toInt()
            canvas.drawCircle(width * 0.78f, height * 0.20f, min(width, height) * 0.075f, paint)
        }
        else -> Unit
    }
    return bitmap
}

private fun createBlurredBackdrop(source: Bitmap): Bitmap {
    val smallWidth = max(1, source.width / 4)
    val smallHeight = max(1, source.height / 4)
    val small = Bitmap.createScaledBitmap(source, smallWidth, smallHeight, true)
    try {
        val pixels = IntArray(smallWidth * smallHeight)
        small.getPixels(pixels, 0, smallWidth, 0, 0, smallWidth, smallHeight)
        val horizontal = boxBlur(pixels, smallWidth, smallHeight, 10, horizontal = true)
        val blurredPixels = boxBlur(horizontal, smallWidth, smallHeight, 10, horizontal = false)
        val blurredSmall = Bitmap.createBitmap(smallWidth, smallHeight, Bitmap.Config.ARGB_8888)
        try {
            blurredSmall.setPixels(blurredPixels, 0, smallWidth, 0, 0, smallWidth, smallHeight)
            return Bitmap.createScaledBitmap(blurredSmall, source.width, source.height, true)
        } finally {
            blurredSmall.recycle()
        }
    } finally {
        if (small !== source) small.recycle()
    }
}

private fun boxBlur(input: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean): IntArray {
    val output = IntArray(input.size)
    for (y in 0 until height) {
        for (x in 0 until width) {
            var red = 0
            var green = 0
            var blue = 0
            var count = 0
            for (offset in -radius..radius) {
                val sampleX = if (horizontal) (x + offset).coerceIn(0, width - 1) else x
                val sampleY = if (horizontal) y else (y + offset).coerceIn(0, height - 1)
                val colour = input[sampleY * width + sampleX]
                red += Color.red(colour)
                green += Color.green(colour)
                blue += Color.blue(colour)
                count++
            }
            output[y * width + x] = Color.rgb(red / count, green / count, blue / count)
        }
    }
    return output
}
