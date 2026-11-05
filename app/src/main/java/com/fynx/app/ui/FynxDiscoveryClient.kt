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
    suspend fun recordInterested(context: Context, postId: String): Result<Unit> = recordEvent(context, "INTERESTED", postId = postId)
    suspend fun recordNotInterested(context: Context, postId: String): Result<Unit> = recordEvent(context, "NOT_INTERESTED", postId = postId)

    /**
     * Explicitly enabled, debug-only Discovery fixtures for the disposable CI emulator.
     * The media IDs point at the same account-scoped files seeded by the runtime script;
     * release builds and ordinary debug sessions never receive synthetic Discovery posts.
     */
    fun localVisualFixtureVideos(context: Context): List<TrendingPost>? {
        val debuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val enabled = context.getSharedPreferences("fynx_ci_visual_fixtures", Context.MODE_PRIVATE)
            .getBoolean("home_discovery_videos", false)
        if (!debuggable || !enabled) return null
        val now = System.currentTimeMillis()
        return listOf(
            TrendingPost(
                id = "ci-home-discovery-portrait",
                authorUsername = "@fynx_visual_fixture",
                authorDisplayName = "FYNX Visual Fixture",
                text = "CI TEST FIXTURE — Discovery portrait video. Local-only preview; not published or uploaded.",
                mediaId = "ci-home-portrait",
                mediaType = "video",
                timestamp = now,
                likeCount = 12,
                commentCount = 3,
                shareCount = 0,
                saveCount = 0,
                score = 100.0,
                authorId = "ci-local-fixture-author",
                visibility = "PUBLIC"
            ),
            TrendingPost(
                id = "ci-home-discovery-landscape",
                authorUsername = "@fynx_visual_fixture",
                authorDisplayName = "FYNX Visual Fixture",
                text = "CI TEST FIXTURE — Discovery landscape video. Local-only preview; not published or uploaded.",
                mediaId = "ci-home-landscape",
                mediaType = "video",
                timestamp = now - 1_000L,
                likeCount = 9,
                commentCount = 2,
                shareCount = 0,
                saveCount = 0,
                score = 90.0,
                authorId = "ci-local-fixture-author",
                visibility = "PUBLIC"
            )
        )
    }

    suspend fun trending(context: Context, limit: Int = 30, offset: Int = 0): Result<List<TrendingPost>> =
        FynxBackendClient.get(context, "/api/discovery/trending?limit=${limit.coerceIn(30, 50)}&offset=${offset.coerceAtLeast(0)}").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("posts") ?: JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(TrendingPost(o.optString("id"), o.optString("authorUsername"), o.optString("authorDisplayName"), o.optString("text"), o.optString("mediaId").takeIf { it.isNotBlank() && it != "null" }, o.optString("mediaType").takeIf { it.isNotBlank() && it != "null" }, o.optDouble("timestamp").toLong(), o.optInt("likeCount"), o.optInt("commentCount"), o.optInt("shareCount"), o.optInt("saveCount"), o.optDouble("discoveryScore"), authorId = o.optString("authorId"), visibility = o.optString("visibility", "PUBLIC"), likedByCurrentUser = o.optBoolean("likedByCurrentUser")))
                }
            }
        }

    data class MarketplaceDiscoveryPage(val items: List<FynxMarketplaceClient.Listing>, val hasMore: Boolean)

    suspend fun marketplaceDiscoveryPage(context: Context, query: String = "", category: String = "All", limit: Int = 30, offset: Int = 0, location: String = ""): Result<MarketplaceDiscoveryPage> {
        val path = "/api/marketplace/discovery?q=${encode(query)}&category=${encode(category)}&limit=${limit.coerceIn(1, 60)}&offset=${offset.coerceAtLeast(0)}&location=${encode(location)}"
        return FynxBackendClient.get(context, path).mapCatching { raw ->
            val json = JSONObject(raw)
            MarketplaceDiscoveryPage(FynxMarketplaceClient.parseListings(raw, preferAvailableStock = true), json.optBoolean("hasMore", false))
        }
    }

    suspend fun marketplaceDiscovery(context: Context, query: String = "", category: String = "All", limit: Int = 30, location: String = ""): Result<List<FynxMarketplaceClient.Listing>> =
        marketplaceDiscoveryPage(context, query, category, limit, 0, location).map { it.items }

    data class TrendingPost(val id: String, val authorUsername: String, val authorDisplayName: String, val text: String, val mediaId: String?, val mediaType: String?, val timestamp: Long, val likeCount: Int, val commentCount: Int, val shareCount: Int, val saveCount: Int, val score: Double, val authorId: String = "", val visibility: String = "PUBLIC", val likedByCurrentUser: Boolean = false)

    private fun encode(value: String): String = URLEncoder.encode(value.trim(), "UTF-8")
    private val EVENT_TYPES = setOf("VIEW", "LIKE", "COMMENT", "SHARE", "SAVE", "FOLLOW", "PROFILE_VIEW", "PRODUCT_CLICK", "MESSAGE", "PURCHASE", "INTERESTED", "NOT_INTERESTED", "REPORT")
}
