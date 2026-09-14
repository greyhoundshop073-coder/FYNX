package com.fynx.app.ui

import android.content.Context
import android.net.Uri

/**
 * Shared Marketplace seller-flow primitives.
 * Keeps the real media limits and validation in one place so the active
 * Marketplace UI can reuse the stronger seller implementation without
 * introducing another backend or fake listing path.
 */
internal object FynxMarketplaceSellerFlowSupport {
    const val MAX_PRODUCT_MEDIA = 12
    const val DEFAULT_CURRENCY = "NGN"

    fun normalizedMedia(context: Context, uris: List<Uri>): List<Uri> =
        uris.asSequence()
            .filter { uri ->
                val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
                mime.startsWith("image/") || mime.startsWith("video/")
            }
            .distinct()
            .take(MAX_PRODUCT_MEDIA)
            .toList()

    fun validListing(title: String, description: String, price: Double?, quantity: Int?, media: List<Uri>): Boolean =
        title.isNotBlank() &&
            description.isNotBlank() &&
            price != null && price > 0.0 &&
            quantity != null && quantity > 0 &&
            media.isNotEmpty()
}
