package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Authenticated discovery client. Events are best-effort so analytics never block user actions. */
object FynxDiscoveryClient {
    suspend fun recordEvent(context: Context, eventType: String, postId: String? = null, listingId: String? = null, targetUserId: String? = null, sessionId: String? = null, metadata: JSONObject? = null): Result<Unit> {
        val type = eventType.trim().uppercase()
        require(type in EVENT_TYPES) { "Unsupported discovery event." }
        val body = JSONObject().apply {
            put("eventType", type)
            postId?.toLongOrNull()?.let { put("postId", it) }
            listingId?.toLongOrNull()?.let { put("listingId", it) }
            targetUserId?.toLongOrNull()?.let { put("targetUserId", it) }
            sessionId?.takeIf { it.isNotBlank() }?.let { put("sessionId", it.take(120)) }
            put("metadata", metadata ?: JSONObject())
        }
        return FynxBackendClient.postJson(context, "/api/discovery/events", body.toString()).map { Unit }
    }

    suspend fun recordView(context: Context, postId: String): Result<Unit> = recordEvent(context, "VIEW", postId = postId)
    suspend fun recordEngagement(context: Context, eventType: String, postId: String): Result<Unit> = recordEvent(context, eventType, postId = postId)
    suspend fun recordProfileView(context: Context, targetUserId: String): Result<Unit> = recordEvent(context, "PROFILE_VIEW", targetUserId = targetUserId)
    suspend fun recordProductClick(context: Context, listingId: String): Result<Unit> = recordEvent(context, "PRODUCT_CLICK", listingId = listingId)
    suspend fun recordPurchase(context: Context, listingId: String): Result<Unit> = recordEvent(context, "PURCHASE", listingId = listingId)
    suspend fun recordNotInterested(context: Context, postId: String): Result<Unit> = recordEvent(context, "NOT_INTERESTED", postId = postId)

    suspend fun trending(context: Context, limit: Int = 20): Result<List<TrendingPost>> =
        FynxBackendClient.get(context, "/api/discovery/trending?limit=${limit.coerceIn(1, 50)}").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("posts") ?: JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(TrendingPost(o.optString("id"), o.optString("authorUsername"), o.optString("authorDisplayName"), o.optString("text"), o.optString("mediaId").takeIf { it.isNotBlank() && it != "null" }, o.optString("mediaType").takeIf { it.isNotBlank() && it != "null" }, o.optDouble("timestamp").toLong(), o.optInt("likeCount"), o.optInt("commentCount"), o.optInt("shareCount"), o.optInt("saveCount"), o.optDouble("discoveryScore")))
                }
            }
        }

    suspend fun marketplaceDiscovery(context: Context, query: String = "", category: String = "All", limit: Int = 30): Result<List<FynxMarketplaceClient.Listing>> {
        val path = "/api/marketplace/discovery?q=${encode(query)}&category=${encode(category)}&limit=${limit.coerceIn(1, 60)}"
        return FynxBackendClient.get(context, path).mapCatching(::parseListings)
    }

    data class TrendingPost(val id: String, val authorUsername: String, val authorDisplayName: String, val text: String, val mediaId: String?, val mediaType: String?, val timestamp: Long, val likeCount: Int, val commentCount: Int, val shareCount: Int, val saveCount: Int, val score: Double)

    private fun parseListings(raw: String): List<FynxMarketplaceClient.Listing> {
        val array = JSONObject(raw).optJSONArray("listings") ?: JSONArray()
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val media = o.optJSONArray("media_ids") ?: JSONArray()
                val ids = buildList { for (j in 0 until media.length()) add(media.get(j).toString()) }
                add(FynxMarketplaceClient.Listing(o.optString("id"), o.optString("seller_username"), o.optString("seller_display_name"), o.optString("store_name"), o.optString("title"), o.optString("description"), o.optDouble("price", 0.0), o.optString("currency", "NGN"), o.optString("category"), o.optString("condition", "NEW"), o.optInt("quantity", 0), o.optString("location"), o.optBoolean("delivery_available", false), o.optBoolean("pickup_available", true), if (o.isNull("delivery_fee")) null else o.optDouble("delivery_fee"), ids, o.optBoolean("active", true)))
            }
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value.trim(), "UTF-8")
    private val EVENT_TYPES = setOf("VIEW", "LIKE", "COMMENT", "SHARE", "SAVE", "FOLLOW", "PROFILE_VIEW", "PRODUCT_CLICK", "MESSAGE", "PURCHASE", "NOT_INTERESTED")
}
