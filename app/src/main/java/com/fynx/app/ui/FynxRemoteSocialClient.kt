package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

object FynxRemoteSocialClient {
    data class RemotePost(
        val id: String,
        val authorId: String,
        val authorUsername: String,
        val authorDisplayName: String,
        val text: String,
        val visibility: String,
        val mediaId: String?,
        val mediaType: String?,
        val mediaUrl: String?,
        val timestamp: Long,
        val likeCount: Int,
        val commentCount: Int,
        val likedByCurrentUser: Boolean,
        val followedByCurrentUser: Boolean
    )

    data class FeedPage(val posts: List<RemotePost>, val hasMore: Boolean)

    data class RemoteComment(
        val id: String,
        val text: String,
        val timestamp: Long,
        val authorId: String,
        val authorUsername: String,
        val authorDisplayName: String
    )

    data class RemoteUser(
        val id: String,
        val username: String,
        val displayName: String
    )

    data class MarketplaceListing(
        val id: String,
        val sellerId: String,
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

    data class MarketplaceOrder(
        val id: String,
        val buyerId: String,
        val sellerId: String,
        val listingId: String,
        val quantity: Int,
        val unitPrice: Double,
        val deliveryFee: Double,
        val totalAmount: Double,
        val currency: String,
        val productTitle: String,
        val sellerUsername: String?,
        val status: String,
        val trackingReference: String?,
        val fulfillmentMethod: String,
        val shippingAddress: JSONObject?,
        val buyerNote: String,
        val inspectionDeadline: String?,
        val deliveryAvailable: Boolean,
        val pickupAvailable: Boolean
    )

    private const val FEED_PAGE_SIZE = 20
    private const val FEED_CACHE_TTL_MS = 120_000L
    private const val FEED_CACHE_KEY = "fynx_feed_cache_v1"
    private const val FEED_CACHE_TIME_KEY = "fynx_feed_cache_time_v1"

    suspend fun feed(context: Context): Result<List<RemotePost>> =
        feedPage(context, FEED_PAGE_SIZE, 0, useCache = true).map { it.posts }

    suspend fun feedPage(context: Context, limit: Int = FEED_PAGE_SIZE, offset: Int = 0, useCache: Boolean = false): Result<FeedPage> {
        val safeLimit = limit.coerceIn(1, FEED_PAGE_SIZE)
        val safeOffset = offset.coerceAtLeast(0)
        if (useCache && safeOffset == 0) readCachedFeed(context)?.let { return Result.success(it) }
        val remote = FynxBackendClient.get(context, "/api/social/feed?limit=$safeLimit&offset=$safeOffset").mapCatching { raw ->
            val page = parseFeedPage(raw)
            if (safeOffset == 0) writeCachedFeed(context, raw)
            page
        }
        if (safeOffset == 0 && remote.isFailure) readStaleCachedFeed(context)?.let { return Result.success(it) }
        return remote
    }

    private fun parseFeedPage(raw: String): FeedPage {
        val root = JSONObject(raw)
        val array = root.optJSONArray("posts") ?: JSONArray()
        val posts = buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(RemotePost(o.optString("id"), o.optString("authorId"), o.optString("authorUsername"), o.optString("authorDisplayName"), o.optString("text"), o.optString("visibility"), o.optString("mediaId").takeIf { it.isNotBlank() && it != "null" }, o.optString("mediaType").takeIf { it.isNotBlank() && it != "null" }, o.optString("mediaUrl").takeIf { it.isNotBlank() }, o.optDouble("timestamp", 0.0).toLong(), o.optInt("likeCount"), o.optInt("commentCount"), o.optBoolean("likedByCurrentUser"), o.optBoolean("followedByCurrentUser")))
            }
        }
        return FeedPage(posts, root.optBoolean("hasMore", posts.size >= FEED_PAGE_SIZE))
    }

    private fun readCachedFeed(context: Context): FeedPage? = runCatching {
        val prefs = context.getSharedPreferences("fynx_feed_cache", Context.MODE_PRIVATE)
        val savedAt = prefs.getLong(FEED_CACHE_TIME_KEY, 0L)
        val raw = prefs.getString(FEED_CACHE_KEY, null) ?: return null
        if (System.currentTimeMillis() - savedAt > FEED_CACHE_TTL_MS) return null
        parseFeedPage(raw)
    }.getOrNull()

    private fun readStaleCachedFeed(context: Context): FeedPage? = runCatching {
        val prefs = context.getSharedPreferences("fynx_feed_cache", Context.MODE_PRIVATE)
        val raw = prefs.getString(FEED_CACHE_KEY, null) ?: return null
        parseFeedPage(raw)
    }.getOrNull()

    private fun writeCachedFeed(context: Context, raw: String) {
        runCatching { context.getSharedPreferences("fynx_feed_cache", Context.MODE_PRIVATE).edit().putString(FEED_CACHE_KEY, raw).putLong(FEED_CACHE_TIME_KEY, System.currentTimeMillis()).apply() }
    }

    suspend fun createPost(context: Context, text: String, visibility: FynxPostVisibility, uri: Uri?): Result<Unit> = runCatching {
        val media = uri?.let { sourceUri ->
            val mime = mediaMimeType(context, sourceUri)
            val type = when { mime.startsWith("image/") -> "image"; mime.startsWith("video/") -> "video"; mime.startsWith("audio/") -> "audio"; else -> throw IllegalArgumentException("Select an image, video or audio.") }
            FynxProductionMessaging.uploadMedia(context, sourceUri, mime).getOrThrow() to type
        }
        FynxBackendClient.postJson(context, "/api/social/posts", JSONObject().apply { put("text", text.trim()); put("visibility", visibility.name); put("mediaId", media?.first?.id ?: JSONObject.NULL); put("mediaType", media?.second ?: JSONObject.NULL) }.toString()).getOrThrow()
        Unit
    }

    private fun mediaMimeType(context: Context, uri: Uri): String {
        context.contentResolver.getType(uri)?.lowercase()?.takeIf { it.isNotBlank() }?.let { return it }
        val path = uri.path?.lowercase().orEmpty()
        return when { path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"; path.endsWith(".png") -> "image/png"; path.endsWith(".webp") -> "image/webp"; path.endsWith(".heic") || path.endsWith(".heif") -> "image/heif"; path.endsWith(".mp4") -> "video/mp4"; path.endsWith(".3gp") -> "video/3gpp"; path.endsWith(".webm") -> "video/webm"; path.endsWith(".m4a") -> "audio/mp4"; path.endsWith(".aac") -> "audio/aac"; path.endsWith(".mp3") -> "audio/mpeg"; path.endsWith(".wav") -> "audio/wav"; else -> "" }
    }

    suspend fun addComment(context: Context, id: String, text: String): Result<RemoteComment> {
        val numericId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        return FynxBackendClient.postJson(context, "/api/social/posts/$numericId/comments", JSONObject().put("text", text.trim()).toString()).mapCatching {
            val o = JSONObject(it).getJSONObject("comment")
            RemoteComment(o.optString("id"), o.optString("text"), o.optDouble("timestamp").toLong(), o.optString("authorId"), o.optString("authorUsername"), o.optString("authorDisplayName"))
        }
    }
}
