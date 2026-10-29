package com.fynx.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Offline image treatments for the camera's Studio group. "Mature Look" is an artistic treatment,
 * not a claim of AI age prediction; Cartoon uses colour quantization and edge drawing locally.
 */
enum class CameraStudioEffect(val label: String) {
    OFF("Off"),
    MATURE_LOOK("Mature Look"),
    FACE_SYMMETRY("Face Symmetry"),
    ANIME_CARTOON("Anime / Cartoon")
}

internal fun applyCameraStudioEffectToPhotoFile(file: File, effect: CameraStudioEffect) {
    if (effect == CameraStudioEffect.OFF) return
    val decoded = BitmapFactory.decodeFile(file.absolutePath)
        ?: error("Unable to decode the captured photo for Studio effects")
    val source = if (max(decoded.width, decoded.height) > 1600) {
        val ratio = 1600f / max(decoded.width, decoded.height).toFloat()
        Bitmap.createScaledBitmap(decoded, max(1, (decoded.width * ratio).toInt()), max(1, (decoded.height * ratio).toInt()), true)
            .also { if (it !== decoded) decoded.recycle() }
    } else decoded
    val width = source.width
    val height = source.height
    val sourcePixels = IntArray(width * height)
    source.getPixels(sourcePixels, 0, width, 0, 0, width, height)
    val outputPixels = sourcePixels.copyOf()
    var detector: FaceDetector? = null
    try {
        when (effect) {
            CameraStudioEffect.OFF -> return
            CameraStudioEffect.ANIME_CARTOON -> applyCartoonTreatment(sourcePixels, outputPixels, width, height)
            CameraStudioEffect.FACE_SYMMETRY, CameraStudioEffect.MATURE_LOOK -> {
                detector = FaceDetection.getClient(
                    FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                        .build()
                )
                val faces = Tasks.await(detector.process(InputImage.fromBitmap(source, 0)))
                require(faces.isNotEmpty()) { "No clear face was found. Try a front-facing photo in good light." }
                if (effect == CameraStudioEffect.FACE_SYMMETRY) {
                    faces.forEach { face ->
                        val bounds = face.boundingBox
                        val left = bounds.left.coerceIn(0, width - 1)
                        val right = bounds.right.coerceIn(left + 1, width)
                        val top = bounds.top.coerceIn(0, height - 1)
                        val bottom = bounds.bottom.coerceIn(top + 1, height)
                        val centerX = (left + right) / 2
                        val centerY = (top + bottom) / 2f
                        val halfWidth = max(1f, (right - left) / 2f)
                        val halfHeight = max(1f, (bottom - top) / 2f)
                        for (y in top until bottom) {
                            val dy = abs(y - centerY) / halfHeight
                            for (x in left until right) {
                                val dx = abs(x - centerX) / halfWidth
                                if (dx > 0.92f || dy > 0.94f) continue
                                val mirrorX = (centerX - abs(x - centerX)).coerceIn(left, right - 1)
                                val sourcePixel = sourcePixels[y * width + mirrorX]
                                val originalPixel = sourcePixels[y * width + x]
                                val edgeFade = min(1f, min((0.94f - dx) * 5f, (0.96f - dy) * 5f)).coerceIn(0f, 1f)
                                val alpha = 0.88f * edgeFade
                                outputPixels[y * width + x] = mixColours(originalPixel, sourcePixel, alpha)
                            }
                        }
                    }
                } else {
                    for (index in sourcePixels.indices) {
                        val pixel = sourcePixels[index]
                        val grey = (Color.red(pixel) * 0.299f + Color.green(pixel) * 0.587f + Color.blue(pixel) * 0.114f)
                        val red = (Color.red(pixel) * 0.72f + grey * 0.28f + 5f).toInt().coerceIn(0, 255)
                        val green = (Color.green(pixel) * 0.72f + grey * 0.28f + 1f).toInt().coerceIn(0, 255)
                        val blue = (Color.blue(pixel) * 0.72f + grey * 0.28f - 4f).toInt().coerceIn(0, 255)
                        outputPixels[index] = Color.rgb(red, green, blue)
                    }
                }
            }
        }
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            output.setPixels(outputPixels, 0, width, 0, 0, width, height)
            if (effect == CameraStudioEffect.MATURE_LOOK) {
                val canvas = Canvas(output)
                val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x8A604B42.toInt()
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeWidth = max(1.3f, width * 0.0015f)
                }
                // Wrinkle accents are constrained to detected face bounds rather than the whole image.
                val faceBounds = Tasks.await(
                    (detector ?: error("Face detector was not ready")).process(InputImage.fromBitmap(source, 0))
                )
                faceBounds.forEach { face ->
                    val rect = RectF(face.boundingBox)
                    val faceWidth = rect.width()
                    val faceHeight = rect.height()
                    if (faceWidth < 40f || faceHeight < 50f) return@forEach
                    for (row in 0..2) {
                        val y = rect.top + faceHeight * (0.24f + row * 0.055f)
                        val path = Path().apply {
                            moveTo(rect.left + faceWidth * 0.31f, y)
                            quadTo(rect.left + faceWidth * 0.50f, y + faceHeight * 0.018f, rect.left + faceWidth * 0.69f, y)
                        }
                        canvas.drawPath(path, linePaint)
                    }
                    canvas.drawArc(RectF(rect.left + faceWidth * 0.12f, rect.top + faceHeight * 0.44f, rect.left + faceWidth * 0.46f, rect.top + faceHeight * 0.61f), 205f, 125f, false, linePaint)
                    canvas.drawArc(RectF(rect.left + faceWidth * 0.54f, rect.top + faceHeight * 0.44f, rect.left + faceWidth * 0.88f, rect.top + faceHeight * 0.61f), 210f, 125f, false, linePaint)
                    val leftFold = Path().apply {
                        moveTo(rect.left + faceWidth * 0.30f, rect.top + faceHeight * 0.57f)
                        quadTo(rect.left + faceWidth * 0.25f, rect.top + faceHeight * 0.68f, rect.left + faceWidth * 0.37f, rect.top + faceHeight * 0.74f)
                    }
                    val rightFold = Path().apply {
                        moveTo(rect.left + faceWidth * 0.70f, rect.top + faceHeight * 0.57f)
                        quadTo(rect.left + faceWidth * 0.75f, rect.top + faceHeight * 0.68f, rect.left + faceWidth * 0.63f, rect.top + faceHeight * 0.74f)
                    }
                    canvas.drawPath(leftFold, linePaint)
                    canvas.drawPath(rightFold, linePaint)
                }
            }
            FileOutputStream(file).use { stream ->
                check(output.compress(Bitmap.CompressFormat.JPEG, 94, stream)) { "Unable to save the Studio effect" }
            }
        } finally {
            output.recycle()
        }
    } finally {
        detector?.close()
        source.recycle()
    }
}

private fun applyCartoonTreatment(source: IntArray, output: IntArray, width: Int, height: Int) {
    fun luminance(pixel: Int): Int =
        (Color.red(pixel) * 0.299f + Color.green(pixel) * 0.587f + Color.blue(pixel) * 0.114f).toInt()
    for (y in 0 until height) {
        val above = max(0, y - 1)
        val below = min(height - 1, y + 1)
        for (x in 0 until width) {
            val left = max(0, x - 1)
            val right = min(width - 1, x + 1)
            val index = y * width + x
            val pixel = source[index]
            val edge = abs(luminance(source[y * width + right]) - luminance(source[y * width + left])) +
                abs(luminance(source[below * width + x]) - luminance(source[above * width + x]))
            if (edge > 70) {
                output[index] = Color.rgb(24, 22, 32)
            } else {
                fun posterize(channel: Int): Int = ((channel / 42) * 42 + 21).coerceIn(0, 255)
                val r = posterize(Color.red(pixel))
                val g = posterize(Color.green(pixel))
                val b = posterize(Color.blue(pixel))
                output[index] = Color.rgb(
                    (r * 1.05f).toInt().coerceIn(0, 255),
                    (g * 1.02f).toInt().coerceIn(0, 255),
                    (b * 1.08f).toInt().coerceIn(0, 255)
                )
            }
        }
    }
}

private fun mixColours(original: Int, mirrored: Int, alpha: Float): Int {
    val inverse = 1f - alpha
    return Color.rgb(
        (Color.red(original) * inverse + Color.red(mirrored) * alpha).toInt().coerceIn(0, 255),
        (Color.green(original) * inverse + Color.green(mirrored) * alpha).toInt().coerceIn(0, 255),
        (Color.blue(original) * inverse + Color.blue(mirrored) * alpha).toInt().coerceIn(0, 255)
    )
}
