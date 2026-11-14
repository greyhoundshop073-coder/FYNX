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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

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
        assertTrue(FynxMarketplaceIsolatedTestFixtures.localBusiness.description.contains("test-only"))
    }

    @Test
    fun productionGreatDealsAndProductCardsRenderFixtureAndPreserveExactListingIdentity() {
        val listings = FynxMarketplaceIsolatedTestFixtures.listings.take(6)
        val candidates = selectMarketplaceAdvertisingCandidates(
            listings.map { listing ->
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
        val expectedDealId = candidates.first().id
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
                        listings = listings,
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
                        onRefresh = {}
                    )
                    listings.drop(12).take(3).forEach { listing ->
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
        compose.onNodeWithText("Discover on FYNX").assertIsDisplayed()
        compose.onNodeWithText("WALLET TEST MODE ONLY — no money is moved").assertIsDisplayed()
        saveScreenshot("marketplace-test-great-deals.png")

        // The first real production banner CTA must route the exact selected listing ID.
        compose.onNodeWithText("Shop this product").performClick()
        compose.runOnIdle { assertEquals(expectedDealId, openedDealId) }

        compose.onNodeWithText("FYNX Test Product 13").performScrollTo().assertIsDisplayed()
        saveScreenshot("marketplace-test-product-card.png")
        compose.onNodeWithContentDescription("Save product").performScrollTo().performClick()
        compose.onNodeWithText("Add to Cart").performScrollTo().performClick()
        compose.onNodeWithText("FYNX Test Product 13").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, favoriteActions)
            assertEquals(1, cartActions)
            assertEquals(FynxMarketplaceIsolatedTestFixtures.listings[12].id, openedCardId)
        }
    }

    private fun saveScreenshot(name: String) {
        val directory = File(
            compose.activity.getExternalFilesDir(null),
            "marketplace-test-fixtures"
        )
        check(directory.mkdirs() || directory.isDirectory)
        val file = File(directory, name)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(file).use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        assertTrue(file.isFile && file.length() > 0L)
    }
}
