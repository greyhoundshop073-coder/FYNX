package com.fynx.app.ui

/** Seller-side Marketplace listing models. Media values are URI strings until storage is connected. */
enum class FynxProductCondition { NEW, USED, REFURBISHED }

data class FynxProductVariant(val name: String, val value: String)

data class FynxProductMedia(val uri: String, val isVideo: Boolean = false)

data class FynxMarketplaceListing(
    val id: String,
    val sellerUsername: String,
    val sellerStoreName: String,
    val title: String,
    val description: String,
    val price: Double,
    val currency: String,
    val category: String,
    val condition: FynxProductCondition,
    val quantity: Int,
    val variants: List<FynxProductVariant> = emptyList(),
    val media: List<FynxProductMedia> = emptyList(),
    val location: String = "",
    val deliveryAvailable: Boolean = false,
    val pickupAvailable: Boolean = true,
    val deliveryFee: Double? = null,
    val isDraft: Boolean = true
)

object FynxMarketplaceBatch1 {
    fun validate(listing: FynxMarketplaceListing): List<String> = buildList {
        if (listing.id.isBlank()) add("Listing ID is required")
        if (listing.sellerUsername.isBlank()) add("Seller is required")
        if (listing.title.trim().length < 2) add("Product name is required")
        if (listing.description.trim().length < 5) add("Product description is required")
        if (listing.price <= 0.0) add("Price must be greater than zero")
        if (listing.currency.isBlank()) add("Currency is required")
        if (listing.category.isBlank()) add("Category is required")
        if (listing.quantity < 0) add("Quantity cannot be negative")
        if (listing.media.isEmpty()) add("At least one product photo or video is required")
        if (listing.media.count { !it.isVideo } > 12) add("A maximum of 12 product photos is allowed")
        if (listing.media.count { it.isVideo } > 1) add("Only one product video is allowed")
        if (listing.deliveryFee != null && listing.deliveryFee < 0.0) add("Delivery fee cannot be negative")
    }

    fun publish(listing: FynxMarketplaceListing): FynxMarketplaceListing? =
        if (validate(listing).isEmpty()) listing.copy(isDraft = false) else null

    fun saveDraft(listing: FynxMarketplaceListing): FynxMarketplaceListing = listing.copy(isDraft = true)
}
