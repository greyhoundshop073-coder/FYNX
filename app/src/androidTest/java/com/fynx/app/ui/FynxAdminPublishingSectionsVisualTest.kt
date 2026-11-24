package com.fynx.app.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Read-only emulator screenshots of the Admin publishing sections and local Marketplace banner. */
class FynxAdminPublishingSectionsVisualTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @After
    fun clearTransportFixture() {
        FynxBackendClient.requestExecutorForTests = null
    }


    @Test
    fun marketplaceBannerShowsBundledOfflineCampaignArtworkAndCapturesIt() {
        val campaigns = FynxMarketplaceLocalCampaignStore.active(compose.activity)
        check(campaigns.any { it.id == "local-sample-fynx-marketplace" }) {
            "The replaceable local FYNX sample campaign must be seeded without a backend."
        }
        compose.setContent {
            MaterialTheme {
                MarketplacePromotionalBanner(
                    listings = emptyList(),
                    sellerReputations = emptyMap(),
                    weeklySeller = null,
                    weeklySellerListing = null,
                    sponsoredPromotions = emptyList(),
                    seasonalPromotions = campaigns,
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
        }
        compose.onNodeWithText("Discover more on FYNX").assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                compose.onNodeWithContentDescription("FYNX Marketplace campaign artwork").assertIsDisplayed()
            }.isSuccess
        }
        capture("FYNX-marketplace-offline-banner.png")
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
            val directory = File(compose.activity.getExternalFilesDir(null), "admin-test-fixtures")
            check(directory.mkdirs() || directory.isDirectory) { "Cannot create Admin screenshot directory" }
            FileOutputStream(File(directory, fileName)).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "PNG encode failed" }
            }
        } finally {
            bitmap.recycle()
            thread.quitSafely()
        }
        android.util.Log.i("FYNXAdminVisualTest", "Captured $fileName")
    }
}
