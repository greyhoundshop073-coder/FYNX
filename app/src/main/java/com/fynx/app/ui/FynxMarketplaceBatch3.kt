package com.fynx.app.ui

/** Social Marketplace integration contracts for Groups, Chat and seller stores. */
data class FynxMarketplaceSeller(
    val username: String,
    val storeName: String,
    val verified: Boolean = false,
    val rating: Double = 0.0,
    val productIds: List<String> = emptyList()
)

data class FynxMarketplaceProductShare(
    val productId: String,
    val sellerUsername: String,
    val title: String,
    val priceText: String,
    val destinationId: String,
    val destinationType: FynxShareDestination
)

enum class FynxShareDestination { GROUP, CHAT }

data class FynxMarketplaceSafetyReport(
    val targetId: String,
    val reporterUsername: String,
    val reason: String,
    val isSellerReport: Boolean = false
)

data class FynxMarketplaceTransactionReference(
    val orderId: String,
    val reference: String,
    val createdAtMillis: Long
)

object FynxMarketplaceBatch3 {
    fun sellerProfile(username: String, storeName: String, verified: Boolean, rating: Double, productIds: List<String>): FynxMarketplaceSeller? {
        if (username.isBlank() || storeName.isBlank() || rating !in 0.0..5.0) return null
        return FynxMarketplaceSeller(username, storeName, verified, rating, productIds.distinct())
    }

    fun shareProduct(productId: String, sellerUsername: String, title: String, priceText: String, destinationId: String, destinationType: FynxShareDestination): FynxMarketplaceProductShare? {
        if (productId.isBlank() || sellerUsername.isBlank() || title.isBlank() || destinationId.isBlank()) return null
        return FynxMarketplaceProductShare(productId, sellerUsername, title, priceText, destinationId, destinationType)
    }

    fun createReport(targetId: String, reporterUsername: String, reason: String, isSellerReport: Boolean): FynxMarketplaceSafetyReport? =
        if (targetId.isBlank() || reporterUsername.isBlank() || reason.trim().isEmpty()) null
        else FynxMarketplaceSafetyReport(targetId, reporterUsername, reason.trim(), isSellerReport)

    fun createTransactionReference(orderId: String, reference: String, createdAtMillis: Long = System.currentTimeMillis()): FynxMarketplaceTransactionReference? =
        if (orderId.isBlank() || reference.isBlank()) null
        else FynxMarketplaceTransactionReference(orderId, reference, createdAtMillis)
}
