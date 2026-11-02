package com.fynx.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.SpannableString
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

/** Media text is stored as normalized coordinates so it renders consistently across screen sizes. */
internal data class FynxCameraTextOverlay(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val x: Float = 0.5f,
    val y: Float = 0.43f,
    val color: Int = android.graphics.Color.WHITE,
    val font: String = "Modern",
    val backgroundColor: Int? = null,
    val bold: Boolean = false,
    val alignment: String = "CENTER",
    val scale: Float = 1f,
    val startMs: Long = 0L,
    val endMs: Long = Long.MAX_VALUE
)

internal data class FynxCameraMediaMetrics(val durationMs: Long, val aspectRatio: Float)

/** Read intrinsic media geometry, including encoded video rotation, away from the UI thread. */
internal fun readFynxCameraMediaMetrics(uri: Uri, mediaType: String): FynxCameraMediaMetrics {
    val fallback = FynxCameraMediaMetrics(0L, 9f / 16f)
    val path = uri.path?.takeIf { it.isNotBlank() } ?: return fallback
    val file = File(path)
    if (!file.isFile || file.length() <= 0L) return fallback
    if (mediaType == "image") {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return fallback
        return FynxCameraMediaMetrics(0L, (bounds.outWidth.toFloat() / bounds.outHeight.toFloat()).coerceIn(0.2f, 5f))
    }
    if (mediaType != "video" && mediaType != "video_note") return fallback
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 0f
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 0f
        val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        if (width <= 0f || height <= 0f) FynxCameraMediaMetrics(duration, fallback.aspectRatio)
        else {
            val aspect = if (rotation == 90 || rotation == 270) height / width else width / height
            FynxCameraMediaMetrics(duration, aspect.coerceIn(0.2f, 5f))
        }
    } catch (_: Exception) {
        fallback
    } finally {
        runCatching { retriever.release() }
    }
}

private fun cameraTextColorName(color: Int): String = when (color) {
    android.graphics.Color.WHITE -> "White"
    android.graphics.Color.YELLOW -> "Yellow"
    android.graphics.Color.CYAN -> "Cyan"
    android.graphics.Color.MAGENTA -> "Magenta"
    android.graphics.Color.RED -> "Red"
    android.graphics.Color.GREEN -> "Green"
    0xFFFF8A00.toInt() -> "Orange"
    0xFFFF5C8A.toInt() -> "Pink"
    0xFF8B75FF.toInt() -> "Violet"
    android.graphics.Color.BLACK -> "Black"
    else -> "Custom"
}

private fun cameraTextFontFamily(name: String): FontFamily = when (name) {
    "Serif" -> FontFamily.Serif
    "Mono" -> FontFamily.Monospace
    "Hand" -> FontFamily.Cursive
    else -> FontFamily.SansSerif
}

private fun cameraTextAlign(name: String): TextAlign = when (name) {
    "LEFT" -> TextAlign.Start
    "RIGHT" -> TextAlign.End
    else -> TextAlign.Center
}

internal fun isFynxCameraTextOverlayVisibleAt(item: FynxCameraTextOverlay, timeMs: Long): Boolean =
    item.text.isNotBlank() && timeMs >= item.startMs && timeMs < item.endMs

private fun formatCameraOverlayTime(timeMs: Long): String {
    val seconds = (timeMs.coerceAtLeast(0L) / 1000L).toInt()
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

/**
 * Renders the text directly on the media. While editing, BasicTextField has no decoration,
 * border, or filled container: only the text and insertion cursor are shown.
 */
@Composable
internal fun CameraTextOverlayLayer(
    overlays: List<FynxCameraTextOverlay>,
    editingId: String?,
    onChange: (FynxCameraTextOverlay) -> Unit,
    onSelect: (String) -> Unit,
    mediaAspectRatio: Float = 9f / 16f,
    centerCrop: Boolean = false,
    playbackPositionMs: Long? = null,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(editingId) {
        if (editingId != null) {
            delay(70L)
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val viewAspect = widthPx / heightPx
        val mediaAspect = mediaAspectRatio.takeIf { it.isFinite() && it > 0f } ?: viewAspect
        // Overlay coordinates stay normalized against source media. Map that frame into the
        // viewport using the same crop/fit mode as the underlying ImageView/VideoView.
        val mediaWidthPx: Float
        val mediaHeightPx: Float
        if (centerCrop) {
            if (mediaAspect >= viewAspect) {
                mediaHeightPx = heightPx
                mediaWidthPx = heightPx * mediaAspect
            } else {
                mediaWidthPx = widthPx
                mediaHeightPx = widthPx / mediaAspect
            }
        } else {
            if (mediaAspect >= viewAspect) {
                mediaWidthPx = widthPx
                mediaHeightPx = widthPx / mediaAspect
            } else {
                mediaHeightPx = heightPx
                mediaWidthPx = heightPx * mediaAspect
            }
        }
        val mediaLeftPx = (widthPx - mediaWidthPx) / 2f
        val mediaTopPx = (heightPx - mediaHeightPx) / 2f
        val density = LocalDensity.current
        val overlayTextMaxWidth = with(density) { (mediaWidthPx * 0.84f).toDp() }
        overlays.forEach { overlay ->
            val editing = overlay.id == editingId
            val visibleAtPlayback = playbackPositionMs == null || isFynxCameraTextOverlayVisibleAt(overlay, playbackPositionMs)
            if ((overlay.text.isNotBlank() && visibleAtPlayback) || editing) {
                val textStyle = TextStyle(
                    color = ComposeColor(overlay.color),
                    fontSize = 36.sp,
                    fontWeight = if (overlay.bold) FontWeight.Bold else FontWeight.Medium,
                    fontFamily = cameraTextFontFamily(overlay.font),
                    textAlign = cameraTextAlign(overlay.alignment),
                    lineHeight = 40.sp
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset {
                            val mediaX = mediaLeftPx + overlay.x.coerceIn(0.04f, 0.96f) * mediaWidthPx
                            val mediaY = mediaTopPx + overlay.y.coerceIn(0.06f, 0.94f) * mediaHeightPx
                            IntOffset((mediaX - widthPx / 2f).roundToInt(), (mediaY - heightPx / 2f).roundToInt())
                        }
                        .graphicsLayer {
                            scaleX = overlay.scale.coerceIn(0.55f, 2.4f)
                            scaleY = overlay.scale.coerceIn(0.55f, 2.4f)
                        }
                ) {
                    if (editing) {
                        BasicTextField(
                            value = overlay.text,
                            onValueChange = { onChange(overlay.copy(text = it.take(240))) },
                            textStyle = textStyle,
                            cursorBrush = SolidColor(ComposeColor(overlay.color)),
                            modifier = Modifier
                                .widthIn(min = 24.dp, max = overlayTextMaxWidth)
                                .focusRequester(focusRequester)
                                .semantics { contentDescription = "Text on media" }
                                .then(
                                    if (overlay.backgroundColor != null) {
                                        Modifier.background(
                                            ComposeColor(overlay.backgroundColor),
                                            RoundedCornerShape(4.dp)
                                        ).padding(horizontal = 6.dp, vertical = 2.dp)
                                    } else {
                                        Modifier.padding(2.dp)
                                    }
                                )
                        )
                    } else {
                        Text(
                            text = overlay.text,
                            style = textStyle,
                            modifier = Modifier
                                .then(
                                    if (overlay.backgroundColor != null) {
                                        Modifier.background(
                                            ComposeColor(overlay.backgroundColor),
                                            RoundedCornerShape(4.dp)
                                        ).padding(horizontal = 6.dp, vertical = 2.dp)
                                    } else Modifier
                                )
                                .clickable { onSelect(overlay.id) }
                                .pointerInput(overlay.id, overlay.x, overlay.y, overlay.scale, widthPx, heightPx, mediaAspect, centerCrop) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        onChange(
                                            overlay.copy(
                                                x = (overlay.x + pan.x / mediaWidthPx.coerceAtLeast(1f)).coerceIn(0.04f, 0.96f),
                                                y = (overlay.y + pan.y / mediaHeightPx.coerceAtLeast(1f)).coerceIn(0.06f, 0.94f),
                                                scale = (overlay.scale * zoom).coerceIn(0.55f, 2.4f)
                                            )
                                        )
                                    }
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CameraTextOverlayTools(
    selected: FynxCameraTextOverlay?,
    overlayCount: Int,
    isVideo: Boolean,
    onAdd: () -> Unit,
    onColor: (Int) -> Unit,
    onFont: (String) -> Unit,
    onToggleBackground: () -> Unit,
    onToggleBold: () -> Unit,
    onAlignment: () -> Unit,
    videoDurationMs: Long = 0L,
    onTiming: (Long, Long) -> Unit = { _, _ -> },
    compact: Boolean = false,
    onDelete: () -> Unit,
    onDone: () -> Unit
) {
    val swatches = listOf(
        android.graphics.Color.WHITE,
        android.graphics.Color.YELLOW,
        android.graphics.Color.CYAN,
        android.graphics.Color.MAGENTA,
        android.graphics.Color.RED,
        android.graphics.Color.GREEN,
        0xFFFF8A00.toInt(),
        0xFFFF5C8A.toInt(),
        0xFF8B75FF.toInt(),
        android.graphics.Color.BLACK
    )
    Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 7.dp)) {
        if (!compact) Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Text on media", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (isVideo) "Visible on the video after export" else "Position it directly over your photo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onAdd) { Text("+ Add text") }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            swatches.forEach { swatch ->
                Surface(
                    onClick = { onColor(swatch) },
                    modifier = Modifier.size(if (compact) 25.dp else 29.dp).semantics { contentDescription = "Text colour ${cameraTextColorName(swatch)}" },
                    shape = CircleShape,
                    color = ComposeColor(swatch),
                    border = androidx.compose.foundation.BorderStroke(
                        if (selected?.color == swatch) 3.dp else 1.dp,
                        if (selected?.color == swatch) ComposeColor(0xFFB8A7FF) else ComposeColor.White.copy(alpha = 0.55f)
                    ),
                    tonalElevation = 1.dp
                ) {}
            }
            Spacer(Modifier.width(2.dp))
            listOf("Modern", "Serif", "Mono", "Hand").forEach { font ->
                FilterChip(
                    selected = selected?.font == font,
                    onClick = { onFont(font) },
                    label = { Text(font) },
                    enabled = selected != null
                )
            }
        }
        if (!compact && isVideo && selected != null && videoDurationMs > 1L) {
            val duration = videoDurationMs.coerceAtLeast(2L)
            val start = selected.startMs.coerceIn(0L, duration - 1L)
            val end = selected.endMs.coerceIn(start + 1L, duration)
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(ComposeColor(0xFF25364A).copy(alpha = 0.42f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Text timing", style = MaterialTheme.typography.labelLarge, color = ComposeColor.White)
                    Text("${formatCameraOverlayTime(start)} – ${formatCameraOverlayTime(end)}", style = MaterialTheme.typography.labelSmall, color = ComposeColor(0xFFB8A7FF))
                }
                Text("Show from", style = MaterialTheme.typography.labelSmall, color = ComposeColor.White.copy(alpha = 0.76f))
                Slider(
                    value = start.toFloat(),
                    onValueChange = { value -> onTiming(value.toLong().coerceIn(0L, end - 1L), end) },
                    valueRange = 0f..(duration - 1L).toFloat()
                )
                Text("Show until", style = MaterialTheme.typography.labelSmall, color = ComposeColor.White.copy(alpha = 0.76f))
                Slider(
                    value = end.toFloat(),
                    onValueChange = { value -> onTiming(start, value.toLong().coerceIn(start + 1L, duration)) },
                    valueRange = (start + 1L).toFloat()..duration.toFloat()
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selected?.backgroundColor != null,
                onClick = onToggleBackground,
                label = { Text("Highlight") },
                enabled = selected != null
            )
            FilterChip(
                selected = selected?.bold == true,
                onClick = onToggleBold,
                label = { Text("Bold") },
                enabled = selected != null
            )
            FilterChip(
                selected = selected?.alignment != "CENTER",
                onClick = onAlignment,
                label = { Text("Align: ${selected?.alignment?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Center"}") },
                enabled = selected != null
            )
            if (selected != null) {
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
            Button(onClick = onDone, enabled = selected != null || overlayCount == 0) { Text("Done") }
        }
    }
}

/** Apply text to a JPEG copy, leaving the editable source/baseline untouched. */
internal fun renderFynxPhotoTextOverlays(
    context: Context,
    sourceUri: Uri,
    overlays: List<FynxCameraTextOverlay>
): Uri {
    val visible = overlays.filter { it.text.isNotBlank() }
    if (visible.isEmpty()) return sourceUri
    val sourcePath = sourceUri.path ?: error("The photo file is unavailable for text export.")
    val source = File(sourcePath)
    require(source.isFile && source.length() > 0L) { "The photo file is unavailable for text export." }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(source.absolutePath, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The photo could not be decoded for text export." }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 4096) sample *= 2
    val bitmap = BitmapFactory.decodeFile(
        source.absolutePath,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
    ) ?: error("The photo could not be decoded for text export.")

    val output = File(context.cacheDir, "fynx_photo_text_${System.currentTimeMillis()}.jpg")
    try {
        val canvas = Canvas(bitmap)
        visible.forEach { item ->
            val text = item.text.trim()
            if (text.isBlank()) return@forEach
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = item.color
                textSize = (bitmap.width * 0.055f * item.scale).coerceIn(24f, bitmap.width * 0.12f)
                typeface = Typeface.create(
                    when (item.font) {
                        "Serif" -> "serif"
                        "Mono" -> "monospace"
                        "Hand" -> "cursive"
                        else -> "sans-serif"
                    },
                    if (item.bold) Typeface.BOLD else Typeface.NORMAL
                )
                if (item.backgroundColor == null) {
                    setShadowLayer(3f, 0f, 2f, 0xB8000000.toInt())
                }
            }
            val styled = SpannableString(text)
            item.backgroundColor?.let {
                styled.setSpan(BackgroundColorSpan(it), 0, styled.length, 0)
            }
            if (item.bold) styled.setSpan(StyleSpan(Typeface.BOLD), 0, styled.length, 0)
            val alignment = when (item.alignment) {
                "LEFT" -> Layout.Alignment.ALIGN_NORMAL
                "RIGHT" -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_CENTER
            }
            val layout = StaticLayout.Builder
                .obtain(styled, 0, styled.length, textPaint, (bitmap.width * 0.84f).toInt().coerceAtLeast(1))
                .setAlignment(alignment)
                .setIncludePad(false)
                .setMaxLines(8)
                .build()
            val x = (item.x.coerceIn(0.04f, 0.96f) * bitmap.width - layout.width / 2f)
                .coerceIn(0f, (bitmap.width - layout.width).coerceAtLeast(0).toFloat())
            val y = (item.y.coerceIn(0.06f, 0.94f) * bitmap.height - layout.height / 2f)
                .coerceIn(0f, (bitmap.height - layout.height).coerceAtLeast(0).toFloat())
            canvas.save()
            canvas.translate(x, y)
            layout.draw(canvas)
            canvas.restore()
        }
        FileOutputStream(output).use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                "The edited photo could not be saved."
            }
        }
        require(output.isFile && output.length() > 0L) { "The edited photo export is empty." }
        return Uri.fromFile(output)
    } catch (failure: Throwable) {
        output.delete()
        throw failure
    } finally {
        bitmap.recycle()
    }
}
