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

    data class ShippingSettings(
        val method: String,
        val note: String,
        val baseFee: Double,
        val additionalItemFee: Double,
        val feeCap: Double?,
        val deliveryAvailable: Boolean,
        val pickupAvailable: Boolean,
        val coverage: List<Coverage>
    )

    data class Coverage(val country: String, val state: String = "", val city: String = "")

    suspend fun listings(context: Context, query: String = "", category: String = ""): Result<List<Listing>> {
        val discovery = FynxDiscoveryClient.marketplaceDiscovery(context, query, category)
        if (discovery.isSuccess) return discovery
        return FynxBackendClient.get(context, "/api/marketplace/listings?q=${encode(query)}&category=${encode(category)}")
            .mapCatching(::parseListings)
    }

    suspend fun listingById(context: Context, listingId: String): Result<Listing> {
        val normalized = listingId.trim()
        if (normalized.isEmpty() || normalized.toLongOrNull() == null || normalized.toLong() <= 0L) {
            return Result.failure(IllegalArgumentException("invalid listing id"))
        }
        return FynxBackendClient.get(context, "/api/marketplace/listing/${encode(normalized)}")
            .mapCatching { raw -> parseListingObject(JSONObject(raw).getJSONObject("listing")) }
    }

    data class SellerReputation(val rank: Int, val sellerCount: Int, val successfulSales: Int, val totalOrders: Int, val completionRate: Double, val averageRating: Double, val reviewCount: Int, val tier: String)

    suspend fun sellerReputation(context: Context, username: String): Result<SellerReputation> =
        FynxBackendClient.get(context, "/api/marketplace/sellers/${encode(username)}/reputation").mapCatching { raw ->
            val o = JSONObject(raw).getJSONObject("reputation")
            SellerReputation(o.optInt("rank", 0), o.optInt("sellerCount", 0), o.optInt("successfulSales", 0), o.optInt("totalOrders", 0), o.optDouble("completionRate", 0.0), o.optDouble("averageRating", 0.0), o.optInt("reviewCount", 0), o.optString("tier", "NEW SELLER"))
        }

    suspend fun myListings(context: Context): Result<List<Listing>> =
        FynxBackendClient.get(context, "/api/marketplace/my-listings").mapCatching(::parseListings)

    suspend fun shippingSettings(context: Context, listingId: String): Result<ShippingSettings> {
        val id = listingId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid listing id"))
        return FynxBackendClient.get(context, "/api/marketplace/listings/$id/shipping").mapCatching { raw ->
            val s = JSONObject(raw).getJSONObject("shipping")
            val rows = s.optJSONArray("coverage") ?: JSONArray()
            val coverage = buildList {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    add(Coverage(row.optString("country"), row.optString("state"), row.optString("city")))
                }
            }
            ShippingSettings(
                method = s.optString("method", "SELLER_ARRANGED"),
                note = s.optString("note"),
                baseFee = s.optDouble("baseFee", 0.0),
                additionalItemFee = s.optDouble("additionalItemFee", 0.0),
                feeCap = if (s.isNull("feeCap")) null else s.optDouble("feeCap"),
                deliveryAvailable = s.optBoolean("deliveryAvailable"),
                pickupAvailable = s.optBoolean("pickupAvailable", true),
                coverage = coverage
            )
        }
    }

    suspend fun saveShippingSettings(
        context: Context,
        listingId: String,
        settings: ShippingSettings
    ): Result<ShippingSettings> {
        val id = listingId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid listing id"))
        require(settings.baseFee >= 0 && settings.additionalItemFee >= 0) { "Shipping fees cannot be negative." }
        require(settings.feeCap == null || settings.feeCap >= settings.baseFee) { "Shipping fee cap cannot be below the base fee." }
        val coverage = JSONArray().apply {
            settings.coverage.distinctBy { "${it.country.trim().uppercase()}|${it.state.trim().uppercase()}|${it.city.trim().uppercase()}" }.take(100).forEach {
                put(JSONObject().put("country", it.country.trim()).put("state", it.state.trim()).put("city", it.city.trim()))
            }
        }
        val body = JSONObject()
            .put("method", settings.method.trim().uppercase().ifBlank { "SELLER_ARRANGED" })
            .put("note", settings.note.trim().take(500))
            .put("baseFee", settings.baseFee)
            .put("additionalItemFee", settings.additionalItemFee)
            .put("feeCap", settings.feeCap ?: JSONObject.NULL)
            .put("coverage", coverage)
        return FynxBackendClient.putJson(context, "/api/marketplace/listings/$id/shipping", body.toString()).mapCatching { raw ->
            val s = JSONObject(raw).getJSONObject("shipping")
            val rows = s.optJSONArray("coverage") ?: JSONArray()
            val parsed = buildList {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    add(Coverage(row.optString("country"), row.optString("state"), row.optString("city")))
                }
            }
            ShippingSettings(s.optString("method", "SELLER_ARRANGED"), s.optString("note"), s.optDouble("baseFee", 0.0), s.optDouble("additionalItemFee", 0.0), if (s.isNull("feeCap")) null else s.optDouble("feeCap"), settings.deliveryAvailable, settings.pickupAvailable, parsed)
        }
    }

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
        if (deliveryFee != null && (!deliveryFee.isFinite() || deliveryFee < 0.0)) return Result.failure(IllegalArgumentException("Enter a valid delivery fee."))
        val distinctMediaIds = mediaIds.distinct().take(12)
        val media = JSONArray().apply { distinctMediaIds.forEach { put(it) } }
        val body = JSONObject().put("title", title.trim()).put("description", description.trim()).put("storeName", storeName.trim()).put("price", price).put("currency", currency.trim().uppercase()).put("category", category.trim()).put("condition", condition.trim().uppercase()).put("quantity", quantity).put("location", location.trim()).put("deliveryAvailable", deliveryAvailable).put("pickupAvailable", pickupAvailable).put("mediaIds", media)
        if (deliveryFee != null) body.put("deliveryFee", deliveryFee)
        return FynxBackendClient.postJson(context, "/api/marketplace/listings", body.toString()).mapCatching { JSONObject(it).getJSONObject("listing").getString("id") }.also { result ->
            result.onSuccess { listingId ->
                val cleanTitle = title.trim().take(120); val cleanDescription = description.trim().take(1000); val cleanStore = storeName.trim().take(120)
                val priceText = "${currency.trim().uppercase()} ${String.format(java.util.Locale.US, "%,.2f", price)}"
                val adText = buildString { append("[FYNX_MARKETPLACE_AD]\n"); append("🛍️ $cleanTitle\n"); append("Price: $priceText\n"); if (cleanStore.isNotBlank()) append("Store: $cleanStore\n"); if (cleanDescription.isNotBlank()) append(cleanDescription); append("\nListing ID: $listingId") }
                FynxRemoteSocialClient.createPost(context = context, text = adText.take(4000), visibility = FynxPostVisibility.PUBLIC, uri = null)
            }
        }
    }

    suspend fun deleteListing(context: Context, listingId: String): Result<Unit> = FynxBackendClient.delete(context, "/api/marketplace/listings/${encode(listingId)}").map { }
    fun safetyAssessment(listing: Listing): FynxMarketplaceSafetyAssessment = FynxMarketplaceSafety.analyze(listing.title, listing.description, listing.storeName, listing.location)
    fun mediaUrl(context: Context, mediaId: String): String = "${FynxBackendClient.baseUrl(context)}/api/media/${encode(mediaId)}"

    private fun parseListings(raw: String): List<Listing> {
        val array = JSONObject(raw).getJSONArray("listings")
        return buildList {
            for (i in 0 until array.length()) add(parseListingObject(array.getJSONObject(i)))
        }
    }

    private fun parseListingObject(o: JSONObject): Listing {
        val media = o.optJSONArray("media_ids") ?: JSONArray()
        val ids = buildList { for (j in 0 until media.length()) add(media.getString(j)) }
        return Listing(
            o.getString("id"), o.optString("seller_username"), o.optString("seller_display_name"),
            o.optString("store_name"), o.optString("title"), o.optString("description"),
            o.optDouble("price", 0.0), o.optString("currency", "NGN"), o.optString("category"),
            o.optString("condition", "NEW"), o.optInt("quantity", 0), o.optString("location"),
            o.optBoolean("delivery_available", false), o.optBoolean("pickup_available", true),
            if (o.isNull("delivery_fee")) null else o.optDouble("delivery_fee"), ids, o.optBoolean("active", true)
        )
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value.trim(), "UTF-8")
}
