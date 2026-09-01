package com.fynx.app.ui

/** Buyer-side Marketplace contracts. Payment and delivery execution are backend-ready hooks. */
data class FynxCartItem(val listingId: String, val title: String, val unitPrice: Double, val quantity: Int, val currency: String)

data class FynxMarketplaceOrder(
    val id: String,
    val items: List<FynxCartItem>,
    val subtotal: Double,
    val deliveryFee: Double,
    val total: Double,
    val currency: String,
    val status: FynxOrderStatus = FynxOrderStatus.PENDING_PAYMENT
)

enum class FynxOrderStatus { PENDING_PAYMENT, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELLED }

data class FynxProductReview(val listingId: String, val reviewerUsername: String, val rating: Int, val text: String = "")

object FynxMarketplaceBatch2 {
    fun cartTotal(items: List<FynxCartItem>): Double = items.sumOf { it.unitPrice * it.quantity.coerceAtLeast(0) }

    fun createOrder(id: String, items: List<FynxCartItem>, deliveryFee: Double, currency: String): FynxMarketplaceOrder? {
        if (id.isBlank() || items.isEmpty() || deliveryFee < 0.0 || currency.isBlank()) return null
        if (items.any { it.quantity <= 0 || it.unitPrice < 0.0 || it.currency.isBlank() }) return null
        val subtotal = cartTotal(items)
        return FynxMarketplaceOrder(id, items.toList(), subtotal, deliveryFee, subtotal + deliveryFee, currency)
    }

    fun nextOrderStatus(status: FynxOrderStatus): FynxOrderStatus = when (status) {
        FynxOrderStatus.PENDING_PAYMENT -> FynxOrderStatus.PAID
        FynxOrderStatus.PAID -> FynxOrderStatus.PROCESSING
        FynxOrderStatus.PROCESSING -> FynxOrderStatus.SHIPPED
        FynxOrderStatus.SHIPPED -> FynxOrderStatus.DELIVERED
        FynxOrderStatus.DELIVERED, FynxOrderStatus.CANCELLED -> status
    }

    fun validateReview(review: FynxProductReview): Boolean =
        review.listingId.isNotBlank() && review.reviewerUsername.isNotBlank() && review.rating in 1..5
}
