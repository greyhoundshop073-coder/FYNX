package com.fynx.app.ui

import java.time.Instant
import java.util.Locale

/**
 * Verified seller performance for one explicitly bounded Marketplace week.
 *
 * The repository must populate these values from payment-confirmed, delivered
 * orders. Advertising spend, impressions, clicks, and campaign priority are
 * intentionally absent so paid promotion can never affect the weekly award.
 */
internal data class MarketplaceWeeklySellerPerformance(
    val sellerUsername: String,
    val weekStart: Instant,
    val weekEndExclusive: Instant,
    val currency: String,
    val deliveredPaidOrders: Int,
    val distinctDeliveredBuyers: Int,
    val grossDeliveredSales: Double,
    val refundedAmount: Double = 0.0
) {
    val netDeliveredSales: Double
        get() = (grossDeliveredSales - refundedAmount).coerceAtLeast(0.0)
}

/**
 * Rejects malformed or out-of-window seasonal promotions, including campaigns
 * with missing timestamps. Cached campaign data must never revive an expired,
 * not-yet-started or administratively disabled banner.
 */
internal fun isMarketplaceSeasonalPromotionEligible(
    promotion: FynxMarketplaceSeasonalPromotion,
    now: Instant
): Boolean {
    if (!promotion.active || promotion.id.isBlank() || promotion.occasionKey.isBlank() || promotion.headline.isBlank()) {
        return false
    }
    val starts = runCatching { Instant.parse(promotion.startsAt) }.getOrNull() ?: return false
    val ends = runCatching { Instant.parse(promotion.endsAt) }.getOrNull() ?: return false
    return starts.isBefore(ends) && !now.isBefore(starts) && now.isBefore(ends)
}

/**
 * Returns a single winner only when the supplied reports describe the same
 * week and currency. Ranking is deterministic and uses delivered paid orders,
 * distinct buyers, then net delivered sales as tie-breakers.
 *
 * This function does not manufacture a winner when there is no qualifying
 * verified sales evidence.
 */
internal fun selectMarketplaceSellerOfTheWeek(
    reports: List<MarketplaceWeeklySellerPerformance>
): MarketplaceWeeklySellerPerformance? {
    val eligible = reports.filter {
        it.sellerUsername.isNotBlank() &&
            it.currency.isNotBlank() &&
            it.weekStart < it.weekEndExclusive &&
            it.deliveredPaidOrders > 0 &&
            it.distinctDeliveredBuyers > 0 &&
            it.grossDeliveredSales.isFinite() &&
            it.grossDeliveredSales >= 0.0 &&
            it.refundedAmount.isFinite() &&
            it.refundedAmount >= 0.0 &&
            it.netDeliveredSales > 0.0
    }
    if (eligible.isEmpty()) return null

    // Never compare different reporting windows: that would make the weekly award unfair.
    val reportingWindows = eligible.map { it.weekStart to it.weekEndExclusive }.distinct()
    if (reportingWindows.size != 1) return null
    val (weekStart, weekEnd) = reportingWindows.single()
    if (weekStart >= weekEnd) return null

    val sameWeek = eligible.filter { it.weekStart == weekStart && it.weekEndExclusive == weekEnd }
    val currencyCounts = sameWeek.groupingBy { it.currency.trim().uppercase(Locale.US) }.eachCount()
    val currency = currencyCounts.entries.sortedWith(
        compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }
    ).firstOrNull()?.key ?: return null

    return sameWeek.asSequence()
        .filter { it.currency.trim().equals(currency, ignoreCase = true) }
        .groupBy { it.sellerUsername.trim().removePrefix("@").lowercase(Locale.US) }
        .mapNotNull { (_, sellerReports) ->
            val first = sellerReports.firstOrNull() ?: return@mapNotNull null
            first.copy(
                deliveredPaidOrders = sellerReports.sumOf { it.deliveredPaidOrders },
                distinctDeliveredBuyers = sellerReports.maxOf { it.distinctDeliveredBuyers },
                grossDeliveredSales = sellerReports.sumOf { it.grossDeliveredSales },
                refundedAmount = sellerReports.sumOf { it.refundedAmount }
            )
        }
        .filter { it.netDeliveredSales > 0.0 }
        .sortedWith(
            compareByDescending<MarketplaceWeeklySellerPerformance> { it.deliveredPaidOrders }
                .thenByDescending { it.distinctDeliveredBuyers }
                .thenByDescending { it.netDeliveredSales }
                .thenBy { it.sellerUsername.trim().lowercase(Locale.US) }
        )
        .firstOrNull()
}
