package com.fynx.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FynxMarketplaceCampaignNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun seasonalCampaignCtasOpenExactProductCategoryAndShopTargets() {
        val currentCampaign = mutableStateOf(promotion("PRODUCT", " 42 ", "Open product"))
        var openedDestination: String? = null

        compose.setContent {
            FynxTheme(accent = FynxAccent.Blue, darkMode = false) {
                MarketplacePromotionalBanner(
                    listings = emptyList(),
                    sellerReputations = emptyMap(),
                    weeklySeller = null,
                    weeklySellerListing = null,
                    sponsoredPromotions = emptyList(),
                    seasonalPromotions = listOf(currentCampaign.value),
                    onOpenListing = { openedDestination = "LISTING:${it.id}" },
                    onOpenSeller = { openedDestination = "SHOP:$it" },
                    onOpenProduct = { openedDestination = "PRODUCT:$it" },
                    onOpenCategory = { openedDestination = "CATEGORY:$it" },
                    onShopNow = { openedDestination = "MARKETPLACE" },
                    onOpenOrders = {},
                    onRefresh = {},
                    autoRotate = false,
                    includeCalendarCelebrations = false
                )
            }
        }

        val destinations = listOf(
            Triple(promotion("PRODUCT", " 42 ", "Open product"), "Open product", "PRODUCT:42"),
            Triple(promotion("CATEGORY", "electronics", "Open category"), "Open category", "CATEGORY:Electronics"),
            Triple(promotion("SHOP", "@seller_one", "Open shop"), "Open shop", "SHOP:seller_one"),
            Triple(promotion("MARKETPLACE", "", "Explore Marketplace"), "Explore Marketplace", "MARKETPLACE")
        )

        destinations.forEach { (campaign, actionLabel, expectedDestination) ->
            compose.runOnIdle {
                currentCampaign.value = campaign
                openedDestination = null
            }
            compose.waitForIdle()
            // The carousel loops its first slide; select its first visible CTA.
            compose.onAllNodesWithText(actionLabel)[0].performClick()
            compose.runOnIdle {
                assertEquals(expectedDestination, openedDestination)
            }
        }
    }

    private fun promotion(
        destinationType: String,
        destinationValue: String,
        actionLabel: String
    ) = FynxMarketplaceSeasonalPromotion(
        id = "campaign-${destinationType.lowercase()}",
        promotionType = "OFFER",
        occasionKey = "cta-${destinationType.lowercase()}",
        headline = "Campaign $destinationType destination",
        body = "Open the intended FYNX Marketplace destination.",
        actionLabel = actionLabel,
        artworkMediaId = "",
        startsAt = "2026-01-01T00:00:00Z",
        endsAt = "2026-12-31T23:59:59Z",
        active = true,
        destinationType = destinationType,
        destinationValue = destinationValue
    )
}
