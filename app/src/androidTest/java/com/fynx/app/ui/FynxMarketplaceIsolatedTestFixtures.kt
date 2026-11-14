package com.fynx.app.ui

/**
 * Isolated instrumentation data. This file lives only in src/androidTest:
 * it is not packaged in the app, sent to the backend, or written to app storage.
 * Every synthetic transaction and review in this object is explicitly test-only.
 */
internal object FynxMarketplaceIsolatedTestFixtures {
    const val banner = "FYNX TEST DATA — ISOLATED INSTRUMENTATION ONLY"

    data class Identity(val id: String, val username: String, val displayName: String)
    data class Shop(
        val ownerUsername: String,
        val name: String,
        val description: String,
        val sellerBio: String,
        val logoPlaceholder: String,
        val coverPlaceholder: String
    )
    data class LocalBusiness(
        val id: String,
        val name: String,
        val category: String,
        val location: String,
        val description: String,
        val pickupAvailable: Boolean,
        val deliveryAvailable: Boolean
    )
    data class SampleReview(
        val id: String,
        val authorLabel: String,
        val rating: Int,
        val comment: String,
        val testOnly: Boolean = true
    )
    data class SimulatedTransaction(
        val reference: String,
        val type: String,
        val amount: Double,
        val availableBalanceDelta: Double,
        val status: String = "SIMULATED — TEST ONLY"
    )
    data class Wallet(
        val currency: String,
        val available: Double,
        val reserved: Double,
        val withdrawalPending: Double,
        val testMode: Boolean,
        val transactions: List<SimulatedTransaction>
    ) {
        fun isReconciled(): Boolean {
            val openingBalance = transactions.sumOf { it.availableBalanceDelta }
            return kotlin.math.abs(openingBalance - (available + reserved + withdrawalPending)) < 0.005
        }
    }
    data class CartLine(val listingId: String, val unitPrice: Double, val quantity: Int)

    val buyer = Identity("fynx-test-buyer-001", "fynx_test_buyer", "FYNX Test Buyer")
    val seller = Identity("fynx-test-seller-001", "fynx_test_seller", "FYNX Test Seller")

    val shop = Shop(
        ownerUsername = seller.username,
        name = "FYNX Test Market",
        description = "Sample shop profile used only by isolated FYNX instrumentation tests.",
        sellerBio = "Test seller identity. This is not a verified public seller account.",
        logoPlaceholder = "TEST SHOP LOGO PLACEHOLDER",
        coverPlaceholder = "TEST SHOP COVER PLACEHOLDER"
    )

    val localBusiness = LocalBusiness(
        id = "fynx-test-business-001",
        name = "FYNX Test Local Store",
        category = "Retail",
        location = "Port Harcourt, Rivers State, Nigeria",
        description = "Test-only local directory listing; not a real registered business.",
        pickupAvailable = true,
        deliveryAvailable = true
    )

    val sampleReviews = listOf(
        SampleReview("fynx-test-review-001", "FYNX TEST BUYER A", 5, "Sample review content — not a customer review."),
        SampleReview("fynx-test-review-002", "FYNX TEST BUYER B", 4, "Fixture text for layout testing only.")
    )

    private val categories = listOf(
        "Electronics", "Fashion", "Home", "Beauty", "Phones & Computers", "Furniture", "Sports"
    )

    val listings: List<FynxRemoteSocialClient.MarketplaceListing> = (1..28).map { index ->
        val currency = if (index % 5 == 0) "USD" else "NGN"
        val price = if (currency == "USD") 35.0 + index * 7.0 else 45_000.0 + index * 12_500.0
        FynxRemoteSocialClient.MarketplaceListing(
            id = "fynx-test-listing-%03d".format(index),
            sellerId = seller.id,
            sellerUsername = seller.username,
            sellerDisplayName = seller.displayName,
            storeName = shop.name,
            title = when (index) {
                1 -> "FYNX Test Wireless Headphones"
                2 -> "FYNX Test Android Smartphone"
                3 -> "FYNX Test Leather Handbag"
                4 -> "FYNX Test Table Lamp"
                else -> "FYNX Test Product %02d".format(index)
            },
            description = "FYNX TEST DATA. Sample listing $index; never published to a real catalogue.",
            price = price,
            currency = currency,
            category = categories[(index - 1) % categories.size],
            condition = if (index % 4 == 0) "USED" else "NEW",
            quantity = if (index % 11 == 0) 0 else 2 + index % 14,
            location = "Port Harcourt, Rivers State, Nigeria",
            deliveryAvailable = index % 3 != 0,
            pickupAvailable = index % 4 != 0,
            deliveryFee = if (index % 3 == 0) null else if (index % 2 == 0) 0.0 else 1_500.0,
            mediaIds = emptyList(),
            active = true,
            originalPrice = price * 1.25,
            productAverageRating = 0.0,
            productReviewCount = 0,
            priceDrop = false
        )
    }

    val wallet = Wallet(
        currency = "NGN",
        available = 170_000.0,
        reserved = 20_000.0,
        withdrawalPending = 10_000.0,
        testMode = true,
        transactions = listOf(
            SimulatedTransaction("FYNX-TEST-FUND-001", "TEST FUNDING", 200_000.0, 200_000.0),
            SimulatedTransaction("FYNX-TEST-ORDER-001", "TEST MARKETPLACE RESERVE", 20_000.0, -20_000.0),
            SimulatedTransaction("FYNX-TEST-WITHDRAW-001", "TEST WITHDRAWAL HOLD", 10_000.0, -10_000.0)
        )
    )

    fun searchAndFilter(query: String = "", category: String = "All"): List<FynxRemoteSocialClient.MarketplaceListing> =
        listings.filter { listing ->
            (query.isBlank() || listing.title.contains(query.trim(), ignoreCase = true) ||
                listing.description.contains(query.trim(), ignoreCase = true)) &&
                (category == "All" || listing.category.equals(category, ignoreCase = true))
        }

    fun page(query: String = "", category: String = "All", offset: Int = 0, limit: Int = 8): List<FynxRemoteSocialClient.MarketplaceListing> =
        searchAndFilter(query, category).drop(offset.coerceAtLeast(0)).take(limit.coerceIn(1, 24))

    fun cartTotal(lines: List<CartLine>): Double =
        lines.sumOf { it.unitPrice * it.quantity.coerceAtLeast(0) }

    fun marketplaceFee(amount: Double): Double = amount.coerceAtLeast(0.0) * 0.05
}
