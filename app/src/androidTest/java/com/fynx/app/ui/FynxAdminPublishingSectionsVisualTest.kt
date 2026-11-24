package com.fynx.app.ui

import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.time.ZoneId
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Offline Owner/Admin UI verification with real PixelCopy evidence and no live publishing writes. */
class FynxAdminPublishingSectionsVisualTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @After
    fun clearTransportFixture() {
        FynxBackendClient.requestExecutorForTests = null
    }


    @Test
    fun adminCampaignEditorSavesSelectedPhotoOfflineAndFeedsMarketplaceBanner() {
        val resolver = compose.activity.contentResolver
        val artworkUri = createFixtureArtworkUri()
        val occasionKey = "admin-ui-" + System.currentTimeMillis()
        val headline = "Admin UI saved offline campaign"
        val now = OffsetDateTime.now(ZoneId.of("Africa/Lagos"))
        val startsAt = now.minusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val endsAt = now.plusDays(3).withHour(18).withMinute(0).withSecond(0).withNano(0)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val methods = mutableListOf<String>()

        // The local Save path is not allowed to send a write request to the backend.
        FynxBackendClient.requestExecutorForTests = { _, method, path, _, _ ->
            methods += method + " " + path
            assertEquals("Offline Admin campaign editor must not write to a server", "GET", method)
            when (path) {
                "/api/admin/marketplace/seasonal-promotions" -> """{"promotions":[]}"""
                else -> """{}"""
            }
        }

        try {
            val screenState = mutableStateOf(0)
            compose.setContent {
                MaterialTheme {
                    when (screenState.value) {
                        0 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            FynxMarketplaceSeasonalPromotionAdminPanel(
                                testOnlyPhotoPicker = { artworkUri },
                                testOnlyStartsAt = startsAt,
                                testOnlyEndsAt = endsAt
                            )
                        }
                        1 -> {
                            val campaigns = FynxMarketplaceLocalCampaignStore.observe(compose.activity).collectAsState()
                            MarketplacePromotionalBanner(
                                listings = emptyList(),
                                sellerReputations = emptyMap(),
                                weeklySeller = null,
                                weeklySellerListing = null,
                                sponsoredPromotions = emptyList(),
                                seasonalPromotions = campaigns.value,
                                onOpenListing = {},
                                onOpenSeller = {},
                                onOpenProduct = {},
                                onOpenCategory = {},
                                onShopNow = {},
                                onOpenOrders = {},
                                onRefresh = {},
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            FynxMarketplaceSeasonalPromotionAdminPanel()
                        }
                    }
                }
            }
            compose.onNodeWithText("Choose photo").performScrollTo().performClick()
            compose.onNodeWithText("Selected photo:", substring = true).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Campaign key").performTextInput(occasionKey)
            compose.onNodeWithText("Banner headline").performTextInput(headline)
            compose.onNodeWithText("Celebration write-up")
                .performTextInput("A real Admin form save, stored locally with selected artwork.")
            compose.onNodeWithText("Publish on this phone").performScrollTo().performClick()

            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText(
                    "Saved on this phone. The campaign is now available in the Marketplace banner without a backend connection."
                ).fetchSemanticsNodes().isNotEmpty()
            }

            val saved = FynxMarketplaceLocalCampaignStore.all(compose.activity)
                .firstOrNull { it.occasionKey == occasionKey }
                ?: error("The Admin campaign form did not persist the new local campaign.")
            assertEquals(true, saved.localOnly)
            assertEquals(true, saved.active)
            assertEquals(headline, saved.headline)
            assertTrue("Selected artwork must be copied into app-private storage", saved.localArtworkUri.startsWith("file:"))
            val privateArtwork = File(Uri.parse(saved.localArtworkUri).path.orEmpty())
            assertTrue("Copied campaign artwork must exist and be non-empty", privateArtwork.isFile && privateArtwork.length() > 0L)
            assertTrue("Local save may make GET refreshes only", methods.all { it.startsWith("GET ") })

            // Verify the saved campaign is actually consumed by the Marketplace banner.
            compose.runOnIdle { screenState.value = 1 }
            compose.waitUntil(timeoutMillis = 10_000) {
                runCatching {
                    compose.onNodeWithText(headline).assertIsDisplayed()
                    compose.onNodeWithContentDescription("FYNX Marketplace campaign artwork").assertIsDisplayed()
                }.isSuccess
            }
            capture("FYNX-marketplace-offline-banner.png")

            // Reopening the real campaign editor must reload the saved campaign from storage.
            compose.runOnIdle { screenState.value = 2 }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText(headline).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(headline).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("LOCAL · THIS DEVICE").performScrollTo().assertIsDisplayed()
        } finally {
            FynxMarketplaceLocalCampaignStore.all(compose.activity)
                .filter { it.occasionKey == occasionKey }
                .forEach { FynxMarketplaceLocalCampaignStore.delete(compose.activity, it.id).getOrThrow() }
            resolver.delete(artworkUri, null, null)
        }
    }

    private fun createFixtureArtworkUri(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "fynx-admin-test-" + System.currentTimeMillis() + ".png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FYNXAdminTests")
        }
        val uri = compose.activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create the isolated MediaStore image fixture.")
        try {
            val bitmap = Bitmap.createBitmap(128, 72, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.rgb(0, 127, 255))
            }
            try {
                val stream = compose.activity.contentResolver.openOutputStream(uri)
                    ?: error("Could not open the isolated MediaStore image fixture.")
                stream.use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        "Could not write the isolated campaign artwork fixture."
                    }
                }
            } finally {
                bitmap.recycle()
            }
            return uri
        } catch (error: Throwable) {
            compose.activity.contentResolver.delete(uri, null, null)
            throw error
        }
    }

    @Test
    fun ownerCanReachMusicLibraryAndCaptureIt() {
        launchAs("OWNER")
        compose.onNodeWithText("Music Library").performScrollTo()
        compose.onNodeWithText("Music Library").performClick()
        compose.onNodeWithText("Official FYNX Music Catalogue").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Select audio").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Song title").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Publish to FYNX").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Official FYNX Music Catalogue").performScrollTo()
        capture("FYNX-admin-owner-music-library.png")
    }

    @Test
    fun ownerCanReachMarketplaceCampaignManagerAndCaptureIt() {
        launchAs("OWNER")
        compose.onNodeWithText("Marketplace").performScrollTo().performClick()
        compose.onNodeWithText("Marketplace banner campaigns").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Campaign type").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Banner headline").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Publish on this phone").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Campaign artwork (optional)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Choose photo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Marketplace banner campaigns").performScrollTo()
        capture("FYNX-admin-owner-marketplace-campaigns.png")
    }

    @Test
    fun adminCanReachMarketplaceCampaignManagerAndCaptureIt() {
        launchAs("ADMIN")
        check(compose.onAllNodesWithText("Music Library").fetchSemanticsNodes().isEmpty()) { "ADMIN must not see Owner-only Music Library navigation" }
        compose.onNodeWithText("Marketplace").performScrollTo().performClick()
        compose.onNodeWithText("Marketplace banner campaigns").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Publish on this phone").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Campaign artwork (optional)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Choose photo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Marketplace banner campaigns").performScrollTo()
        capture("FYNX-admin-admin-marketplace-campaigns.png")
    }

    @Test
    fun ownerCanCaptureEveryAdminControlCenterSection() {
        launchAs("OWNER")

        // Overview is captured once at the top for the account role and platform totals,
        // then again at the revenue ledger so the whole dashboard is represented.
        compose.onNodeWithText("FYNX CONTROL CENTER").assertIsDisplayed()
        capture("FYNX-admin-owner-overview.png")
        compose.onNodeWithText("FYNX Business Revenue").performScrollTo().assertIsDisplayed()
        capture("FYNX-admin-owner-overview-revenue.png")

        compose.onNodeWithText("Safety").performScrollTo().performClick()
        compose.onNodeWithText("Reported accounts").performScrollTo().assertIsDisplayed()
        capture("FYNX-admin-owner-safety-account-reports.png")
        compose.onNodeWithText("Content reports").performScrollTo().assertIsDisplayed()
        capture("FYNX-admin-owner-safety-content-reports.png")

        compose.onNodeWithText("Marketplace").performScrollTo().performClick()
        compose.onNodeWithText("Marketplace protection cases").performScrollTo().assertIsDisplayed()
        capture("FYNX-admin-owner-marketplace-protection.png")

        compose.onNodeWithText("People").performScrollTo().performClick()
        compose.onNodeWithText("Current Admins").performScrollTo().assertIsDisplayed()
        capture("FYNX-admin-owner-people-admins.png")
        compose.onNodeWithText("Manage an account").performScrollTo().assertIsDisplayed()
        capture("FYNX-admin-owner-people-account-management.png")

        compose.onNodeWithText("Announcements").performScrollTo().performClick()
        compose.onNodeWithText("Create an official update").performScrollTo().assertIsDisplayed()
        capture("FYNX-admin-owner-announcement-editor.png")
        compose.onNodeWithText("Manage announcements").performScrollTo().assertIsDisplayed()
        capture("FYNX-admin-owner-announcement-management.png")

        compose.onNodeWithText("Activity").performScrollTo().performClick()
        compose.onNodeWithText("Administrative activity").performScrollTo().assertIsDisplayed()
        capture("FYNX-admin-owner-activity.png")
    }

    private fun launchAs(role: String) {
        val requested = mutableSetOf<String>()
        FynxBackendClient.requestExecutorForTests = { _, method, path, _, _ ->
            assertEquals("Visual fixtures must be read-only", "GET", method)
            requested += path
            when (path) {
                "/api/admin/dashboard" ->
                    """{"role":"$role","counts":{"users":1284,"openReports":14,"openAppeals":3,"safetyActions24h":9}}"""
                "/api/admin/admins" -> """{"admins":[]}"""
                "/api/admin/revenue" -> """{"role":"$role","breakdown":[]}"""
                "/api/admin/marketplace/protection/cases" -> """{"cases":[]}"""
                "/api/admin/marketplace/drivers" -> """{"drivers":[]}"""
                "/api/admin/social/music/catalogue" -> """{"tracks":[]}"""
                "/api/admin/marketplace/seasonal-promotions" -> """{"promotions":[]}"""
                "/api/support/access" -> """{"role":"$role","supportAccess":true}"""
                else -> """{}"""
            }
        }
        compose.setContent {
            MaterialTheme {
                FynxAdminControlCenterPanel()
            }
        }
        compose.waitUntil(timeoutMillis = 20_000) {
            requested.contains("/api/admin/dashboard") &&
                runCatching { compose.onNodeWithText("$role ACCOUNT").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithText("FYNX CONTROL CENTER").assertIsDisplayed()
    }

    private fun capture(fileName: String) {
        val window = compose.activity.window
        val decor = window.decorView
        check(decor.width > 0 && decor.height > 0) { "FYNX screen has no laid-out content" }
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        val thread = HandlerThread("FYNX-admin-section-capture").apply { start() }
        val latch = CountDownLatch(1)
        var result = PixelCopy.ERROR_UNKNOWN
        try {
            PixelCopy.request(window, bitmap, { status ->
                result = status
                latch.countDown()
            }, Handler(thread.looper))
            check(latch.await(15, TimeUnit.SECONDS)) { "Timed out capturing $fileName" }
            check(result == PixelCopy.SUCCESS) { "PixelCopy failed for $fileName: $result" }
            require(fileName.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.png"))) {
                "Unexpected Admin screenshot filename: " + fileName
            }
            // Store the authentic PixelCopy image in public MediaStore Pictures. This survives
            // Gradle uninstalling the target APK after connectedDebugAndroidTest, and adb can
            // pull the directory without relying on a shell copy from an app-scoped folder.
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/FYNXAdminEvidence")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = compose.activity.contentResolver
            val screenshotUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create MediaStore evidence entry for " + fileName)
            try {
                val output = resolver.openOutputStream(screenshotUri, "w")
                    ?: error("Could not open MediaStore evidence output for " + fileName)
                output.use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "PNG encode failed for " + fileName }
                }
                val publish = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                check(resolver.update(screenshotUri, publish, null, null) == 1) {
                    "Could not publish screenshot into shared Pictures for " + fileName
                }
                val size = resolver.query(
                    screenshotUri,
                    arrayOf(MediaStore.MediaColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                } ?: 0L
                check(size > 0L) { "MediaStore screenshot is empty: " + fileName }
            } catch (failure: Throwable) {
                resolver.delete(screenshotUri, null, null)
                throw failure
            }
            android.util.Log.i("FYNXAdminVisualTest", "Captured PixelCopy screenshot in shared Pictures: " + fileName)
        } finally {
            bitmap.recycle()
            thread.quitSafely()
        }
        android.util.Log.i("FYNXAdminVisualTest", "Captured $fileName")
    }
}
