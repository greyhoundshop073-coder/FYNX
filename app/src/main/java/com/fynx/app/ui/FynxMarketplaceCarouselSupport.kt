package com.fynx.app.ui

import java.util.Locale

/**
 * Minimal backend-derived facts used to choose the real listings shown in the
 * advertising carousel. UI and network dependencies are deliberately excluded
 * so selection rules can be tested deterministically on the JVM.
 */
internal data class MarketplaceAdvertisingCandidate(
    val id: String,
    val sellerUsername: String,
    val title: String,
    val price: Double,
    val currency: String,
    val quantity: Int,
    val active: Boolean
)

/**
 * Selects active, available, unique listings in one currency. Real successful
 * sales rank sellers when supplied; without that evidence, products use a
 * stable price order and are not falsely promoted as best sellers.
 */
internal fun selectMarketplaceAdvertisingCandidates(
    candidates: List<MarketplaceAdvertisingCandidate>,
    successfulSalesBySeller: Map<String, Int> = emptyMap()
): List<MarketplaceAdvertisingCandidate> {
    fun normalizedSeller(value: String) =
        value.trim().removePrefix("@").trim().lowercase(Locale.US)

    val eligible = candidates
        .filter {
            it.id.isNotBlank() &&
                it.title.isNotBlank() &&
                it.sellerUsername.isNotBlank() &&
                it.currency.isNotBlank() &&
                it.active &&
                it.quantity > 0 &&
                it.price.isFinite() &&
                it.price >= 0.0
        }
        .distinctBy { it.id.trim() }

    val currency = eligible
        .groupingBy { it.currency.trim().uppercase(Locale.US) }
        .eachCount()
        .entries
        .sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenBy { it.key }
        )
        .firstOrNull()
        ?.key
        ?: return emptyList()

    val sales = successfulSalesBySeller.entries.associate {
        normalizedSeller(it.key) to it.value.coerceAtLeast(0)
    }

    return eligible
        .filter { it.currency.trim().uppercase(Locale.US) == currency }
        .sortedWith(
            compareByDescending<MarketplaceAdvertisingCandidate> {
                sales[normalizedSeller(it.sellerUsername)] ?: 0
            }
                .thenBy { it.price }
                .thenBy { it.title.trim().lowercase(Locale.US) }
                .thenBy { it.id.trim() }
        )
}

/**
 * The LazyRow appends a duplicate of the first product as its final display
 * item. It animates from the last real product to that duplicate, then the UI
 * silently resets the scroll index to zero for a seamless visible loop.
 */
internal fun nextMarketplaceAdvertisingDisplayIndex(
    currentIndex: Int,
    productCount: Int
): Int? {
    if (productCount <= 1) return null
    val current = currentIndex.coerceIn(0, productCount)
    return when {
        current >= productCount -> 0
        current >= productCount - 1 -> productCount
        else -> current + 1
    }
}
