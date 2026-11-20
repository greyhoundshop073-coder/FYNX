package com.fynx.app.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream

/**
 * Saves evidence screenshots from instrumentation tests.
 *
 * Compose's node capture can fail on emulators for screens hosted in a separate
 * window (dialogs, sheets) or before a frame is drawn, throwing
 * "Failed to capture a node to bitmap." That is a capture problem, not a product
 * defect, and every behavioural assertion in the test has already run by then.
 *
 * Order of attempts:
 *  1. the caller's node capture,
 *  2. a full-screen UiAutomation screenshot (also covers dialog windows).
 * If both fail the failure is logged and recorded in `screenshot-failures.txt`
 * next to the screenshots; it is never silent, but it does not fail a test whose
 * assertions passed.
 */
internal object FynxTestScreenshots {
    private const val TAG = "FynxTestScreenshots"

    fun save(directory: File, name: String, captureNode: () -> Bitmap): Boolean {
        val bitmap: Bitmap? = try {
            captureNode()
        } catch (nodeError: Throwable) {
            Log.w(TAG, "Node capture failed for $name; trying full-screen capture: ${nodeError.message}")
            try {
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            } catch (screenError: Throwable) {
                recordFailure(directory, name, "node: ${nodeError.message}; screen: ${screenError.message}")
                null
            }
        }
        if (bitmap == null) {
            recordFailure(directory, name, "no bitmap produced")
            return false
        }
        val file = File(directory, name)
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        val saved = file.isFile && file.length() > 0L
        if (!saved) recordFailure(directory, name, "PNG was not written")
        return saved
    }

    private fun recordFailure(directory: File, name: String, reason: String) {
        Log.e(TAG, "SCREENSHOT NOT SAVED: $name ($reason)")
        try {
            File(directory, "screenshot-failures.txt").appendText("$name: $reason\n")
        } catch (ignored: Throwable) {
            // Logging above is the fallback; recording must never throw.
        }
    }
}
