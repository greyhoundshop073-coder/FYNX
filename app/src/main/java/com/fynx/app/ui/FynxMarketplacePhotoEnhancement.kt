package com.fynx.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Optional, on-device Marketplace product-photo background cleanup.
 *
 * The original URI is never overwritten. Generated output lives only in app-private cache
 * until the Marketplace upload has consumed it. Model availability is managed by Google Play
 * services, so the first use may require downloading the model; image pixels are processed
 * locally and are not sent to a background-removal service.
 */
internal object FynxMarketplacePhotoEnhancement {
    private const val MAX_WORKING_DIMENSION = 1600
    private const val OUTPUT_SIZE = 1080
    private const val SUBJECT_PADDING = 840
    private const val MIN_ALPHA = 18
    private val generatedFiles = ConcurrentHashMap<String, File>()

    suspend fun enhance(context: Context, source: Uri): Result<Uri> {
        return try {
            val mime = context.contentResolver.getType(source).orEmpty().lowercase()
            require(mime.startsWith("image/") || mime.isBlank()) {
                "Choose a still photo. Video enhancement is not supported."
            }
            val input = InputImage.fromFilePath(context, source)
            val segmenter = SubjectSegmentation.getClient(
                SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
            )
            val foreground = try {
                val moduleInstaller = ModuleInstall.getClient(context)
                val availability = moduleInstaller.areModulesAvailable(segmenter).awaitResult()
                if (!availability.areModulesAvailable()) {
                    // Sideloaded/debug APKs may not have received Play Store install-time model download.
                    moduleInstaller.installModules(
                        ModuleInstallRequest.newBuilder().addApi(segmenter).build()
                    ).awaitResult()
                }
                // Await model initialization before processing so first-use installation does not
                // silently return an empty result while Google Play services is still preparing it.
                segmenter.initTask.awaitResult()
                segmenter.process(input).awaitResult().foregroundBitmap
                    ?: throw IllegalStateException("No clean foreground was found.")
            } finally {
                segmenter.close()
            }
            val finished = renderProductPhoto(foreground)
            val directory = File(context.cacheDir, "marketplace-photo-enhancements")
            if (!directory.exists() && !directory.mkdirs()) {
                throw IllegalStateException("Temporary photo storage is unavailable.")
            }
            val destination = File(directory, "product_${UUID.randomUUID()}.jpg")
            destination.outputStream().buffered().use { output ->
                if (!finished.compress(Bitmap.CompressFormat.JPEG, 92, output)) {
                    destination.delete()
                    throw IllegalStateException("The enhanced photo could not be saved.")
                }
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destination
            )
            generatedFiles[uri.toString()] = destination
            Result.success(uri)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    /** Delete only files created by this helper. Never delete gallery or camera originals. */
    fun discard(uri: Uri?) {
        if (uri == null) return
        generatedFiles.remove(uri.toString())?.let { file ->
            runCatching { file.delete() }
        }
    }

    fun discardAll(uris: Collection<Uri>) {
        uris.distinct().forEach { discard(it) }
    }

    private fun renderProductPhoto(foreground: Bitmap): Bitmap {
        val scaleDown = minOf(
            1f,
            MAX_WORKING_DIMENSION.toFloat() / foreground.width.coerceAtLeast(1),
            MAX_WORKING_DIMENSION.toFloat() / foreground.height.coerceAtLeast(1)
        )
        val working = if (scaleDown < 1f) {
            Bitmap.createScaledBitmap(
                foreground,
                (foreground.width * scaleDown).toInt().coerceAtLeast(1),
                (foreground.height * scaleDown).toInt().coerceAtLeast(1),
                true
            )
        } else foreground

        val width = working.width
        val height = working.height
        val row = IntArray(width)
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            working.getPixels(row, 0, width, 0, y, width, 1)
            for (x in 0 until width) {
                if (Color.alpha(row[x]) > MIN_ALPHA) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < left || bottom < top) {
            throw IllegalStateException("No product foreground was found.")
        }

        // A completely opaque image often means the segmentation model did not remove its
        // background. Refuse to label that as enhanced rather than silently make a false result.
        val corners = intArrayOf(
            working.getPixel(0, 0),
            working.getPixel(width - 1, 0),
            working.getPixel(0, height - 1),
            working.getPixel(width - 1, height - 1)
        )
        val subjectWidth = right - left + 1
        val subjectHeight = bottom - top + 1
        val coversAlmostEverything = subjectWidth.toLong() * subjectHeight.toLong() >=
            width.toLong() * height.toLong() * 98L / 100L
        if (coversAlmostEverything && corners.all { Color.alpha(it) >= 250 }) {
            throw IllegalStateException("The background could not be separated clearly.")
        }

        val subject = Bitmap.createBitmap(working, left, top, subjectWidth, subjectHeight)
        val output = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, OUTPUT_SIZE.toFloat(), OUTPUT_SIZE.toFloat(),
                Color.WHITE, Color.rgb(245, 247, 250), Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, OUTPUT_SIZE.toFloat(), OUTPUT_SIZE.toFloat(), backgroundPaint)

        val factor = minOf(
            SUBJECT_PADDING.toFloat() / subject.width.coerceAtLeast(1),
            SUBJECT_PADDING.toFloat() / subject.height.coerceAtLeast(1)
        )
        val targetWidth = subject.width * factor
        val targetHeight = subject.height * factor
        val target = RectF(
            (OUTPUT_SIZE - targetWidth) / 2f,
            (OUTPUT_SIZE - targetHeight) / 2f - 12f,
            (OUTPUT_SIZE + targetWidth) / 2f,
            (OUTPUT_SIZE + targetHeight) / 2f - 12f
        )

        // A small soft oval grounds the object without changing its shape or proportions.
        val shadowBounds = RectF(
            target.left + target.width() * 0.17f,
            target.bottom - 5f,
            target.right - target.width() * 0.17f,
            target.bottom + 20f
        )
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(24, 28, 36, 52)
            setShadowLayer(20f, 0f, 6f, Color.argb(38, 28, 36, 52))
        }
        canvas.drawOval(shadowBounds, shadowPaint)

        val subjectPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(
            subject,
            Rect(0, 0, subject.width, subject.height),
            target,
            subjectPaint
        )
        if (subject !== working) subject.recycle()
        if (working !== foreground) working.recycle()
        return output
    }

    private suspend fun <T> Task<T>.awaitResult(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { value ->
                if (continuation.isActive) continuation.resume(value)
            }
            addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            addOnCanceledListener {
                if (continuation.isActive) {
                    continuation.cancel(CancellationException("Photo enhancement was cancelled."))
                }
            }
            continuation.invokeOnCancellation {
                // ML Kit does not expose task cancellation; late callbacks are ignored above.
            }
        }
}
