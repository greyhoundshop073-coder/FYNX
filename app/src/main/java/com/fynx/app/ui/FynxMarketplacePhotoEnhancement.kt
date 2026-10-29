package com.fynx.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
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
    /** The beta segmentation SDK can crash in native code on Android 16/API 36. */
    fun isSupportedOnCurrentAndroid(): Boolean = Build.VERSION.SDK_INT < 36

    private const val MAX_WORKING_DIMENSION = 1600
    private const val OUTPUT_SIZE = 1080
    private const val SUBJECT_PADDING = 840
    private const val MIN_ALPHA = 18
    private val generatedFiles = ConcurrentHashMap<String, File>()

    suspend fun enhance(context: Context, source: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            // ML Kit subject-segmentation beta1 has an open native-crash report on Android 16/API 36.
            // A SIGSEGV cannot be recovered by Kotlin; keep this optional path fail-closed until a safe SDK is verified.
            if (Build.VERSION.SDK_INT >= 36) {
                throw IllegalStateException(
                    "Photo background cleanup is temporarily unavailable on Android 16 and newer. " +
                        "Your original photo is unchanged; you can still publish it normally."
                )
            }
            val mime = context.contentResolver.getType(source).orEmpty().lowercase()
            require(mime.startsWith("image/") || mime.isBlank()) {
                "Choose a still photo. Video enhancement is not supported."
            }
            val preparedBitmap = decodeBoundedInputBitmap(context, source)
            val input = InputImage.fromBitmap(preparedBitmap, 0)
            val segmenter = SubjectSegmentation.getClient(
                SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
            )
            val foreground = try {
                ensureSegmenterModelAvailable(context, segmenter)
                segmenter.process(input).awaitResult().foregroundBitmap
                    ?: throw IllegalStateException("No clean foreground was found.")
            } finally {
                segmenter.close()
                preparedBitmap.recycle()
            }
            val finished = try {
                renderProductPhoto(foreground)
            } finally {
                foreground.recycle()
            }
            try {
                val directory = File(context.cacheDir, "marketplace-photo-enhancements")
                if (!directory.exists() && !directory.mkdirs()) {
                    throw IllegalStateException("Temporary photo storage is unavailable.")
                }
                val destination = File(directory, "product_${UUID.randomUUID()}.jpg")
                try {
                    destination.outputStream().buffered().use { output ->
                        if (!finished.compress(Bitmap.CompressFormat.JPEG, 92, output)) {
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
                } catch (error: Throwable) {
                    destination.delete()
                    throw error
                }
            } finally {
                finished.recycle()
            }
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


    /**
     * Explicitly wait for an on-demand model install to reach a terminal state.
     * installModules() by itself only confirms the request was accepted.
     */
    private suspend fun ensureSegmenterModelAvailable(
        context: Context,
        segmenter: com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
    ) {
        val installer = ModuleInstall.getClient(context)
        val available = installer.areModulesAvailable(segmenter).awaitResult()
        if (available.areModulesAvailable()) return

        val installationCompleted = withTimeoutOrNull(120_000L) {
            suspendCancellableCoroutine<Unit> { continuation ->
            val lock = Any()
            val finished = AtomicBoolean(false)
            var targetSessionId: Int? = null
            val pendingUpdates = mutableListOf<ModuleInstallStatusUpdate>()

            lateinit var listener: InstallStatusListener

            fun finish(error: Throwable?) {
                if (!finished.compareAndSet(false, true)) return
                runCatching { installer.unregisterListener(listener) }
                if (!continuation.isActive) return
                if (error == null) continuation.resume(Unit)
                else continuation.resumeWithException(error)
            }

            fun processUpdate(update: ModuleInstallStatusUpdate) {
                val expectedSession = synchronized(lock) { targetSessionId }
                if (expectedSession == null || update.sessionId != expectedSession) return
                when (update.installState) {
                    ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED -> finish(null)
                    ModuleInstallStatusUpdate.InstallState.STATE_FAILED ->
                        finish(IllegalStateException("Google Play services could not install the photo model."))
                    ModuleInstallStatusUpdate.InstallState.STATE_CANCELED ->
                        finish(IllegalStateException("Photo model installation was cancelled."))
                }
            }

            listener = object : InstallStatusListener {
                override fun onInstallStatusUpdated(update: ModuleInstallStatusUpdate) {
                    var canProcess = false
                    synchronized(lock) {
                        val expectedSession = targetSessionId
                        if (expectedSession == null) {
                            pendingUpdates.add(update)
                        } else if (update.sessionId == expectedSession) {
                            canProcess = true
                        }
                    }
                    if (canProcess) processUpdate(update)
                }
            }

            continuation.invokeOnCancellation {
                finish(IllegalStateException("Photo enhancement was cancelled."))
            }

            val request = ModuleInstallRequest.newBuilder()
                .addApi(segmenter)
                .setListener(listener)
                .build()
            installer.installModules(request)
                .addOnSuccessListener { response ->
                    if (response.areModulesAlreadyInstalled() || response.sessionId == 0) {
                        finish(null)
                    } else {
                        val matchingUpdates = synchronized(lock) {
                            targetSessionId = response.sessionId
                            pendingUpdates.filter { it.sessionId == response.sessionId }.also {
                                pendingUpdates.clear()
                            }
                        }
                        matchingUpdates.forEach(::processUpdate)
                    }
                }
                .addOnFailureListener { error ->
                    finish(IllegalStateException("Google Play services could not prepare the photo model.", error))
                }
            }
        }
        if (installationCompleted == null) {
            throw IllegalStateException("Photo model download timed out. Your original photo is unchanged; retry when the connection is more reliable.")
        }

        val confirmed = installer.areModulesAvailable(segmenter).awaitResult()
        if (!confirmed.areModulesAvailable()) {
            throw IllegalStateException("The photo model is still unavailable. Keep the original photo and retry later.")
        }
    }

    /**
     * Decode to a bounded bitmap before ML inference. This prevents very high-resolution
     * gallery images from being passed to the segmenter at their full camera resolution and
     * honors EXIF rotation/mirroring so the clean-up preview stays upright.
     */
    private fun decodeBoundedInputBitmap(context: Context, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: throw IllegalStateException("The selected photo could not be opened.")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException("The selected photo could not be decoded.")
        }
        var sample = 1
        while (bounds.outWidth / sample > MAX_WORKING_DIMENSION ||
            bounds.outHeight / sample > MAX_WORKING_DIMENSION) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: throw IllegalStateException("The selected photo could not be decoded.")

        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                if (stream != null) ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                ) else ExifInterface.ORIENTATION_NORMAL
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
        }
        if (matrix.isIdentity) return decoded
        return try {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } finally {
            decoded.recycle()
        }
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

        var subject: Bitmap? = null
        var output: Bitmap? = null
        var completed = false
        try {
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

            // A completely opaque image often means segmentation did not remove its
            // background. Never label that unchanged result as enhanced.
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

            val subjectBitmap = Bitmap.createBitmap(working, left, top, subjectWidth, subjectHeight)
            subject = subjectBitmap
            val outputBitmap = Bitmap.createBitmap(OUTPUT_SIZE, OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
            output = outputBitmap
            val canvas = Canvas(outputBitmap)
            val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, OUTPUT_SIZE.toFloat(), OUTPUT_SIZE.toFloat(),
                    Color.WHITE, Color.rgb(245, 247, 250), Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, OUTPUT_SIZE.toFloat(), OUTPUT_SIZE.toFloat(), backgroundPaint)

            val factor = minOf(
                SUBJECT_PADDING.toFloat() / subjectBitmap.width.coerceAtLeast(1),
                SUBJECT_PADDING.toFloat() / subjectBitmap.height.coerceAtLeast(1)
            )
            val targetWidth = subjectBitmap.width * factor
            val targetHeight = subjectBitmap.height * factor
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
                subjectBitmap,
                Rect(0, 0, subjectBitmap.width, subjectBitmap.height),
                target,
                subjectPaint
            )
            completed = true
            return outputBitmap
        } finally {
            // Rendering can fail after the segmenter returns a bitmap (including on OOM).
            // Release intermediates on every path without recycling the returned image.
            subject?.takeIf { it !== working }?.recycle()
            if (working !== foreground) working.recycle()
            if (!completed) output?.recycle()
        }
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
