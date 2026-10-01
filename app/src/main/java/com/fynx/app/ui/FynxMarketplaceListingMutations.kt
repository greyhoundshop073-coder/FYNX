package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

/** Marketplace seller listing mutation helpers. */
suspend fun updateMarketplaceListing(
    context: Context,
    id: String,
    title: String,
    price: Double,
    quantity: Int
): Result<Unit> {
    val numericId = id.toLongOrNull()
        ?: return Result.failure(IllegalArgumentException("invalid listing id"))
    val safeTitle = title.trim().take(160)
    if (safeTitle.length < 2) {
        return Result.failure(IllegalArgumentException("Product name is required."))
    }
    if (!price.isFinite() || price <= 0.0) {
        return Result.failure(IllegalArgumentException("Enter a valid product price."))
    }
    if (quantity < 0) {
        return Result.failure(IllegalArgumentException("Quantity cannot be negative."))
    }

    return FynxBackendClient.postJson(
        context,
        "/api/marketplace/listings/$numericId/update",
        JSONObject().apply {
            put("title", safeTitle)
            put("price", price)
            put("quantity", quantity)
        }.toString()
    ).map { Unit }
}
