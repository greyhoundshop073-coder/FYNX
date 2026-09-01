package com.fynx.app.ui

/** Marketplace foundation for seller listings, media, pricing, purchases and sharing. */
enum class FynxMarketplaceMediaType { PHOTO, VIDEO }
enum class FynxOrderStatus { PENDING, CONFIRMED, COMPLETED, CANCELLED }

data class FynxMarketplaceMedia(val uri: String, val type: FynxMarketplaceMediaType)

data class FynxMarketplaceProduct(
    val id: String,
    val sellerUsername: String,
    val title: String,
    val description: String,
    val priceMinor: Long,
    val currency: String = "NGN",
    val media: List<FynxMarketplaceMedia> = emptyList(),
    val available: Boolean = true
)

data class FynxMarketplaceOrder(
    val id: String,
    val productId: String,
    val buyerUsername: String,
    val sellerUsername: String,
    val quantity: Int = 1,
    val status: FynxOrderStatus = FynxOrderStatus.PENDING
)

object FynxMarketplaceFoundation {
    fun valid(product: FynxMarketplaceProduct): Boolean =
        product.id.isNotBlank() && product.sellerUsername.isNotBlank() &&
            product.title.isNotBlank() && product.description.isNotBlank() &&
            product.priceMinor >= 0L

    fun canPurchase(product: FynxMarketplaceProduct, quantity: Int): Boolean =
        product.available && quantity > 0 && valid(product)

    fun createOrder(product: FynxMarketplaceProduct, buyerUsername: String, quantity: Int): FynxMarketplaceOrder? =
        if (!canPurchase(product, quantity) || buyerUsername.isBlank()) null
        else FynxMarketplaceOrder(
            id = "order_${product.id}_${System.currentTimeMillis()}",
            productId = product.id,
            buyerUsername = buyerUsername,
            sellerUsername = product.sellerUsername,
            quantity = quantity
        )

    fun shareTarget(product: FynxMarketplaceProduct): String = "marketplace/product/${product.id}"
    fun sellerTarget(product: FynxMarketplaceProduct): String = "profile/${product.sellerUsername}/marketplace"
}
