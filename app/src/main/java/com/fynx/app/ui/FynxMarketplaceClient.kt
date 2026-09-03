package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Account-scoped Marketplace network client. No fake listings are generated. */
object FynxMarketplaceClient {
    data class Listing(
        val id: String,
        val sellerUsername: String,
        val sellerDisplayName: String,
        val storeName: String,
        val title: String,
        val description: String,
        val price: Double,
        val currency: String,
        val category: String,
        val condition: String,
        val quantity: Int,
        val location: String,
        val deliveryAvailable: Boolean,
        val pickupAvailable: Boolean,
        val deliveryFee: Double?,
        val mediaIds: List<String>,
        val active: Boolean = true
    )

    suspend fun listings(context: Context, query: String = "", category: String = ""): Result<List<Listing>> =
        FynxBackendClient.get(context, "/api/marketplace/listings?q=${encode(query)}&category=${encode(category)}").mapCatching(::parseListings)

    suspend fun myListings(context: Context): Result<List<Listing>> =
        FynxBackendClient.get(context, "/api/marketplace/my-listings").mapCatching(::parseListings)

    suspend fun createListing(
        context: Context,
        title: String,
        description: String,
        storeName: String,
        price: Double,
        currency: String,
        category: String,
        condition: String,
        quantity: Int,
        location: String,
        deliveryAvailable: Boolean,
        pickupAvailable: Boolean,
        deliveryFee: Double?,
        mediaIds: List<String>
    ): Result<String> {
        val assessment = FynxMarketplaceSafety.analyze(title, description, storeName, location)
        FynxMarketplaceSafety.publishDecision(assessment).getOrElse { return Result.failure(it) }
        if (!price.isFinite() || price <= 0.0) return Result.failure(IllegalArgumentException("Enter a valid product price."))
        if (quantity <= 0) return Result.failure(IllegalArgumentException("Product quantity must be at least 1."))
        if (deliveryFee != null && (!deliveryFee.isFinite() || deliveryFee < 0.0)) {
            return Result.failure(IllegalArgumentException("Enter a valid delivery fee."))
        }
        val distinctMediaIds = mediaIds.distinct().take(12)
        val media = JSONArray().apply { distinctMediaIds.forEach { put(it) } }
        val body = JSONObject()
            .put("title", title.trim())
            .put("description", description.trim())
            .put("storeName", storeName.trim())
            .put("price", price)
            .put("currency", currency.trim().uppercase())
            .put("category", category.trim())
            .put("condition", condition.trim().uppercase())
            .put("quantity", quantity)
            .put("location", location.trim())
            .put("deliveryAvailable", deliveryAvailable)
            .put("pickupAvailable", pickupAvailable)
            .put("mediaIds", media)
        if (deliveryFee != null) body.put("deliveryFee", deliveryFee)
        return FynxBackendClient.postJson(context, "/api/marketplace/listings", body.toString()).mapCatching {
            JSONObject(it).getJSONObject("listing").getString("id")
        }.also { result ->
            result.onSuccess { listingId ->
                // A real listing is also published as a real social-feed product post.
                // The advertisement intentionally starts without media because the existing
                // listing API does not expose each media item's MIME type to this client.
                // This avoids ever labeling a video as an image.
                FynxRemoteSocialClient.createMarketplaceAd(
                    context = context,
                    listingId = listingId,
                    title = title.trim(),
                    description = description.trim(),
                    storeName = storeName.trim(),
                    price = price,
                    currency = currency.trim().uppercase(),
                    mediaId = null
                )
            }
        }
    }

    suspend fun deleteListing(context: Context, listingId: String): Result<Unit> =
        FynxBackendClient.delete(context, "/api/marketplace/listings/${encode(listingId)}").map { }

    fun safetyAssessment(listing: Listing): FynxMarketplaceSafetyAssessment =
        FynxMarketplaceSafety.analyze(listing.title, listing.description, listing.storeName, listing.location)

    fun mediaUrl(context: Context, mediaId: String): String =
        "${FynxBackendClient.baseUrl(context)}/api/media/${encode(mediaId)}"

    private fun parseListings(raw: String): List<Listing> {
        val array = JSONObject(raw).getJSONArray("listings")
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val media = o.optJSONArray("media_ids") ?: JSONArray()
                val ids = buildList { for (j in 0 until media.length()) add(media.getString(j)) }
                add(Listing(
                    id = o.getString("id"),
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
                    mediaIds = ids,
                    active = o.optBoolean("active", true)
                ))
            }
        }
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value.trim(), "UTF-8").replace("+", "%20")
}
