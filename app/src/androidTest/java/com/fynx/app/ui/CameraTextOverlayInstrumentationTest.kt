package com.fynx.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CameraTextOverlayInstrumentationTest {
    @Test
    fun videoTextOverlayHonoursInclusiveStartAndExclusiveEndTimes() {
        val overlay = FynxCameraTextOverlay(text = "Timed text", startMs = 1_200L, endMs = 3_800L)
        assertFalse(isFynxCameraTextOverlayVisibleAt(overlay, 1_199L))
        assertTrue(isFynxCameraTextOverlayVisibleAt(overlay, 1_200L))
        assertTrue(isFynxCameraTextOverlayVisibleAt(overlay, 3_799L))
        assertFalse(isFynxCameraTextOverlayVisibleAt(overlay, 3_800L))
        assertFalse(isFynxCameraTextOverlayVisibleAt(overlay.copy(text = "   "), 2_000L))
    }

    @Test
    fun videoOverlayCoordinatesPreserveTopCentreAndBottomMediaPositions() {
        val topLeft = cameraVideoTextOverlayBackgroundAnchor(
            FynxCameraTextOverlay(x = 0.04f, y = 0.06f)
        )
        assertEquals("Left edge maps to negative NDC X", -0.92f, topLeft.first, 0.001f)
        assertEquals("Top edge maps to positive NDC Y", 0.88f, topLeft.second, 0.001f)

        val center = cameraVideoTextOverlayBackgroundAnchor(
            FynxCameraTextOverlay(x = 0.5f, y = 0.5f)
        )
        assertEquals("Media centre maps to NDC centre X", 0f, center.first, 0.001f)
        assertEquals("Media centre maps to NDC centre Y", 0f, center.second, 0.001f)

        val bottomRight = cameraVideoTextOverlayBackgroundAnchor(
            FynxCameraTextOverlay(x = 0.96f, y = 0.94f)
        )
        assertEquals("Right edge maps to positive NDC X", 0.92f, bottomRight.first, 0.001f)
        assertEquals("Bottom edge maps to negative NDC Y", -0.88f, bottomRight.second, 0.001f)
    }

    @Test
    fun mediaDraftCodecPreservesEveryEditableTextOverlayProperty() {
        val source = Uri.fromFile(File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "draft-edit-source.mp4"))
        val overlays = listOf(
            FynxCameraTextOverlay(
                id = "first-overlay",
                text = "Top title",
                x = 0.18f,
                y = 0.21f,
                color = Color.YELLOW,
                font = "Serif",
                backgroundColor = 0xB8000000.toInt(),
                bold = true,
                alignment = "LEFT",
                scale = 1.35f,
                startMs = 350L,
                endMs = 2_450L
            ),
            FynxCameraTextOverlay(
                id = "second-overlay",
                text = "Bottom note",
                x = 0.82f,
                y = 0.88f,
                color = Color.CYAN,
                font = "Mono",
                alignment = "RIGHT",
                scale = 0.75f,
                startMs = 1_100L,
                endMs = 4_900L
            )
        )
        val original = FynxCameraMediaDraftEdit(
            sourceUri = source,
            overlays = overlays,
            videoStyle = CameraVideoStyle.VINTAGE.name,
            captionText = "A saved manual caption"
        )

        val encoded = encodeFynxCameraMediaDraftEdit(original)
        val restored = decodeFynxCameraMediaDraftEdit(source, encoded)
            ?: error("Saved media edit state could not be restored")

        assertEquals(original.videoStyle, restored.videoStyle)
        assertEquals(original.captionText, restored.captionText)
        assertEquals(overlays.size, restored.overlays.size)
        overlays.zip(restored.overlays).forEach { (expected, actual) ->
            assertEquals(expected.id, actual.id)
            assertEquals(expected.text, actual.text)
            assertEquals(expected.x, actual.x, 0.0001f)
            assertEquals(expected.y, actual.y, 0.0001f)
            assertEquals(expected.color, actual.color)
            assertEquals(expected.font, actual.font)
            assertEquals(expected.backgroundColor, actual.backgroundColor)
            assertEquals(expected.bold, actual.bold)
            assertEquals(expected.alignment, actual.alignment)
            assertEquals(expected.scale, actual.scale, 0.0001f)
            assertEquals(expected.startMs, actual.startMs)
            assertEquals(expected.endMs, actual.endMs)
        }
    }

    @Test
    fun localPostDraftKeepsEditableMediaSourceAndOverlayStateUntilDelete() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val finalMedia = File(context.cacheDir, "fynx-draft-final-${System.currentTimeMillis()}.jpg")
        val editableSource = File(context.cacheDir, "fynx-draft-source-${System.currentTimeMillis()}.jpg")
        var draftId: String? = null
        try {
            finalMedia.writeBytes(byteArrayOf(1, 2, 3, 4))
            editableSource.writeBytes(byteArrayOf(5, 6, 7, 8))
            val finalUri = Uri.fromFile(finalMedia)
            val overlay = FynxCameraTextOverlay(
                id = "saved-draft-overlay",
                text = "Continue editing me",
                x = 0.24f,
                y = 0.71f,
                color = Color.CYAN,
                font = "Hand",
                backgroundColor = 0xA8000000.toInt(),
                bold = true,
                alignment = "RIGHT",
                scale = 1.4f,
                startMs = 640L,
                endMs = 4_800L
            )
            val saved = FynxLocalPostDraftStore.save(
                context = context,
                existingId = null,
                caption = "Draft with editable text",
                visibility = FynxPostVisibility.FRIENDS_ONLY,
                audience = FynxPostAudience.FRIENDS,
                selectedAudienceIds = emptySet(),
                location = null,
                textBackground = null,
                media = listOf(finalUri to "image"),
                music = null,
                mediaEdits = mapOf(
                    finalUri.toString() to FynxCameraMediaDraftEdit(
                        sourceUri = Uri.fromFile(editableSource),
                        overlays = listOf(overlay)
                    )
                )
            ).getOrThrow()
            draftId = saved.id

            val stored = FynxLocalPostDraftStore.list(context).firstOrNull { it.id == saved.id }
                ?: error("The locally saved media draft could not be reopened")
            assertTrue("Final media copy must be durable", File(stored.mediaPaths.single()).isFile)
            val storedSource = stored.mediaEditSourcePaths.single()
                ?: error("The editable source media was not preserved")
            assertTrue("Editable source copy must be durable", File(storedSource).isFile)
            val stateJson = stored.mediaEditStates.single()
                ?: error("The text overlay settings were not preserved")
            val restored = decodeFynxCameraMediaDraftEdit(Uri.fromFile(File(storedSource)), stateJson)
                ?: error("Editable text state could not be decoded from the saved draft")
            assertEquals(overlay, restored.overlays.single())
        } finally {
            draftId?.let { FynxLocalPostDraftStore.delete(context, it) }
            finalMedia.delete()
            editableSource.delete()
        }
    }

    @Test
    fun photoTextOverlayIsRenderedAtEachSelectedPositionAndKeepsSourceDimensions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "fynx-text-overlay-source-${System.currentTimeMillis()}.jpg")
        var outputFile: File? = null
        val bitmap = Bitmap.createBitmap(480, 720, Bitmap.Config.ARGB_8888)
        try {
            Canvas(bitmap).drawColor(Color.BLACK)
            source.outputStream().use { stream ->
                assertTrue("The source image could not be written", bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream))
            }
        } finally {
            bitmap.recycle()
        }

        try {
            val metrics = readFynxCameraMediaMetrics(Uri.fromFile(source), "image")
            assertEquals("Overlay geometry uses the source photo aspect ratio", 480f / 720f, metrics.aspectRatio, 0.01f)

            val exported = renderFynxPhotoTextOverlays(
                context,
                Uri.fromFile(source),
                listOf(
                    FynxCameraTextOverlay(
                        id = "camera-overlay-top",
                        text = "TOP",
                        x = 0.22f,
                        y = 0.24f,
                        color = Color.GREEN,
                        font = "Serif",
                        bold = true
                    ),
                    FynxCameraTextOverlay(
                        id = "camera-overlay-bottom",
                        text = "BOTTOM",
                        x = 0.78f,
                        y = 0.76f,
                        color = Color.MAGENTA,
                        font = "Mono",
                        scale = 1.25f
                    )
                )
            )
            outputFile = File(exported.path ?: error("Text export returned no file path"))
            val rendered = BitmapFactory.decodeFile(outputFile!!.absolutePath)
                ?: error("Text export could not be decoded")
            try {
                assertEquals("Photo export changed the source width", 480, rendered.width)
                assertEquals("Photo export changed the source height", 720, rendered.height)

                fun hasOverlayColourNear(xFraction: Float, yFraction: Float, expected: Int): Boolean {
                    val centerX = (rendered.width * xFraction).toInt()
                    val centerY = (rendered.height * yFraction).toInt()
                    val radiusX = (rendered.width * 0.16f).toInt()
                    val radiusY = (rendered.height * 0.12f).toInt()
                    val red = Color.red(expected)
                    val green = Color.green(expected)
                    val blue = Color.blue(expected)
                    var matches = 0
                    for (y in (centerY - radiusY).coerceAtLeast(0)..(centerY + radiusY).coerceAtMost(rendered.height - 1)) {
                        for (x in (centerX - radiusX).coerceAtLeast(0)..(centerX + radiusX).coerceAtMost(rendered.width - 1)) {
                            val pixel = rendered.getPixel(x, y)
                            if (Color.alpha(pixel) > 160 &&
                                kotlin.math.abs(Color.red(pixel) - red) < 90 &&
                                kotlin.math.abs(Color.green(pixel) - green) < 90 &&
                                kotlin.math.abs(Color.blue(pixel) - blue) < 90
                            ) matches++
                        }
                    }
                    return matches >= 8
                }

                assertTrue(
                    "Photo export did not burn the first overlay colour near its selected top position",
                    hasOverlayColourNear(0.22f, 0.24f, Color.GREEN)
                )
                assertTrue(
                    "Photo export did not burn the second overlay colour near its selected bottom position",
                    hasOverlayColourNear(0.78f, 0.76f, Color.MAGENTA)
                )
            } finally {
                rendered.recycle()
            }
        } finally {
            source.delete()
            outputFile?.delete()
        }
    }
}
