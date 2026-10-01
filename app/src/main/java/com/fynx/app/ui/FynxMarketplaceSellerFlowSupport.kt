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
    const val MAX_TITLE_LENGTH = 120
    const val MAX_DESCRIPTION_LENGTH = 5000
    const val MAX_QUANTITY = 1_000_000
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

    fun addMedia(context: Context, existing: List<Uri>, uri: Uri): List<Uri> =
        normalizedMedia(context, existing + uri)

    fun validListing(title: String, description: String, price: Double?, quantity: Int?, media: List<Uri>): Boolean =
        title.trim().isNotBlank() &&
            title.length <= MAX_TITLE_LENGTH &&
            description.trim().isNotBlank() &&
            description.length <= MAX_DESCRIPTION_LENGTH &&
            price != null && price.isFinite() && price > 0.0 &&
            quantity != null && quantity in 1..MAX_QUANTITY &&
            media.isNotEmpty() && media.size <= MAX_PRODUCT_MEDIA
}
