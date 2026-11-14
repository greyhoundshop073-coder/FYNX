package com.fynx.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * TEST-ONLY fixture. It is passed directly to the production MarketplaceReferenceProductCard
 * composable; it is never inserted into the app catalogue, backend, cart persistence or account.
 */
class FynxMarketplaceProductCardComposeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun productionCardRendersFixtureAndInvokesFavoriteCartAndOpenCallbacks() {
        var opened = 0
        var favorited = 0
        var addedToCart = 0

        val fixture = FynxRemoteSocialClient.MarketplaceListing(
            id = "ui-test-listing-001",
            sellerId = "ui-test-seller-001",
            sellerUsername = "test_seller",
            sellerDisplayName = "Test Seller",
            storeName = "FYNX UI Test Store",
            title = "UI Test Wireless Headphones",
            description = "Test-only card fixture; not a real listing.",
            price = 80_000.0,
            currency = "NGN",
            category = "Electronics",
            condition = "NEW",
            quantity = 4,
            location = "Port Harcourt, Rivers",
            deliveryAvailable = true,
            pickupAvailable = true,
            deliveryFee = 0.0,
            mediaIds = emptyList(),
            active = true,
            originalPrice = 100_000.0,
            productAverageRating = 4.7,
            productReviewCount = 1_200,
            priceDrop = true
        )

        compose.setContent {
            MaterialTheme {
                // The production card is taller than a phone viewport. Give the test host
                // a real scroll container so off-screen metadata and actions can be exercised.
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    MarketplaceReferenceProductCard(
                        listing = fixture,
                        onOpen = { opened++ },
                        onProfile = {},
                        isFavorite = false,
                        onFavorite = { favorited++ },
                        onAddToCart = { addedToCart++ },
                        isWeeklyBestSeller = true,
                        isSpecialOffer = true
                    )
                }
            }
        }

        compose.onNodeWithText("UI Test Wireless Headphones").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("₦80,000").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("₦100,000").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("4.7").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("(1.2k reviews)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("FYNX UI Test Store").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Port Harcourt, Rivers").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Free Delivery", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Local pickup", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Best Seller").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Special Offer").performScrollTo().assertIsDisplayed()

        compose.onNodeWithContentDescription("Save product").performScrollTo().performClick()
        compose.onNodeWithText("Add to Cart").performScrollTo().performClick()
        compose.onNodeWithText("UI Test Wireless Headphones").performClick()

        compose.runOnIdle {
            assertEquals(1, favorited)
            assertEquals(1, addedToCart)
            assertEquals(1, opened)
        }
    }
}
