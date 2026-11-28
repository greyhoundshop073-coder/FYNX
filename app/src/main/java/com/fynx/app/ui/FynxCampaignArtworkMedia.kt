package com.fynx.app.ui

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer

internal data class FynxCampaignArtworkInfo(
    val mimeType: String,
    val width: Int,
    val height: Int,
    val frameCount: Int = 1
) {
    val isAnimatedGif: Boolean get() = mimeType == "image/gif" && frameCount > 1
}

internal object FynxCampaignArtworkMedia {
    const val MAX_PHOTO_BYTES = 12 * 1024 * 1024
    const val MAX_GIF_BYTES = 8 * 1024 * 1024
    private const val MAX_GIF_DIMENSION = 1600
    private const val MAX_GIF_PIXELS = 2_560_000L
    private const val MAX_GIF_FRAMES = 60
    private const val MAX_GIF_DURATION_MS = 15_000L
    private const val MAX_PREVIEW_DIMENSION = 1440

    fun inspectUri(context: Context, uri: Uri): FynxCampaignArtworkInfo {
        val bytes = readBounded(context.contentResolver.openInputStream(uri) ?: error("The selected artwork could not be opened."))
        return inspectBytes(bytes, context.contentResolver.getType(uri))
    }

    fun inspectFile(file: File): FynxCampaignArtworkInfo {
        require(file.isFile) { "The saved campaign artwork could not be found." }
        val bytes = FileInputStream(file).use(::readBounded)
        return inspectBytes(bytes, null)
    }

    private fun readBounded(input: InputStream): ByteArray {
        input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_PHOTO_BYTES) { "Campaign artwork must be 12 MB or smaller." }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }

    private fun inspectBytes(bytes: ByteArray, reportedMime: String?): FynxCampaignArtworkInfo {
        require(bytes.isNotEmpty()) { "The selected artwork is empty." }
        val gif = bytes.startsWithAscii("GIF87a") || bytes.startsWithAscii("GIF89a")
        require(reportedMime?.trim()?.equals("image/gif", ignoreCase = true) != true || gif) {
            "The selected file is not a valid GIF image."
        }
        if (gif) {
            require(bytes.size <= MAX_GIF_BYTES) { "Animated GIFs must be 8 MB or smaller to keep Marketplace fast." }
            return inspectGif(bytes)
        }

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "Choose a valid JPG, PNG, WebP or GIF image." }
        require(options.outWidth <= 8000 && options.outHeight <= 8000 &&
            options.outWidth.toLong() * options.outHeight.toLong() <= 40_000_000L) {
            "Campaign photos are too large. Choose an image up to 8,000 px per side."
        }
        val mime = when {
            bytes.startsWithBytes(0xFF, 0xD8, 0xFF) -> "image/jpeg"
            bytes.startsWithBytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> "image/png"
            bytes.size >= 12 && bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
                bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP" -> "image/webp"
            bytes.size >= 12 && bytes.copyOfRange(4, 8).toString(Charsets.US_ASCII) == "ftyp" &&
                bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII).startsWith("hei") -> "image/heic"
            else -> reportedMime?.trim()?.lowercase()?.takeIf { it.startsWith("image/") && !it.endsWith("/*") }
                ?: "image/jpeg"
        }
        return FynxCampaignArtworkInfo(mime, options.outWidth, options.outHeight)
    }

    private fun inspectGif(bytes: ByteArray): FynxCampaignArtworkInfo {
        val input = ByteArrayInputStream(bytes)
        val header = ByteArray(6)
        require(input.read(header) == 6) { "This GIF file is incomplete." }
        require(String(header, Charsets.US_ASCII) == "GIF87a" || String(header, Charsets.US_ASCII) == "GIF89a") {
            "The selected GIF file is invalid."
        }
        val width = readLittleEndian(input)
        val height = readLittleEndian(input)
        require(width in 1..MAX_GIF_DIMENSION && height in 1..MAX_GIF_DIMENSION &&
            width.toLong() * height.toLong() <= MAX_GIF_PIXELS) {
            "GIF dimensions must be no larger than 1,600 px per side (2.56 megapixels total)."
        }
        val packed = input.read()
        require(packed >= 0) { "This GIF file is incomplete." }
        require(input.read() >= 0 && input.read() >= 0) { "This GIF file is incomplete." }
        if ((packed and 0x80) != 0) skipFully(input, 3 * (1 shl ((packed and 0x07) + 1)))

        var frames = 0
        var durationMs = 0L
        var pendingFrameDelayMs = 100L
        while (true) {
            val marker = input.read()
            when (marker) {
                0x3B -> break
                0x21 -> {
                    val label = input.read()
                    require(label >= 0) { "This GIF file is incomplete." }
                    var firstBlock = true
                    while (true) {
                        val size = input.read()
                        require(size >= 0) { "This GIF file is incomplete." }
                        if (size == 0) break
                        val block = ByteArray(size)
                        require(input.readFully(block) == size) { "This GIF file is incomplete." }
                        if (label == 0xF9 && firstBlock && size >= 4) {
                            val delay = (block[1].toInt() and 0xFF) or ((block[2].toInt() and 0xFF) shl 8)
                            pendingFrameDelayMs = if (delay == 0) 100L else delay * 10L
                        }
                        firstBlock = false
                    }
                }
                0x2C -> {
                    val descriptor = ByteArray(9)
                    require(input.readFully(descriptor) == descriptor.size) { "This GIF file is incomplete." }
                    val localPacked = descriptor[8].toInt() and 0xFF
                    if ((localPacked and 0x80) != 0) skipFully(input, 3 * (1 shl ((localPacked and 0x07) + 1)))
                    require(input.read() >= 0) { "This GIF file is incomplete." } // LZW minimum code size
                    skipSubBlocks(input)
                    frames += 1
                    durationMs += pendingFrameDelayMs
                    pendingFrameDelayMs = 100L
                    require(frames <= MAX_GIF_FRAMES) { "Animated GIFs can contain up to 60 frames." }
                    require(durationMs <= MAX_GIF_DURATION_MS) { "Animated GIFs can run for up to 15 seconds." }
                }
                else -> throw IllegalArgumentException("The selected GIF file has an invalid frame structure.")
            }
        }
        require(frames > 0) { "The selected GIF does not contain an image frame." }
        return FynxCampaignArtworkInfo("image/gif", width, height, frames)
    }

    private fun readLittleEndian(input: InputStream): Int {
        val low = input.read()
        val high = input.read()
        require(low >= 0 && high >= 0) { "This GIF file is incomplete." }
        return low or (high shl 8)
    }

    private fun skipFully(input: InputStream, count: Int) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining.toLong()).toInt()
            if (skipped > 0) remaining -= skipped
            else {
                require(input.read() >= 0) { "This GIF file is incomplete." }
                remaining--
            }
        }
    }

    private fun skipSubBlocks(input: InputStream) {
        while (true) {
            val size = input.read()
            require(size >= 0) { "This GIF file is incomplete." }
            if (size == 0) return
            skipFully(input, size)
        }
    }

    private fun InputStream.readFully(target: ByteArray): Int {
        var offset = 0
        while (offset < target.size) {
            val count = read(target, offset, target.size - offset)
            if (count < 0) break
            offset += count
        }
        return offset
    }

    private fun ByteArray.startsWithAscii(value: String): Boolean =
        size >= value.length && copyOfRange(0, value.length).toString(Charsets.US_ASCII) == value

    private fun ByteArray.startsWithBytes(vararg expected: Int): Boolean =
        size >= expected.size && expected.indices.all { (this[it].toInt() and 0xFF) == expected[it] }

    fun isGifFile(file: File): Boolean = runCatching {
        FileInputStream(file).use { input ->
            val header = ByteArray(6)
            input.read(header) == 6 &&
                (String(header, Charsets.US_ASCII) == "GIF87a" || String(header, Charsets.US_ASCII) == "GIF89a")
        }
    }.getOrDefault(false)

    private fun configureInfiniteGifLoop(drawable: Drawable): Drawable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            (drawable as? AnimatedImageDrawable)?.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        }
        return drawable
    }

    fun decodeGifFile(file: File): Drawable {
        // Apply the same full signature, byte-size, dimension, frame-count and
        // total-duration checks to cached/downloaded files as to picker uploads.
        // This keeps an oversized or malformed remote GIF from bypassing the
        // Marketplace performance limits simply because it came from the server.
        val artworkInfo = inspectFile(file)
        require(artworkInfo.mimeType == "image/gif") {
            "The downloaded campaign artwork is not a valid GIF."
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(file)
            return configureInfiniteGifLoop(ImageDecoder.decodeDrawable(source) { decoder, imageInfo, _ ->
                val sourceSize = imageInfo.size
                val sample = minOf(1f, MAX_PREVIEW_DIMENSION.toFloat() / maxOf(sourceSize.width, sourceSize.height).coerceAtLeast(1))
                decoder.setTargetSize(
                    (sourceSize.width * sample).toInt().coerceAtLeast(1),
                    (sourceSize.height * sample).toInt().coerceAtLeast(1)
                )
            })
        }
        val bytes = FileInputStream(file).use(::readBounded)
        val movie = Movie.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalArgumentException("This Android version could not decode the GIF.")
        require(movie.width() in 1..2048 && movie.height() in 1..2048 &&
            movie.width().toLong() * movie.height().toLong() <= 4_194_304L) {
            "This GIF is too large to animate safely on this device."
        }
        return FynxLegacyGifDrawable(movie)
    }

    fun decodePreview(context: Context, uri: Uri): Drawable {
        val bytes = readBounded(context.contentResolver.openInputStream(uri) ?: error("The selected artwork could not be opened."))
        val info = inspectBytes(bytes, context.contentResolver.getType(uri))
        if (info.mimeType == "image/gif") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                return configureInfiniteGifLoop(ImageDecoder.decodeDrawable(source) { decoder, imageInfo, _ ->
                    val sourceSize = imageInfo.size
                    val sample = minOf(1f, MAX_PREVIEW_DIMENSION.toFloat() / maxOf(sourceSize.width, sourceSize.height).coerceAtLeast(1))
                    decoder.setTargetSize(
                        (sourceSize.width * sample).toInt().coerceAtLeast(1),
                        (sourceSize.height * sample).toInt().coerceAtLeast(1)
                    )
                })
            }
            val movie = Movie.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IllegalArgumentException("This Android version could not decode the selected GIF.")
            return FynxLegacyGifDrawable(movie)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
            return BitmapDrawable(context.resources, ImageDecoder.decodeBitmap(source) { decoder, imageInfo, _ ->
                val sourceSize = imageInfo.size
                val sample = minOf(1f, MAX_PREVIEW_DIMENSION.toFloat() / maxOf(sourceSize.width, sourceSize.height).coerceAtLeast(1))
                decoder.setTargetSize(
                    (sourceSize.width * sample).toInt().coerceAtLeast(1),
                    (sourceSize.height * sample).toInt().coerceAtLeast(1)
                )
            })
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > MAX_PREVIEW_DIMENSION || bounds.outHeight / sample > MAX_PREVIEW_DIMENSION) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }
        ) ?: throw IllegalArgumentException("The selected photo could not be decoded.")
        return BitmapDrawable(context.resources, bitmap)
    }
}

internal class FynxLegacyGifDrawable(private val movie: Movie) : Drawable(), Runnable {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var playing = false
    private var startedAt = 0L

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.width() <= 0 || bounds.height() <= 0 || movie.width() <= 0 || movie.height() <= 0) return
        val scale = maxOf(bounds.width().toFloat() / movie.width(), bounds.height().toFloat() / movie.height())
        val drawWidth = movie.width() * scale
        val drawHeight = movie.height() * scale
        val save = canvas.save()
        canvas.clipRect(bounds)
        canvas.translate(bounds.left + (bounds.width() - drawWidth) / 2f, bounds.top + (bounds.height() - drawHeight) / 2f)
        canvas.scale(scale, scale)
        val duration = movie.duration().coerceAtLeast(100)
        movie.setTime(((SystemClock.uptimeMillis() - startedAt) % duration).toInt())
        movie.draw(canvas, 0f, 0f, paint)
        canvas.restoreToCount(save)
        if (playing) scheduleSelf(this, SystemClock.uptimeMillis() + 33L)
    }

    fun start() {
        if (playing) return
        playing = true
        startedAt = SystemClock.uptimeMillis()
        scheduleSelf(this, SystemClock.uptimeMillis() + 16L)
        invalidateSelf()
    }

    fun stop() {
        if (!playing) return
        playing = false
        unscheduleSelf(this)
        invalidateSelf()
    }

    override fun run() = invalidateSelf()
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth(): Int = movie.width()
    override fun getIntrinsicHeight(): Int = movie.height()
}

@SuppressLint("AppCompatCustomView") // Internal Compose artwork host; no AppCompat widgets or theme APIs are used.
internal class FynxCampaignArtworkImageView(context: Context) : ImageView(context), View.OnAttachStateChangeListener,
    android.view.ViewTreeObserver.OnPreDrawListener {
    private var artwork: Drawable? = null
    private var playbackAllowed = true
    private var preDrawRegistered = false

    init {
        scaleType = ScaleType.CENTER_CROP
        addOnAttachStateChangeListener(this)
    }

    fun showArtwork(drawable: Drawable, allowPlayback: Boolean) {
        if (artwork !== drawable) {
            stopAnimation(artwork)
            artwork = drawable
            setImageDrawable(drawable)
        }
        playbackAllowed = allowPlayback
        syncPlayback()
        updatePreDrawRegistration()
    }

    override fun onPreDraw(): Boolean {
        syncPlayback()
        return true
    }

    private fun updatePreDrawRegistration() {
        val needsAnimationVisibility =
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && artwork is AnimatedImageDrawable) ||
                artwork is FynxLegacyGifDrawable
        if (isAttachedToWindow && needsAnimationVisibility && !preDrawRegistered && viewTreeObserver.isAlive) {
            viewTreeObserver.addOnPreDrawListener(this)
            preDrawRegistered = true
        } else if ((!needsAnimationVisibility || !isAttachedToWindow) && preDrawRegistered && viewTreeObserver.isAlive) {
            viewTreeObserver.removeOnPreDrawListener(this)
            preDrawRegistered = false
        }
    }

    private fun syncPlayback() {
        val rect = Rect()
        val visible = playbackAllowed && ValueAnimator.areAnimatorsEnabled() && isShown && hasWindowFocus() && windowVisibility == View.VISIBLE && getGlobalVisibleRect(rect)
        val drawable = artwork ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && drawable is AnimatedImageDrawable) {
            if (visible && !drawable.isRunning) drawable.start()
            else if (!visible && drawable.isRunning) drawable.stop()
        } else if (drawable is FynxLegacyGifDrawable) {
            if (visible) drawable.start() else drawable.stop()
        }
    }

    private fun stopAnimation(drawable: Drawable?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && drawable is AnimatedImageDrawable) {
            if (drawable.isRunning) drawable.stop()
        } else if (drawable is FynxLegacyGifDrawable) {
            drawable.stop()
        }
    }

    override fun onViewAttachedToWindow(view: View) {
        updatePreDrawRegistration()
        syncPlayback()
    }

    override fun onViewDetachedFromWindow(view: View) {
        if (viewTreeObserver.isAlive && preDrawRegistered) viewTreeObserver.removeOnPreDrawListener(this)
        preDrawRegistered = false
        stopAnimation(artwork)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        syncPlayback()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        syncPlayback()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        syncPlayback()
    }
}

@Composable
internal fun FynxCampaignArtworkPreview(
    uri: Uri,
    modifier: Modifier = Modifier,
    maxHeight: Int = 180,
    playbackActive: Boolean = true
) {
    val context = LocalContext.current
    val drawableState = produceState<Drawable?>(initialValue = null, uri) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { FynxCampaignArtworkMedia.decodePreview(context, uri) }.getOrNull()
        }
    }
    val drawable = drawableState.value
    if (drawable == null) {
        Box(
            modifier.fillMaxWidth().heightIn(min = 88.dp, max = maxHeight.dp)
                .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("Artwork preview unavailable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        AndroidView(
            factory = { FynxCampaignArtworkImageView(it).apply { showArtwork(drawable, playbackActive) } },
            update = { it.showArtwork(drawable, playbackActive) },
            modifier = modifier.fillMaxWidth().heightIn(min = 88.dp, max = maxHeight.dp)
                .clip(RoundedCornerShape(12.dp))
                .semantics { contentDescription = "FYNX campaign artwork preview" }
        )
    }
}
