package com.fynx.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * Local face-tracked photo effects. ML Kit provides face geometry on device; sticker artwork is
 * rendered locally and this feature does not upload camera frames.
 *
 * Hair Colour and Funny Face are stylized overlays, not claims of hair segmentation or geometric
 * face morphing. Processed-video effects are a separate future stage.
 */
enum class CameraFaceEffect(val label: String) {
    OFF("Off"),
    CUTE_EARS("Cute Ears"),
    SMART_GLASSES("Smart Glasses"),
    ROYAL_CROWN("Royal Crown"),
    PUPPY_FACE("Puppy / Animal"),
    VIRTUAL_MAKEUP("Virtual Makeup"),
    HAIR_COLOUR("Hair Colour"),
    FUNNY_FACE("Funny Face")
}

data class CameraFaceBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

private fun newFynxFaceDetector(): FaceDetector {
    val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .enableTracking()
        .setMinFaceSize(0.08f)
        .build()
    return FaceDetection.getClient(options)
}

/** Keeps only recent frames and maps detected faces into PreviewView's FILL_CENTER coordinates. */
internal class FynxCameraFaceAnalyzer(
    context: Context,
    private val previewView: PreviewView,
    private val mainExecutor: Executor,
    private val isActive: () -> Boolean,
    private val isFrontCamera: () -> Boolean,
    private val onFaces: (List<CameraFaceBounds>) -> Unit
) : ImageAnalysis.Analyzer, Closeable {
    val executor = Executors.newSingleThreadExecutor()
    private val detector = newFynxFaceDetector()
    private val lastFrameAt = AtomicLong(0L)
    private val closed = AtomicLong(0L)

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        if (closed.get() != 0L || !isActive()) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val now = SystemClock.elapsedRealtime()
        val previous = lastFrameAt.getAndSet(now)
        if (now - previous < 90L) {
            imageProxy.close()
            return
        }
        val frameWidth = imageProxy.width
        val frameHeight = imageProxy.height
        val rotation = imageProxy.imageInfo.rotationDegrees
        val input = try {
            InputImage.fromMediaImage(mediaImage, rotation)
        } catch (_: Exception) {
            imageProxy.close()
            return
        }

        try {
            detector.process(input)
                .addOnSuccessListener(mainExecutor) { faces ->
                    if (closed.get() != 0L || !isActive()) {
                        onFaces(emptyList())
                        return@addOnSuccessListener
                    }
                    val uprightWidth = if (rotation == 90 || rotation == 270) frameHeight else frameWidth
                    val uprightHeight = if (rotation == 90 || rotation == 270) frameWidth else frameHeight
                    val viewWidth = previewView.width.toFloat()
                    val viewHeight = previewView.height.toFloat()
                    if (viewWidth <= 0f || viewHeight <= 0f || uprightWidth <= 0 || uprightHeight <= 0) {
                        onFaces(emptyList())
                        return@addOnSuccessListener
                    }
                    val scale = max(viewWidth / uprightWidth.toFloat(), viewHeight / uprightHeight.toFloat())
                    val offsetX = (viewWidth - uprightWidth * scale) / 2f
                    val offsetY = (viewHeight - uprightHeight * scale) / 2f
                    val front = isFrontCamera()
                    val mapped = faces.take(3).mapNotNull { face ->
                        val box = face.boundingBox
                        var left = box.left * scale + offsetX
                        var right = box.right * scale + offsetX
                        val top = box.top * scale + offsetY
                        val bottom = box.bottom * scale + offsetY
                        if (front) {
                            val mirroredLeft = viewWidth - right
                            right = viewWidth - left
                            left = mirroredLeft
                        }
                        val boundedLeft = left.coerceIn(0f, viewWidth)
                        val boundedRight = right.coerceIn(0f, viewWidth)
                        val boundedTop = top.coerceIn(0f, viewHeight)
                        val boundedBottom = bottom.coerceIn(0f, viewHeight)
                        if (boundedRight - boundedLeft < 18f || boundedBottom - boundedTop < 18f) null
                        else CameraFaceBounds(boundedLeft, boundedTop, boundedRight, boundedBottom)
                    }
                    onFaces(mapped)
                }
                .addOnFailureListener(mainExecutor) { onFaces(emptyList()) }
                .addOnCompleteListener { imageProxy.close() }
        } catch (_: Exception) {
            imageProxy.close()
        }
    }

    override fun close() {
        if (closed.compareAndSet(0L, 1L)) {
            onFaces(emptyList())
            detector.close()
            executor.shutdown()
        }
    }
}

/** Draws stickers in pixel coordinates; same geometry is used in live preview and saved photos. */
internal fun drawFynxCameraFaceEffect(canvas: Canvas, bounds: RectF, effect: CameraFaceEffect) {
    if (effect == CameraFaceEffect.OFF) return
    val width = bounds.width()
    val height = bounds.height()
    if (width < 18f || height < 18f) return
    val save = canvas.save()
    canvas.translate(bounds.left, bounds.top)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = max(2f, width * 0.018f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val cx = width / 2f
    when (effect) {
        CameraFaceEffect.OFF -> Unit
        CameraFaceEffect.CUTE_EARS -> {
            paint.color = 0xFFFFB5D0.toInt()
            canvas.save()
            canvas.rotate(-22f, width * 0.19f, height * 0.05f)
            canvas.drawRoundRect(RectF(width * 0.025f, -height * 0.19f, width * 0.30f, height * 0.17f), width * 0.13f, width * 0.13f, paint)
            canvas.restore()
            canvas.save()
            canvas.rotate(22f, width * 0.81f, height * 0.05f)
            canvas.drawRoundRect(RectF(width * 0.70f, -height * 0.19f, width * 0.975f, height * 0.17f), width * 0.13f, width * 0.13f, paint)
            canvas.restore()
            paint.color = 0xFFFF5F9D.toInt()
            canvas.drawOval(RectF(width * 0.10f, -height * 0.09f, width * 0.22f, height * 0.10f), paint)
            canvas.drawOval(RectF(width * 0.78f, -height * 0.09f, width * 0.90f, height * 0.10f), paint)
        }
        CameraFaceEffect.SMART_GLASSES -> {
            val eyeTop = height * 0.31f
            val eyeBottom = height * 0.48f
            val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xCC101820.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(RectF(width * 0.08f, eyeTop, width * 0.45f, eyeBottom), height * 0.04f, height * 0.04f, glassPaint)
            canvas.drawRoundRect(RectF(width * 0.55f, eyeTop, width * 0.92f, eyeBottom), height * 0.04f, height * 0.04f, glassPaint)
            stroke.color = 0xFFE8F5FF.toInt()
            canvas.drawRoundRect(RectF(width * 0.08f, eyeTop, width * 0.45f, eyeBottom), height * 0.04f, height * 0.04f, stroke)
            canvas.drawRoundRect(RectF(width * 0.55f, eyeTop, width * 0.92f, eyeBottom), height * 0.04f, height * 0.04f, stroke)
            canvas.drawLine(width * 0.45f, height * 0.38f, width * 0.55f, height * 0.38f, stroke)
        }
        CameraFaceEffect.ROYAL_CROWN -> {
            val path = Path().apply {
                moveTo(width * 0.12f, height * 0.12f)
                lineTo(width * 0.08f, -height * 0.20f)
                lineTo(width * 0.32f, -height * 0.025f)
                lineTo(width * 0.50f, -height * 0.28f)
                lineTo(width * 0.68f, -height * 0.025f)
                lineTo(width * 0.92f, -height * 0.20f)
                lineTo(width * 0.88f, height * 0.12f)
                close()
            }
            paint.color = 0xFFFFC928.toInt()
            canvas.drawPath(path, paint)
            paint.color = 0xFFB97913.toInt()
            canvas.drawRect(width * 0.12f, height * 0.08f, width * 0.88f, height * 0.16f, paint)
            paint.color = 0xFFFFF2A6.toInt()
            canvas.drawCircle(width * 0.32f, -height * 0.015f, width * 0.035f, paint)
            canvas.drawCircle(cx, -height * 0.19f, width * 0.04f, paint)
            canvas.drawCircle(width * 0.68f, -height * 0.015f, width * 0.035f, paint)
        }
        CameraFaceEffect.PUPPY_FACE -> {
            paint.color = 0xFF8B573A.toInt()
            canvas.save()
            canvas.rotate(-18f, width * 0.10f, height * 0.17f)
            canvas.drawOval(RectF(-width * 0.02f, height * 0.015f, width * 0.27f, height * 0.62f), paint)
            canvas.restore()
            canvas.save()
            canvas.rotate(18f, width * 0.90f, height * 0.17f)
            canvas.drawOval(RectF(width * 0.73f, height * 0.015f, width * 1.02f, height * 0.62f), paint)
            canvas.restore()
            paint.color = 0xFF30231F.toInt()
            canvas.drawOval(RectF(width * 0.40f, height * 0.52f, width * 0.60f, height * 0.67f), paint)
            paint.color = 0xFFFFFFFF.toInt()
            canvas.drawOval(RectF(width * 0.445f, height * 0.54f, width * 0.49f, height * 0.575f), paint)
        }
        CameraFaceEffect.VIRTUAL_MAKEUP -> {
            paint.color = 0x66FF3C86
            canvas.drawOval(RectF(width * 0.12f, height * 0.55f, width * 0.34f, height * 0.70f), paint)
            canvas.drawOval(RectF(width * 0.66f, height * 0.55f, width * 0.88f, height * 0.70f), paint)
            paint.color = 0xFFD93468.toInt()
            canvas.drawOval(RectF(width * 0.40f, height * 0.70f, width * 0.60f, height * 0.78f), paint)
            paint.color = 0xFFFFD4E3.toInt()
            canvas.drawOval(RectF(width * 0.445f, height * 0.705f, width * 0.49f, height * 0.73f), paint)
        }
        CameraFaceEffect.HAIR_COLOUR -> {
            val hair = Path().apply {
                moveTo(width * 0.06f, height * 0.17f)
                quadTo(width * 0.50f, -height * 0.20f, width * 0.94f, height * 0.17f)
                lineTo(width * 0.88f, height * 0.30f)
                quadTo(width * 0.50f, height * 0.05f, width * 0.12f, height * 0.30f)
                close()
            }
            paint.color = 0xFFB04CFF.toInt()
            canvas.drawPath(hair, paint)
            paint.color = 0xFFFFD8FF.toInt()
            canvas.drawCircle(width * 0.35f, height * 0.10f, width * 0.025f, paint)
            canvas.drawCircle(width * 0.65f, height * 0.10f, width * 0.025f, paint)
        }
        CameraFaceEffect.FUNNY_FACE -> {
            paint.color = 0xFFFFB4C8.toInt()
            canvas.drawOval(RectF(width * 0.10f, height * 0.55f, width * 0.36f, height * 0.68f), paint)
            canvas.drawOval(RectF(width * 0.64f, height * 0.55f, width * 0.90f, height * 0.68f), paint)
            paint.color = 0xFFFFA12B.toInt()
            canvas.drawOval(RectF(width * 0.36f, height * 0.40f, width * 0.64f, height * 0.66f), paint)
            stroke.color = 0xFF873A35.toInt()
            canvas.drawArc(RectF(width * 0.30f, height * 0.69f, width * 0.70f, height * 0.90f), 10f, 160f, false, stroke)
        }
    }
    canvas.restoreToCount(save)
}

/** Bakes the selected sticker into the outgoing photo using the same drawing function as preview. */
internal fun applyCameraFaceEffectToPhotoFile(context: Context, file: File, effect: CameraFaceEffect) {
    if (effect == CameraFaceEffect.OFF) return
    val detector = newFynxFaceDetector()
    try {
        val input = InputImage.fromFilePath(context, Uri.fromFile(file))
        val faces = Tasks.await(detector.process(input))
        if (faces.isEmpty()) return
        val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: error("Unable to decode captured photo for face effects")
        val output = decoded.copy(Bitmap.Config.ARGB_8888, true)
            ?: run { decoded.recycle(); error("Unable to prepare captured photo for face effects") }
        try {
            val canvas = Canvas(output)
            faces.forEach { face -> drawFynxCameraFaceEffect(canvas, RectF(face.boundingBox), effect) }
            FileOutputStream(file).use { stream ->
                check(output.compress(Bitmap.CompressFormat.JPEG, 95, stream)) { "Unable to save face effect" }
            }
        } finally {
            output.recycle()
            decoded.recycle()
        }
    } finally {
        detector.close()
    }
}
