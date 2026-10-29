package com.fynx.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * Device-side regression tests that run the actual local photo processors on a fixed portrait.
 * They assert that each processor writes a readable image with visible pixel changes.
 */
@RunWith(AndroidJUnit4::class)
class CameraPhotoEffectsInstrumentationTest {
    private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext get() = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun eachTrackedFaceEffectWritesAVisibleEdit() {
        withPortrait { pristine, original ->
            CameraFaceEffect.values().filter { it != CameraFaceEffect.OFF }.forEach { effect ->
                verifyEditedImage("Face FX: ${effect.label}", pristine, original) {
                    applyCameraFaceEffectToPhotoFile(it, effect)
                }
            }
        }
    }

    @Test
    fun eachBackgroundEffectWritesAVisibleEdit() {
        withPortrait { pristine, original ->
            CameraBackgroundEffect.values().filter { it != CameraBackgroundEffect.OFF }.forEach { effect ->
                verifyEditedImage("Background: ${effect.label}", pristine, original) {
                    applyCameraBackgroundEffectToPhotoFile(it, effect)
                }
            }
        }
    }

    @Test
    fun eachStudioEffectWritesAVisibleEdit() {
        withPortrait { pristine, original ->
            CameraStudioEffect.values().filter { it != CameraStudioEffect.OFF }.forEach { effect ->
                verifyEditedImage("AI Studio: ${effect.label}", pristine, original) {
                    applyCameraStudioEffectToPhotoFile(it, effect)
                }
            }
        }
    }

    @Test
    fun faceEffectRejectsAnImageWithoutAFaceAndKeepsTheSourceReadable() {
        val source = File.createTempFile("fynx-camera-no-face-", ".jpg", targetContext.cacheDir)
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        try {
            // Solid colour contains no detectable facial features.
            bitmap.eraseColor(Color.rgb(80, 120, 150))
            source.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it)) }
            val beforeBytes = source.readBytes()
            val failure = runCatching {
                applyCameraFaceEffectToPhotoFile(source, CameraFaceEffect.CUTE_EARS)
            }.exceptionOrNull()
            assertNotNull("A face effect must clearly reject a photo without a detected face", failure)
            assertTrue("Failed face detection must leave a readable original file", source.length() > 1024)
            val unchanged = BitmapFactory.decodeFile(source.absolutePath)
            assertNotNull("The original must still decode after a rejected effect", unchanged)
            unchanged.recycle()
            // The processor checks for a face before saving output; file content must remain unchanged.
            assertTrue("Failed face detection unexpectedly rewrote the image", source.readBytes().contentEquals(beforeBytes))
        } finally {
            bitmap.recycle()
            source.delete()
        }
    }

    private fun withPortrait(block: (File, Bitmap) -> Unit) {
        val pristine = File.createTempFile("fynx-camera-fixture-", ".jpg", targetContext.cacheDir)
        val original = testContext.assets.open("camera_test_portrait.jpg").use { input ->
            pristine.outputStream().use { output -> input.copyTo(output) }
            BitmapFactory.decodeFile(pristine.absolutePath)
        }
        assertNotNull("Bundled portrait fixture must decode", original)
        try {
            block(pristine, original)
        } finally {
            original.recycle()
            pristine.delete()
        }
    }

    private fun verifyEditedImage(
        label: String,
        pristine: File,
        original: Bitmap,
        transform: (File) -> Unit
    ) {
        val editedFile = File.createTempFile("fynx-camera-edited-", ".jpg", targetContext.cacheDir)
        try {
            pristine.copyTo(editedFile, overwrite = true)
            transform(editedFile)
            assertTrue("$label did not save an image", editedFile.isFile && editedFile.length() > 1024L)
            val edited = BitmapFactory.decodeFile(editedFile.absolutePath)
            assertNotNull("$label output is not a decodable image", edited)
            try {
                assertEquals("$label changed width unexpectedly", original.width, edited.width)
                assertEquals("$label changed height unexpectedly", original.height, edited.height)
                val changedSamples = countStronglyChangedSamples(original, edited)
                assertTrue("$label had no visible edit (strongly changed sample count=$changedSamples)", changedSamples > 4)
            } finally {
                edited.recycle()
            }
        } finally {
            editedFile.delete()
        }
    }

    private fun countStronglyChangedSamples(before: Bitmap, after: Bitmap): Int {
        val width = minOf(before.width, after.width)
        val height = minOf(before.height, after.height)
        val stepX = max(1, width / 120)
        val stepY = max(1, height / 120)
        var count = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val a = before.getPixel(x, y)
                val b = after.getPixel(x, y)
                val difference =
                    abs(Color.red(a) - Color.red(b)) +
                    abs(Color.green(a) - Color.green(b)) +
                    abs(Color.blue(a) - Color.blue(b))
                if (difference > 35) count++
                x += stepX
            }
            y += stepY
        }
        return count
    }
}
