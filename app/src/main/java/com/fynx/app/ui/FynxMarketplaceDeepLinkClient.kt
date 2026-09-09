package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

/** Exact, real Marketplace listing lookup used by deep-link routing. */
suspend fun loadExactMarketplaceListing(
    context: Context,
    listingId: String
): Result<FynxRemoteSocialClient.MarketplaceListing> = runCatching {
    val numericId = listingId.trim().toLongOrNull()
        ?: throw IllegalArgumentException("Invalid marketplace listing id")
    val raw = FynxBackendClient.get(context, "/api/marketplace/listing/$numericId").getOrThrow()
    val o = JSONObject(raw).getJSONObject("listing")
    val media = o.optJSONArray("media_ids")
    val mediaIds = buildList {
        if (media != null) for (i in 0 until media.length()) add(media.getString(i))
    }
    FynxRemoteSocialClient.MarketplaceListing(
        id = o.getString("id"),
        sellerId = o.optString("seller_id"),
        sellerUsername = o.optString("seller_username"),
        sellerDisplayName = o.optString("seller_display_name"),
        storeName = o.optString("store_name"),
        title = o.optString("title"),
        description = o.optString("description"),
        price = o.optDouble("price", 0.0),
        currency = o.optString("currency", "NGN"),
        category = o.optString("category"),
        condition = o.optString("condition", "NEW"),
        quantity = o.optInt("quantity", 0),
        location = o.optString("location"),
        deliveryAvailable = o.optBoolean("delivery_available", false),
        pickupAvailable = o.optBoolean("pickup_available", true),
        deliveryFee = if (o.isNull("delivery_fee")) null else o.optDouble("delivery_fee"),
        mediaIds = mediaIds,
        active = o.optBoolean("active", true)
    )
}
