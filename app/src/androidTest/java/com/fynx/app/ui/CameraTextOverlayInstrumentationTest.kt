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
    fun videoTextTimingFallsBackToRecordedDurationWhenMetadataIsMissing() {
        assertEquals(3_400L, resolveFynxCameraVideoDurationMs(0L, 3_400L))
        assertEquals(3_400L, resolveFynxCameraVideoDurationMs(-1L, 3_400L))
        assertEquals(4_100L, resolveFynxCameraVideoDurationMs(4_100L, 3_400L))
        assertEquals(0L, resolveFynxCameraVideoDurationMs(0L, 0L))
    }

    @Test
    fun fileBackedPostDraftMediaCanBeReopenedByTheCameraEditor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "fynx-file-draft-" + System.currentTimeMillis() + ".jpg")
        val bitmap = Bitmap.createBitmap(48, 64, Bitmap.Config.ARGB_8888)
        var working: File? = null
        var original: File? = null
        try {
            Canvas(bitmap).drawColor(Color.BLUE)
            source.outputStream().use { stream ->
                assertTrue("The draft image could not be encoded", bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream))
            }
            val copied = copyCameraGalleryMediaToCache(context, Uri.fromFile(source))
            working = copied.first
            original = copied.second
            assertEquals("image", copied.third)
            assertTrue("The working draft image was not copied", working!!.isFile && working!!.length() > 0L)
            assertTrue("The original draft image was not retained", original!!.isFile && original!!.length() > 0L)
            assertEquals("jpg", working!!.extension)
        } finally {
            working?.takeIf { it != source }?.delete()
            original?.takeIf { it != source }?.delete()
            source.delete()
            bitmap.recycle()
        }
    }

    @Test
    fun providerBackedGalleryImageUriIsCopiedIntoAppOwnedMedia() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val name = "fynx-gallery-picker-${System.currentTimeMillis()}.jpg"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/FYNX Test")
        }
        val pickerUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("The Gallery provider did not accept the disposable image fixture")
        val bitmap = Bitmap.createBitmap(48, 64, Bitmap.Config.ARGB_8888)
        var working: File? = null
        var original: File? = null
        try {
            Canvas(bitmap).drawColor(Color.GREEN)
            resolver.openOutputStream(pickerUri, "w")!!.use { stream ->
                assertTrue("The Gallery fixture could not be encoded", bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream))
            }
            val copied = copyCameraGalleryMediaToCache(context, pickerUri)
            working = copied.first
            original = copied.second
            assertEquals("Gallery provider MIME should classify the item as an image", "image", copied.third)
            assertTrue("The Gallery source copy must be app-owned and non-empty", original!!.isFile && original!!.length() > 0L)
            assertTrue("The Gallery working copy must be app-owned and non-empty", working!!.isFile && working!!.length() > 0L)
            assertEquals("jpg", working!!.extension)
        } finally {
            working?.delete()
            original?.takeIf { it != working }?.delete()
            bitmap.recycle()
            resolver.delete(pickerUri, null, null)
        }
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
    fun localVideoPostDraftPreservesEditableSourceCaptionAndStyleAcrossRestore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val finalMedia = File(context.cacheDir, "fynx-video-draft-final-${System.currentTimeMillis()}.mp4")
        val editableSource = File(context.cacheDir, "fynx-video-draft-source-${System.currentTimeMillis()}.mp4")
        var draftId: String? = null
        try {
            // Store-level regression coverage: verifies durable file ownership and edit metadata,
            // not MP4 codec playback (which the camera runtime journey exercises).
            finalMedia.writeBytes(byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112))
            editableSource.writeBytes(byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112))
            val finalUri = Uri.fromFile(finalMedia)
            val overlay = FynxCameraTextOverlay(
                id = "saved-video-overlay",
                text = "Video draft overlay",
                x = 0.42f,
                y = 0.68f,
                startMs = 500L,
                endMs = 2_500L
            )
            val saved = FynxLocalPostDraftStore.save(
                context = context,
                existingId = null,
                caption = "Saved video caption",
                visibility = FynxPostVisibility.FRIENDS_ONLY,
                audience = FynxPostAudience.FRIENDS,
                selectedAudienceIds = emptySet(),
                location = null,
                textBackground = null,
                media = listOf(finalUri to "video"),
                music = null,
                mediaEdits = mapOf(
                    finalUri.toString() to FynxCameraMediaDraftEdit(
                        sourceUri = Uri.fromFile(editableSource),
                        overlays = listOf(overlay),
                        videoStyle = CameraVideoStyle.VIVID.name,
                        captionText = "Timed video captions"
                    )
                )
            ).getOrThrow()
            draftId = saved.id
            val restoredDraft = FynxLocalPostDraftStore.list(context).firstOrNull { it.id == saved.id }
                ?: error("The saved video post draft could not be restored")
            assertEquals(listOf("video"), restoredDraft.mediaTypes)
            val durableFinal = File(restoredDraft.mediaPaths.single())
            assertTrue("The final video draft copy must be durable", durableFinal.isFile && durableFinal.length() > 0L)
            assertEquals("mp4", durableFinal.extension)
            val sourcePath = restoredDraft.mediaEditSourcePaths.single()
                ?: error("The original editable video was not retained")
            assertTrue("The original video source copy must be durable", File(sourcePath).isFile)
            val editState = restoredDraft.mediaEditStates.single()
                ?: error("The saved video edit state is missing")
            val restoredEdit = decodeFynxCameraMediaDraftEdit(Uri.fromFile(File(sourcePath)), editState)
                ?: error("Saved video edit state could not be decoded")
            assertEquals(CameraVideoStyle.VIVID.name, restoredEdit.videoStyle)
            assertEquals("Timed video captions", restoredEdit.captionText)
            assertEquals(listOf(overlay), restoredEdit.overlays)
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
