package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import java.util.Locale

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
    val SUPPORTED_CURRENCIES = listOf("NGN", "USD")
    fun isSupportedCurrency(currency: String): Boolean = currency.trim().uppercase() in SUPPORTED_CURRENCIES

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

/** Accepts only real JSON numeric values; missing, string, NaN and infinite amounts fail closed. */
internal fun marketplaceRequiredAmount(value: Any?): Double {
    val amount = (value as? Number)?.toDouble()
        ?: throw IllegalStateException("Seller earnings data is incomplete.")
    if (!amount.isFinite()) throw IllegalStateException("Seller earnings data is invalid.")
    return amount
}

/** Balance buckets cannot be negative; do not turn malformed values into a plausible zero balance. */
internal fun marketplaceRequiredBalance(value: Any?): Double {
    val amount = marketplaceRequiredAmount(value)
    if (amount < 0.0) throw IllegalStateException("Seller earnings data is invalid.")
    return amount
}

/** Currency codes are required explicitly rather than defaulting missing money to NGN. */
internal fun marketplaceRequiredCurrency(value: Any?): String {
    val currency = (value as? String)?.trim()?.uppercase(Locale.US).orEmpty()
    if (!currency.matches(Regex("[A-Z]{3}"))) {
        throw IllegalStateException("Seller earnings data is incomplete.")
    }
    return currency
}

/**
 * Keep transport/HTTP implementation details out of Marketplace user-facing error text.
 * Domain validation messages remain useful to the user; raw server bodies and request IDs do not.
 */
internal fun marketplaceSafeError(error: Throwable?, fallback: String): String {
    val message = error?.message?.trim().orEmpty()
    if (message.isBlank()) return fallback
    val normalized = message.lowercase(Locale.US)
    val isRawTransportResponse =
        normalized.startsWith("fynx backend returned http ") ||
            normalized.startsWith("http ") ||
            normalized.contains("request_id") ||
            normalized.contains("\"status\":\"error\"") ||
            normalized.startsWith("java.net.") ||
            normalized.startsWith("javax.net.") ||
            normalized.contains("unknownhostexception") ||
            normalized.contains("sockettimeoutexception") ||
            normalized.contains("connection reset") ||
            normalized.contains("failed to connect") ||
            normalized.contains("unable to resolve host") ||
            normalized.contains("cleartext communication") ||
            normalized.startsWith("<html") ||
            normalized.startsWith("<!doctype html") ||
            (normalized.startsWith("{") && normalized.endsWith("}"))
    return if (isRawTransportResponse) fallback else message.take(240)
}
