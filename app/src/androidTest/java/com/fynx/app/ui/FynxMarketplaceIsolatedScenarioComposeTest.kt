package com.fynx.app.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId

/**
 * Runs real FYNX production Marketplace composables against data compiled only
 * into the androidTest APK. The fixture catalogue is never supplied to production clients.
 */
class FynxMarketplaceIsolatedScenarioComposeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun isolatedCatalogueExercisesSearchPaginationCartFeeAndWalletReconciliation() {
        val all = FynxMarketplaceIsolatedTestFixtures.listings
        assertEquals(28, all.size)
        assertEquals(8, FynxMarketplaceIsolatedTestFixtures.page(limit = 8).size)
        assertEquals(8, FynxMarketplaceIsolatedTestFixtures.page(offset = 8, limit = 8).size)
        assertTrue(
            FynxMarketplaceIsolatedTestFixtures.page(offset = 0, limit = 8)
                .map { it.id }.intersect(
                    FynxMarketplaceIsolatedTestFixtures.page(offset = 8, limit = 8).map { it.id }.toSet()
                ).isEmpty()
        )
        assertTrue(FynxMarketplaceIsolatedTestFixtures.searchAndFilter("Wireless").isNotEmpty())
        assertTrue(FynxMarketplaceIsolatedTestFixtures.searchAndFilter(category = "Electronics").isNotEmpty())
        assertTrue(FynxMarketplaceIsolatedTestFixtures.listings.map { it.currency }.containsAll(listOf("NGN", "USD")))

        val lines = listOf(
            FynxMarketplaceIsolatedTestFixtures.CartLine(all[0].id, all[0].price, 2),
            FynxMarketplaceIsolatedTestFixtures.CartLine(all[1].id, all[1].price, 1)
        )
        assertEquals(all[0].price * 2 + all[1].price, FynxMarketplaceIsolatedTestFixtures.cartTotal(lines), 0.001)
        assertEquals(1_250.0, FynxMarketplaceIsolatedTestFixtures.marketplaceFee(25_000.0), 0.001)
        assertTrue("TEST MODE fixture balances must reconcile", FynxMarketplaceIsolatedTestFixtures.wallet.isReconciled())
        assertTrue(FynxMarketplaceIsolatedTestFixtures.wallet.testMode)
        assertTrue(FynxMarketplaceIsolatedTestFixtures.wallet.transactions.all { it.status.contains("TEST ONLY") })
        assertTrue(FynxMarketplaceIsolatedTestFixtures.sampleReviews.all { it.testOnly })
        assertEquals("fynx_test_buyer", FynxMarketplaceIsolatedTestFixtures.buyer.username)
        assertEquals("fynx_test_seller", FynxMarketplaceIsolatedTestFixtures.shop.ownerUsername)
        assertTrue(FynxMarketplaceIsolatedTestFixtures.localBusiness.description.contains("test-only", ignoreCase = true))
    }

    @Test
    fun productionGreatDealsAndProductCardsRenderFixtureAndPreserveExactListingIdentity() {
        val dealListings = FynxMarketplaceIsolatedTestFixtures.listings.take(6)
        val cardListings = FynxMarketplaceIsolatedTestFixtures.listings.drop(12).take(3)
        val candidates = selectMarketplaceAdvertisingCandidates(
            dealListings.map { listing ->
                MarketplaceAdvertisingCandidate(
                    id = listing.id,
                    sellerUsername = listing.sellerUsername,
                    title = listing.title,
                    price = listing.price,
                    currency = listing.currency,
                    quantity = listing.quantity,
                    active = listing.active
                )
            }
        )
        assertTrue("Great Deals fixture needs at least two eligible products", candidates.size >= 2)
        val expectedDealId = candidates[1].id
        val firstDeal = dealListings.first { it.id == candidates.first().id }
        val secondDeal = dealListings.first { it.id == expectedDealId }
        // Dated campaign slides can precede products; follow the real Marketplace order.
        val featuredSlideStartIndex = if (
            marketplaceCelebrationFor(LocalDate.now(ZoneId.of("Africa/Lagos"))) != null
        ) 1 else 0
        var openedDealId: String? = null
        var openedCardId: String? = null
        var favoriteActions = 0
        var cartActions = 0

        compose.setContent {
            MaterialTheme {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(FynxMarketplaceIsolatedTestFixtures.banner)
                    Text("BUYER TEST IDENTITY: @${FynxMarketplaceIsolatedTestFixtures.buyer.username}")
                    Text("SELLER TEST IDENTITY: @${FynxMarketplaceIsolatedTestFixtures.seller.username}")
                    Text("SHOP TEST DATA: ${FynxMarketplaceIsolatedTestFixtures.shop.name}")
                    Text("WALLET TEST MODE ONLY — no money is moved")
                    MarketplacePromotionalBanner(
                        listings = dealListings,
                        sellerReputations = emptyMap(),
                        weeklySeller = null,
                        weeklySellerListing = null,
                        sponsoredPromotions = emptyList(),
                        seasonalPromotions = emptyList(),
                        onOpenListing = { openedDealId = it.id },
                        onOpenSeller = {},
                        onOpenProduct = {},
                        onOpenCategory = {},
                        onShopNow = {},
                        onOpenOrders = {},
                        onRefresh = {},
                        autoRotate = false
                    )
                    cardListings.forEach { listing ->
                        MarketplaceReferenceProductCard(
                            listing = listing,
                            onOpen = { openedCardId = listing.id },
                            onProfile = {},
                            isFavorite = false,
                            onFavorite = { favoriteActions++ },
                            onAddToCart = { cartActions++ }
                        )
                    }
                    Text("LOCAL STORE TEST DATA: ${FynxMarketplaceIsolatedTestFixtures.localBusiness.name}")
                    Text("SIMULATED WALLET TEST TRANSACTIONS: ${FynxMarketplaceIsolatedTestFixtures.wallet.transactions.size}")
                }
            }
        }

        compose.onNodeWithText(FynxMarketplaceIsolatedTestFixtures.banner).assertIsDisplayed()
        compose.onNodeWithText("WALLET TEST MODE ONLY — no money is moved").assertIsDisplayed()
        compose.onNodeWithText("Discover on FYNX").performScrollTo().assertIsDisplayed()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("fynx-marketplace-promo-carousel").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("fynx-marketplace-promo-carousel").performScrollToIndex(featuredSlideStartIndex)
        compose.waitUntil(timeoutMillis = 8_000) {
            compose.onAllNodesWithText(firstDeal.title).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(firstDeal.title).assertIsDisplayed()
        saveScreenshot("marketplace-test-great-deals.png")

        // Select the next actual product slide after any dated campaign slide.
        compose.onNodeWithTag("fynx-marketplace-promo-carousel").performScrollToIndex(featuredSlideStartIndex + 1)
        compose.waitUntil(timeoutMillis = 3_000) {
            runCatching { compose.onNodeWithText(secondDeal.title).assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithText(secondDeal.title).assertIsDisplayed()
        // The whole production slide is clickable; tap its unique headline to prove that
        // the selected product identity, rather than a neighbouring card, is routed.
        compose.onNodeWithContentDescription("Shop this product: ${secondDeal.title}").performClick()
        compose.runOnIdle { assertEquals(expectedDealId, openedDealId) }

        compose.onAllNodesWithText("FYNX Test Product 13")[0].performScrollTo().assertIsDisplayed()
        saveScreenshot("marketplace-test-product-card.png")
        compose.onAllNodesWithContentDescription("Save product")[0].performScrollTo().performClick()
        compose.onAllNodesWithText("Add to Cart")[0].performScrollTo().performClick()
        compose.onAllNodesWithText("FYNX Test Product 13")[0].performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, favoriteActions)
            assertEquals(1, cartActions)
            assertEquals(FynxMarketplaceIsolatedTestFixtures.listings[12].id, openedCardId)
        }
    }

    @Test
    fun greatDealsAutomaticallyAdvanceFromTheFirstToTheNextEligibleProduct() {
        val dealListings = FynxMarketplaceIsolatedTestFixtures.listings.take(6)
        val candidates = selectMarketplaceAdvertisingCandidates(
            dealListings.map { listing ->
                MarketplaceAdvertisingCandidate(
                    id = listing.id,
                    sellerUsername = listing.sellerUsername,
                    title = listing.title,
                    price = listing.price,
                    currency = listing.currency,
                    quantity = listing.quantity,
                    active = listing.active
                )
            }
        )
        assertTrue(candidates.size >= 2)
        val featuredSlideStartIndex = if (
            marketplaceCelebrationFor(LocalDate.now(ZoneId.of("Africa/Lagos"))) != null
        ) 1 else 0
        compose.setContent {
            MaterialTheme {
                MarketplacePromotionalBanner(
                    listings = dealListings,
                    sellerReputations = emptyMap(),
                    weeklySeller = null,
                    weeklySellerListing = null,
                    sponsoredPromotions = emptyList(),
                    seasonalPromotions = emptyList(),
                    onOpenListing = {}, onOpenSeller = {}, onOpenProduct = {}, onOpenCategory = {},
                    onShopNow = {}, onOpenOrders = {}, onRefresh = {}
                )
            }
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("fynx-marketplace-promo-carousel").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("fynx-marketplace-promo-carousel").performScrollToIndex(featuredSlideStartIndex)
        compose.waitUntil(timeoutMillis = 8_000) {
            compose.onAllNodesWithText(candidates.first().title).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(candidates.first().title).assertIsDisplayed()
        // Rotation is 6.5 seconds in production. Assert the next real listing headline,
        // rather than a counter that the production promotional banner does not render.
        compose.waitUntil(timeoutMillis = 12_000) {
            runCatching { compose.onNodeWithText(candidates[1].title).assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithText(candidates[1].title).assertIsDisplayed()
    }

    @Test
    fun sellerActivityLabelsUseTheLastActiveTimestampNotListingAge() {
        val now = 2_000_000_000_000L
        assertEquals("Active just now", marketplaceSellerActivityLabel(now - 20_000L, now))
        assertEquals("Active 5 minutes ago", marketplaceSellerActivityLabel(now - 5L * 60_000L, now))
        assertEquals("Active 1 hour ago", marketplaceSellerActivityLabel(now - 60L * 60_000L, now))
        assertEquals("Active 2 hours ago", marketplaceSellerActivityLabel(now - 2L * 60L * 60L * 1000L, now))
        assertEquals("Active yesterday", marketplaceSellerActivityLabel(now - 24L * 60L * 60L * 1000L, now))
        assertEquals("Active 3 days ago", marketplaceSellerActivityLabel(now - 3L * 24L * 60L * 60L * 1000L, now))
        assertNull(marketplaceSellerActivityLabel(null, now))
        assertNull(marketplaceSellerActivityLabel(0L, now))
        assertNull(marketplaceSellerActivityLabel(now + 1L, now))
    }

    private fun saveScreenshot(name: String) {
        // Visual evidence is optional: screenshot capture must never fail a functional test.
        runCatching {
            val directory = File(
                compose.activity.getExternalFilesDir(null),
                "marketplace-test-fixtures"
            )
            check(directory.mkdirs() || directory.isDirectory)
            val file = File(directory, name)
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                ?: error("Could not capture the Android device display")
            FileOutputStream(file).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            check(file.isFile && file.length() > 0L)
        }.onFailure { error ->
            android.util.Log.w("FYNXMarketplaceTest", "Optional screenshot '$name' was not captured", error)
        }
    }
}
