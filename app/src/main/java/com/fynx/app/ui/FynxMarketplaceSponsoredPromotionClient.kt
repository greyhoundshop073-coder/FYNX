package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A paid campaign paired with its canonical Marketplace product listing. */
data class FynxMarketplaceSponsoredPromotion(
    val campaignId: String,
    val headline: String,
    val body: String,
    val listing: FynxRemoteSocialClient.MarketplaceListing
)

object FynxMarketplaceSponsoredPromotionClient {
    suspend fun load(context: Context): Result<List<FynxMarketplaceSponsoredPromotion>> =
        FynxBackendClient.get(context, "/api/advertising/marketplace-feed").mapCatching { raw ->
            val root = JSONObject(raw)
            val rows = root.optJSONArray("promotions") ?: JSONArray()
            buildList {
                for (index in 0 until rows.length()) {
                    val campaign = rows.optJSONObject(index) ?: continue
                    val listing = campaign.optJSONObject("listing") ?: continue
                    val id = listing.optString("id").trim()
                    val campaignId = campaign.optString("campaignId").trim()
                    val sellerUsername = listing.optString("sellerUsername").trim().removePrefix("@")
                    val title = listing.optString("title").trim()
                    val price = listing.optDouble("price", Double.NaN)
                    val currency = listing.optString("currency").trim().uppercase()
                    val quantity = listing.optInt("quantity", 0)
                    if (id.toLongOrNull()?.let { it > 0L } != true ||
                        campaignId.toLongOrNull()?.let { it > 0L } != true ||
                        sellerUsername.isBlank() ||
                        title.isBlank() ||
                        !price.isFinite() || price <= 0.0 ||
                        currency !in setOf("NGN", "USD") ||
                        quantity < 1
                    ) continue

                    val media = listing.optJSONArray("mediaIds") ?: JSONArray()
                    val mediaIds = buildList {
                        for (mediaIndex in 0 until media.length()) {
                            val mediaId = media.optString(mediaIndex).trim()
                            if (mediaId.isNotBlank()) add(mediaId)
                        }
                    }
                    val mediaTypesJson = listing.optJSONArray("mediaTypes") ?: JSONArray()
                    val mediaTypes = buildList {
                        for (mediaIndex in 0 until mediaTypesJson.length()) {
                            add(mediaTypesJson.optString(mediaIndex))
                        }
                    }
                    val product = FynxRemoteSocialClient.MarketplaceListing(
                        id = id,
                        sellerId = listing.optString("sellerId"),
                        sellerUsername = sellerUsername,
                        sellerDisplayName = listing.optString("sellerDisplayName").ifBlank { sellerUsername },
                        storeName = listing.optString("storeName"),
                        title = title,
                        description = listing.optString("description"),
                        price = price,
                        currency = currency,
                        category = listing.optString("category"),
                        condition = listing.optString("condition", "NEW"),
                        quantity = quantity,
                        location = listing.optString("location"),
                        deliveryAvailable = listing.optBoolean("deliveryAvailable"),
                        pickupAvailable = listing.optBoolean("pickupAvailable", true),
                        deliveryFee = if (listing.isNull("deliveryFee")) null else listing.optDouble("deliveryFee"),
                        mediaIds = mediaIds,
                        active = listing.optBoolean("active", true),
                        mediaTypes = mediaTypes
                    )
                    if (!product.active) continue
                    add(
                        FynxMarketplaceSponsoredPromotion(
                            campaignId = campaignId,
                            headline = campaign.optString("headline").trim(),
                            body = campaign.optString("body").trim(),
                            listing = product
                        )
                    )
                }
            }
        }
}
