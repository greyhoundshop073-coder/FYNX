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
    fun photoTextOverlayIsRenderedIntoTheExportedImage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "fynx-text-overlay-source.jpg")
        val bitmap = Bitmap.createBitmap(480, 720, Bitmap.Config.ARGB_8888)
        try {
            Canvas(bitmap).drawColor(Color.BLACK)
            source.outputStream().use { stream ->
                assertTrue("The source image could not be written", bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream))
            }
        } finally {
            bitmap.recycle()
        }

        val metrics = readFynxCameraMediaMetrics(Uri.fromFile(source), "image")
        assertEquals("Overlay geometry uses the source photo aspect ratio", 480f / 720f, metrics.aspectRatio, 0.01f)

        val exported = renderFynxPhotoTextOverlays(
            context,
            Uri.fromFile(source),
            listOf(
                FynxCameraTextOverlay(
                    id = "camera-overlay-test",
                    text = "FYNX TEXT TEST",
                    x = 0.5f,
                    y = 0.5f,
                    color = Color.WHITE,
                    font = "Modern",
                    bold = true
                )
            )
        )
        val outputFile = File(exported.path ?: error("Text export returned no file path"))
        val rendered = BitmapFactory.decodeFile(outputFile.absolutePath)
            ?: error("Text export could not be decoded")
        try {
            var brightPixels = 0
            var y = 0
            while (y < rendered.height) {
                var x = 0
                while (x < rendered.width) {
                    if (Color.red(rendered.getPixel(x, y)) > 210 &&
                        Color.green(rendered.getPixel(x, y)) > 210 &&
                        Color.blue(rendered.getPixel(x, y)) > 210
                    ) brightPixels++
                    x += 2
                }
                y += 2
            }
            assertTrue("Photo export did not burn the text overlay into the image", brightPixels > 20)
        } finally {
            rendered.recycle()
            source.delete()
            outputFile.delete()
        }
    }
}
