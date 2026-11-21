package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FynxMarketplaceProductCardPresentationTest {

    @Test
    fun reviewCountsStayCompactAndNeverInventAZeroReviewCount() {
        assertEquals("", marketplaceReviewCountLabel(0))
        assertEquals("", marketplaceReviewCountLabel(-3))
        assertEquals("856", marketplaceReviewCountLabel(856))
        assertEquals("1.2k", marketplaceReviewCountLabel(1_200))
        assertEquals("1m", marketplaceReviewCountLabel(1_000_000))
    }

    @Test
    fun productCardPricesUseRecognizableNairaAndDollarSymbols() {
        assertEquals("₦320,000", marketplaceCardPriceLabel("NGN", 320_000.0))
        assertEquals("\$180,000", marketplaceCardPriceLabel("USD", 180_000.0))
        assertEquals("EUR 25.50", marketplaceCardPriceLabel("EUR", 25.5))
    }

    @Test
    fun discountPercentRequiresARealHigherOriginalPriceAndRoundsCorrectly() {
        assertEquals(20, marketplaceDiscountPercent(80.0, 100.0))
        assertEquals(15, marketplaceDiscountPercent(85.0, 100.0))
        assertNull(marketplaceDiscountPercent(100.0, 100.0))
        assertNull(marketplaceDiscountPercent(101.0, 100.0))
        assertNull(marketplaceDiscountPercent(0.0, 100.0))
        assertNull(marketplaceDiscountPercent(80.0, null))
    }

    @Test
    fun conditionLabelsOnlyRecognizeSupportedListingConditions() {
        assertEquals("New", marketplaceConditionLabel("NEW"))
        assertEquals("Used", marketplaceConditionLabel("used"))
        assertEquals("Refurbished", marketplaceConditionLabel("REFURBISHED"))
        assertNull(marketplaceConditionLabel("unknown"))
    }

    @Test
    fun stockLabelsDistinguishUnavailableLowAndNormalStock() {
        assertEquals("Out of stock", marketplaceStockLabel(0))
        assertEquals("Out of stock", marketplaceStockLabel(-1))
        assertEquals("Only 5 left", marketplaceStockLabel(5))
        assertNull(marketplaceStockLabel(6))
    }

    @Test
    fun freeDeliveryRequiresAnExplicitZeroFee() {
        assertNull(marketplaceDeliveryLabel(false, 0.0))
        assertEquals("Free Delivery", marketplaceDeliveryLabel(true, 0.0))
        assertEquals("Delivery Available", marketplaceDeliveryLabel(true, 350.0))
        assertEquals("Delivery Available", marketplaceDeliveryLabel(true, null))
    }

    @Test
    fun verifiedAndSponsoredBadgesRequireExplicitServerEligibility() {
        assertNull(marketplaceVerifiedSellerBadge(false))
        assertEquals("Verified Seller", marketplaceVerifiedSellerBadge(true))
        assertNull(marketplaceSponsoredBadge(false))
        assertEquals("Sponsored", marketplaceSponsoredBadge(true))
    }

    @Test
    fun newArrivalUsesTheRealListingTimestampAndASevenDayWindow() {
        val now = 2_000_000_000_000L
        assertEquals(true, marketplaceIsNewArrival(now - 60_000L, now))
        assertEquals(true, marketplaceIsNewArrival(now - 7L * 24L * 60L * 60L * 1000L, now))
        assertEquals(false, marketplaceIsNewArrival(now - 8L * 24L * 60L * 60L * 1000L, now))
        assertEquals(false, marketplaceIsNewArrival(0L, now))
        assertEquals(false, marketplaceIsNewArrival(now + 1L, now))
    }

    @Test
    fun trendingRequiresTopFiveDiscoveryRankAndARealPositiveBackendScore() {
        assertEquals(true, marketplaceIsTrendingDiscoveryRank(1, 72.0))
        assertEquals(true, marketplaceIsTrendingDiscoveryRank(5, 8.0))
        assertEquals(false, marketplaceIsTrendingDiscoveryRank(6, 100.0))
        assertEquals(false, marketplaceIsTrendingDiscoveryRank(1, 0.0))
        assertEquals(false, marketplaceIsTrendingDiscoveryRank(1, Double.NaN))
    }

    @Test
    fun sellerHighlightsRequireRealSalesOrEnoughStrongReviews() {
        assertEquals(
            "Top Seller",
            marketplaceSellerHighlight(FynxMarketplaceClient.SellerReputation(2, 40, 12, 13, 92.3, 4.2, 3, "TOP SELLER"))
        )
        assertEquals(
            "Top Rated Seller",
            marketplaceSellerHighlight(FynxMarketplaceClient.SellerReputation(8, 40, 0, 0, 0.0, 4.8, 12, "NEW SELLER"))
        )
        assertNull(
            marketplaceSellerHighlight(FynxMarketplaceClient.SellerReputation(1, 40, 0, 0, 0.0, 0.0, 0, "NEW SELLER"))
        )
    }
}
