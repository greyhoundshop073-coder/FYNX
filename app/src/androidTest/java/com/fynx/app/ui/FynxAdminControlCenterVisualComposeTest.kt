package com.fynx.app.ui

import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Captures the existing production Owner/Admin Control Center composable on an
 * Android emulator. Responses are isolated, read-only instrumentation fixtures;
 * no real account, database, or backend credential is used.
 */
class FynxAdminControlCenterVisualComposeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @After
    fun clearTransportFixture() {
        FynxBackendClient.requestExecutorForTests = null
    }

    @Test
    fun capturesOwnerControlCenterOverview() {
        captureOverview("OWNER")
    }

    @Test
    fun capturesAdminControlCenterOverview() {
        captureOverview("ADMIN")
    }

    private fun captureFynxWindow(): Bitmap {
        val window = compose.activity.window
        val decor = window.decorView
        check(decor.width > 0 && decor.height > 0) {
            "FYNX Admin window has no laid-out content to capture"
        }
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        val thread = HandlerThread("FYNX-admin-window-capture").apply { start() }
        val latch = CountDownLatch(1)
        var copyResult = PixelCopy.ERROR_UNKNOWN
        try {
            PixelCopy.request(window, bitmap, { result ->
                copyResult = result
                latch.countDown()
            }, Handler(thread.looper))
            check(latch.await(15, TimeUnit.SECONDS)) {
                "Timed out capturing the FYNX Admin app window"
            }
            check(copyResult == PixelCopy.SUCCESS) {
                "PixelCopy could not capture the FYNX Admin app window; result=$copyResult"
            }
            return bitmap
        } catch (failure: Throwable) {
            bitmap.recycle()
            throw failure
        } finally {
            thread.quitSafely()
        }
    }

    private fun captureOverview(role: String) {
        val requestedPaths = Collections.synchronizedList(mutableListOf<String>())
        FynxBackendClient.requestExecutorForTests = { _, method, path, _, _ ->
            assertEquals(
                "Admin visual fixtures must never issue a mutation",
                "GET",
                method
            )
            requestedPaths += path
            when {
                path == "/api/admin/dashboard" ->
                    """{"role":"$role","counts":{"users":1284,"openReports":14,"openAppeals":3,"safetyActions24h":9}}"""

                path == "/api/admin/admins" ->
                    """{"admins":[{"id":"42","username":"fynx_support_lead","displayName":"FYNX Support Lead","grantedAt":"2026-11-23T10:00:00Z"}]}"""

                path == "/api/admin/revenue" ->
                    """{"role":"$role","breakdown":[{"source":"MARKETPLACE_FEE","status":"SETTLED","currency":"NGN","transactions":7,"amount":245000.0},{"source":"MARKETPLACE_FEE","status":"PENDING","currency":"NGN","transactions":2,"amount":32000.0},{"source":"MARKETPLACE_FEE","status":"REFUNDED","currency":"USD","transactions":1,"amount":15.0}]}"""

                path == "/api/admin/marketplace/protection/cases" ->
                    """{"cases":[]}"""

                path == "/api/admin/marketplace/drivers" ->
                    """{"drivers":[]}"""

                path == "/api/support/access" ->
                    """{"role":"$role","supportAccess":true}"""

                path.startsWith("/api/admin/support/cases") ->
                    """{"cases":[],"counts":{"open":0,"in_review":0,"waiting_user":0,"resolved":0,"unassigned":0}}"""

                path == "/api/admin/support/admins" ->
                    """{"admins":[]}"""

                path == "/api/admin/social/music/catalogue" ->
                    """{"tracks":[]}"""

                path == "/api/admin/marketplace/seasonal-promotions" ->
                    """{"promotions":[]}"""

                else -> """{}"""
            }
        }

        compose.setContent {
            FynxTheme(accent = FynxAccent.Blue, darkMode = false) {
                FynxAdminControlCenterPanel()
            }
        }

        compose.waitUntil(timeoutMillis = 20_000) {
            requestedPaths.contains("/api/admin/dashboard") &&
                requestedPaths.contains("/api/admin/revenue") &&
                requestedPaths.contains("/api/support/access") &&
                requestedPaths.contains("/api/admin/support/cases") &&
                requestedPaths.contains("/api/admin/marketplace/seasonal-promotions") &&
                runCatching {
                    compose.onNodeWithText(role + " ACCOUNT").assertIsDisplayed()
                }.isSuccess
        }

        compose.onNodeWithText("FYNX CONTROL CENTER").assertIsDisplayed()
        compose.onNodeWithText(role + " ACCOUNT").assertIsDisplayed()
        compose.onNodeWithText("Open reports").assertIsDisplayed()
        compose.onNodeWithText("Overview").assertIsDisplayed()

        assertTrue("Dashboard fixture was not requested", requestedPaths.contains("/api/admin/dashboard"))
        assertTrue("Revenue fixture was not requested", requestedPaths.contains("/api/admin/revenue"))
        assertTrue("Support fixture was not requested", requestedPaths.contains("/api/support/access"))

        // Capture the app's own Window surface rather than the whole emulator
        // display. System-owned dialogs (for example Pixel Launcher ANR prompts)
        // are separate windows and must not be included in FYNX visual evidence.
        // Save into shared Pictures media storage because connected tests uninstall
        // the target app after instrumentation.
        val fileName = if (role == "OWNER") {
            "FYNX-admin-owner-overview.png"
        } else {
            "FYNX-admin-admin-overview.png"
        }
        val bitmap = captureFynxWindow()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FYNX")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = compose.activity.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create shared media entry for " + fileName)
        try {
            val output = resolver.openOutputStream(uri)
                ?: error("Could not open shared media output for " + fileName)
            output.use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (failure: Throwable) {
            resolver.delete(uri, null, null)
            throw failure
        } finally {
            bitmap.recycle()
        }
        android.util.Log.i(
            "FYNXAdminVisualTest",
            "Captured " + role + " Control Center screenshot into shared media: " + uri
        )
    }
}
