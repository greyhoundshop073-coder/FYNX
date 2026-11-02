package com.fynx.app.ui

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FynxMarketplacePromotionModelsTest {
    private val start = Instant.parse("2026-10-26T00:00:00Z")
    private val end = Instant.parse("2026-11-02T00:00:00Z")

    private fun seller(
        username: String,
        orders: Int,
        buyers: Int,
        gross: Double,
        refunded: Double = 0.0,
        currency: String = "NGN",
        weekStart: Instant = start,
        weekEnd: Instant = end
    ) = MarketplaceWeeklySellerPerformance(
        sellerUsername = username,
        weekStart = weekStart,
        weekEndExclusive = weekEnd,
        currency = currency,
        deliveredPaidOrders = orders,
        distinctDeliveredBuyers = buyers,
        grossDeliveredSales = gross,
        refundedAmount = refunded
    )

    @Test
    fun highestDeliveredPaidOrderCountWinsAndAdsAreNotAnInput() {
        val winner = selectMarketplaceSellerOfTheWeek(
            listOf(
                seller("seller-a", orders = 4, buyers = 4, gross = 90000.0),
                seller("seller-b", orders = 7, buyers = 5, gross = 120000.0)
            )
        )
        assertEquals("seller-b", winner?.sellerUsername)
    }

    @Test
    fun buyerCountThenNetSalesBreakTiesDeterministically() {
        val winner = selectMarketplaceSellerOfTheWeek(
            listOf(
                seller("seller-a", orders = 5, buyers = 3, gross = 100000.0),
                seller("seller-b", orders = 5, buyers = 4, gross = 80000.0)
            )
        )
        assertEquals("seller-b", winner?.sellerUsername)
    }

    @Test
    fun refundsReduceEligibleNetSalesAndZeroNetSalesCannotWin() {
        val winner = selectMarketplaceSellerOfTheWeek(
            listOf(
                seller("fully-refunded", orders = 3, buyers = 3, gross = 50000.0, refunded = 50000.0),
                seller("valid", orders = 2, buyers = 2, gross = 25000.0, refunded = 1000.0)
            )
        )
        assertEquals("valid", winner?.sellerUsername)
    }

    @Test
    fun noVerifiedCompletedOrdersMeansNoWinner() {
        assertNull(selectMarketplaceSellerOfTheWeek(listOf(seller("new-shop", 0, 0, 0.0))))
    }

    @Test
    fun mixedWeeksCannotBeComparedForOneWeeklyAward() {
        val previousWeek = Instant.parse("2026-10-19T00:00:00Z")
        assertNull(
            selectMarketplaceSellerOfTheWeek(
                listOf(
                    seller("seller-a", 5, 4, 50000.0),
                    seller("seller-b", 8, 6, 70000.0, weekStart = previousWeek)
                )
            )
        )
    }

    @Test
    fun tieBreaksAreStableWhenAllPerformanceMetricsMatch() {
        val winner = selectMarketplaceSellerOfTheWeek(
            listOf(
                seller("zeta", 3, 3, 50000.0),
                seller("alpha", 3, 3, 50000.0)
            )
        )
        assertEquals("alpha", winner?.sellerUsername)
    }
}
