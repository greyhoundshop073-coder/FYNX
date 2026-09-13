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
        val authorDisplayName: String,
        val parentCommentId: String? = null
    )

    data class CommentPage(val comments: List<RemoteComment>, val nextCursor: String?)

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
    private const val FEED_CACHE_KEY_PREFIX = "fynx_feed_cache_v1_"
    private const val FEED_CACHE_TIME_KEY_PREFIX = "fynx_feed_cache_time_v1_"

    private fun feedCacheAccountKey(context: Context): String? =
        FynxAuthStore.accountStorageKey(context)?.takeIf { it.isNotBlank() }

    private fun feedCacheKey(context: Context): String? =
        feedCacheAccountKey(context)?.let { FEED_CACHE_KEY_PREFIX + it }

    private fun feedCacheTimeKey(context: Context): String? =
        feedCacheAccountKey(context)?.let { FEED_CACHE_TIME_KEY_PREFIX + it }

    suspend fun feed(context: Context): Result<List<RemotePost>> =
        feedPage(context, FEED_PAGE_SIZE, 0, useCache = true).map { it.posts }

    suspend fun feedPage(
        context: Context,
        limit: Int = FEED_PAGE_SIZE,
        offset: Int = 0,
        useCache: Boolean = false
    ): Result<FeedPage> {
        val safeLimit = limit.coerceIn(1, FEED_PAGE_SIZE)
        val safeOffset = offset.coerceAtLeast(0)
        if (useCache && safeOffset == 0) {
            readCachedFeed(context)?.let { return Result.success(it) }
        }
        val remote = FynxBackendClient.get(
            context,
            "/api/social/feed?limit=$safeLimit&offset=$safeOffset"
        ).mapCatching { raw ->
            val page = parseFeedPage(raw)
            if (safeOffset == 0) writeCachedFeed(context, raw)
            page
        }
        if (safeOffset == 0 && remote.isFailure) {
            readStaleCachedFeed(context)?.let { return Result.success(it) }
        }
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
        val cacheKey = feedCacheKey(context) ?: return null
        val timeKey = feedCacheTimeKey(context) ?: return null
        val prefs = context.getSharedPreferences("fynx_feed_cache", Context.MODE_PRIVATE)
        val savedAt = prefs.getLong(timeKey, 0L)
        val raw = prefs.getString(cacheKey, null) ?: return null
        if (System.currentTimeMillis() - savedAt > FEED_CACHE_TTL_MS) return null
        parseFeedPage(raw)
    }.getOrNull()

    private fun readStaleCachedFeed(context: Context): FeedPage? = runCatching {
        val cacheKey = feedCacheKey(context) ?: return null
        val prefs = context.getSharedPreferences("fynx_feed_cache", Context.MODE_PRIVATE)
        val raw = prefs.getString(cacheKey, null) ?: return null
        parseFeedPage(raw)
    }.getOrNull()

    private fun writeCachedFeed(context: Context, raw: String) { runCatching { val cacheKey = feedCacheKey(context) ?: return; val timeKey = feedCacheTimeKey(context) ?: return; context.getSharedPreferences("fynx_feed_cache", Context.MODE_PRIVATE).edit().putString(cacheKey, raw).putLong(timeKey, System.currentTimeMillis()).apply() } }

    suspend fun createPost(context: Context, text: String, visibility: FynxPostVisibility, uri: Uri?): Result<Unit> = runCatching {
        val media = uri?.let { sourceUri ->
            val mime = mediaMimeType(context, sourceUri)
            val type = when { mime.startsWith("image/") -> "image"; mime.startsWith("video/") -> "video"; mime.startsWith("audio/") -> "audio"; else -> throw IllegalArgumentException("Select an image, video or audio.") }
            FynxProductionMessaging.uploadMedia(context, sourceUri, mime).getOrThrow() to type
        }
        FynxBackendClient.postJson(context, "/api/social/posts", JSONObject().apply { put("text", text.trim()); put("visibility", visibility.name); put("mediaId", media?.first?.id ?: JSONObject.NULL); put("mediaType", media?.second ?: JSONObject.NULL) }.toString()).getOrThrow()
        Unit
    }

    private fun mediaMimeType(context: Context, uri: Uri): String { context.contentResolver.getType(uri)?.lowercase()?.takeIf { it.isNotBlank() }?.let { return it }; val path = uri.path?.lowercase().orEmpty(); return when { path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"; path.endsWith(".png") -> "image/png"; path.endsWith(".webp") -> "image/webp"; path.endsWith(".heic") || path.endsWith(".heif") -> "image/heif"; path.endsWith(".mp4") -> "video/mp4"; path.endsWith(".3gp") -> "video/3gpp"; path.endsWith(".webm") -> "video/webm"; path.endsWith(".m4a") -> "audio/mp4"; path.endsWith(".aac") -> "audio/aac"; path.endsWith(".mp3") -> "audio/mpeg"; path.endsWith(".wav") -> "audio/wav"; else -> "" } }

    suspend fun createMarketplaceAd(context: Context, listingId: String, title: String, description: String, storeName: String, price: Double, currency: String, mediaId: String?): Result<Unit> { val priceText = "${currency.trim().uppercase()} ${String.format(Locale.US, "%,.2f", price)}"; val text = "[FYNX_MARKETPLACE_AD]\n🛍️ ${title.trim().take(120)}\nPrice: $priceText\nStore: ${storeName.trim().take(120)}\n${description.trim().take(1000)}\nListing ID: $listingId"; return FynxBackendClient.postJson(context, "/api/social/posts", JSONObject().apply { put("text", text.take(4000)); put("visibility", "PUBLIC"); put("mediaId", mediaId ?: JSONObject.NULL); put("mediaType", if (mediaId != null) "image" else JSONObject.NULL) }.toString()).map { Unit } }

    suspend fun listings(context: Context): Result<List<MarketplaceListing>> = FynxBackendClient.get(context, "/api/marketplace/listings").mapCatching { raw -> JSONArray(JSONObject(raw).optString("listings", "[]")) }.map { emptyList() }

    suspend fun deleteMarketplaceListing(context: Context, id: String): Result<Unit> = FynxBackendClient.delete(context, "/api/marketplace/listings/${id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid listing id"))}").map { Unit }

    suspend fun createMarketplaceOrder(context: Context, listingId: String, quantity: Int): Result<MarketplaceOrder> { val numericId = listingId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid listing id")); require(quantity > 0) { "Quantity must be at least 1." }; return FynxBackendClient.postJson(context, "/api/marketplace/orders", JSONObject().apply { put("listingId", numericId); put("quantity", quantity); put("orderId", java.util.UUID.randomUUID().toString()) }.toString()).mapCatching { parseOrder(JSONObject(it).getJSONObject("order")) } }

    suspend fun orders(context: Context): Result<List<MarketplaceOrder>> = FynxBackendClient.get(context, "/api/marketplace/orders").mapCatching { raw -> val array = JSONObject(raw).optJSONArray("orders") ?: JSONArray(); buildList { for (i in 0 until array.length()) add(parseOrder(array.getJSONObject(i))) } }

    private fun parseOrder(o: JSONObject): MarketplaceOrder { val product = o.optJSONObject("product"); return MarketplaceOrder(o.optString("id"), o.optString("buyerId"), o.optString("sellerId"), o.optString("listingId"), o.optInt("quantity"), o.optDouble("unitPrice"), o.optDouble("deliveryFee"), o.optDouble("totalAmount"), o.optString("currency", "NGN"), product?.optString("title").orEmpty().ifBlank { o.optString("productTitle") }, o.optString("sellerUsername").takeIf { it.isNotBlank() } ?: product?.optString("sellerUsername")?.takeIf { it.isNotBlank() }, o.optString("status"), o.optString("trackingReference").takeIf { it.isNotBlank() }, o.optString("fulfillmentMethod", "DELIVERY"), o.optJSONObject("shippingAddress"), o.optString("buyerNote"), o.optString("inspectionDeadline").takeIf { it.isNotBlank() }, product?.optBoolean("deliveryAvailable", false) ?: false, product?.optBoolean("pickupAvailable", false) ?: false) }

    suspend fun setMarketplaceFulfillment(context: Context, id: String, method: String, name: String = "", phone: String = "", address: String = "", city: String = "", state: String = "", country: String = "", buyerNote: String = ""): Result<Unit> { val safe = method.trim().uppercase(); require(safe == "DELIVERY" || safe == "PICKUP") { "Choose delivery or pickup." }; if (safe == "DELIVERY") require(name.trim().isNotBlank() && phone.trim().isNotBlank() && address.trim().isNotBlank()) { "Name, phone and delivery address are required." }; val shipping = if (safe == "DELIVERY") JSONObject().apply { put("name", name.trim().take(120)); put("phone", phone.trim().take(40)); put("address", address.trim().take(500)); put("city", city.trim().take(100)); put("state", state.trim().take(100)); put("country", country.trim().take(100)) } else JSONObject.NULL; return FynxBackendClient.postJson(context, "/api/marketplace/orders/$id/fulfillment", JSONObject().apply { put("method", safe); put("shippingAddress", shipping); put("buyerNote", buyerNote.trim().take(1000)) }.toString()).map { Unit } }

    suspend fun shipMarketplaceOrder(context: Context, id: String, trackingReference: String): Result<Unit> = FynxBackendClient.postJson(context, "/api/marketplace/orders/$id/ship", JSONObject().put("trackingReference", trackingReference.trim().take(160)).toString()).map { Unit }
    suspend fun confirmMarketplaceDelivery(context: Context, id: String): Result<Unit> = FynxBackendClient.postJson(context, "/api/marketplace/orders/$id/confirm-delivery", "{}").map { Unit }
    suspend fun completeMarketplaceOrder(context: Context, id: String): Result<Unit> = FynxBackendClient.postJson(context, "/api/marketplace/orders/$id/complete", "{}").map { Unit }
    suspend fun cancelMarketplaceOrder(context: Context, id: String): Result<Unit> = FynxBackendClient.postJson(context, "/api/marketplace/orders/$id/cancel", "{}").map { Unit }
    suspend fun disputeMarketplaceOrder(context: Context, id: String, reason: String, details: String): Result<Unit> { val safeReason = reason.trim().uppercase().takeIf { it in setOf("ITEM_NOT_RECEIVED", "WRONG_ITEM", "DAMAGED", "NOT_AS_DESCRIBED", "SUSPECTED_SCAM", "OTHER") } ?: "OTHER"; return FynxBackendClient.postJson(context, "/api/marketplace/orders/$id/disputes", JSONObject().apply { put("reason", safeReason); put("details", details.trim().take(4000)) }.toString()).map { Unit } }
    suspend fun reviewMarketplaceOrder(context: Context, id: String, rating: Int, comment: String): Result<Unit> { require(rating in 1..5) { "Rating must be between 1 and 5." }; return FynxBackendClient.postJson(context, "/api/marketplace/orders/$id/review", JSONObject().apply { put("rating", rating); put("comment", comment.trim().take(1000)) }.toString()).map { Unit } }

    suspend fun like(context: Context, id: String): Result<Pair<Boolean, Int>> { val numericId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id")); return FynxBackendClient.postJson(context, "/api/social/posts/$numericId/like", "{}").mapCatching { val o = JSONObject(it); o.optBoolean("liked") to o.optInt("likeCount") } }

    suspend fun comments(context: Context, id: String): Result<List<RemoteComment>> = commentsPage(context, id, null).map { it.comments }

    suspend fun commentsPage(context: Context, id: String, before: String?, limit: Int = 50): Result<CommentPage> {
        val numericId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        val safeLimit = limit.coerceIn(1, 100)
        val query = buildString { append("?limit=").append(safeLimit); if (!before.isNullOrBlank()) append("&before=").append(URLEncoder.encode(before, "UTF-8")) }
        return FynxBackendClient.get(context, "/api/social/posts/$numericId/comments/page$query").mapCatching { raw ->
            val root = JSONObject(raw); val array = root.optJSONArray("comments") ?: JSONArray()
            val parsed = buildList { for (i in 0 until array.length()) { val o = array.getJSONObject(i); add(RemoteComment(o.optString("id"), o.optString("text"), o.optDouble("timestamp").toLong(), o.optString("authorId"), o.optString("authorUsername"), o.optString("authorDisplayName"), o.optString("parentCommentId").takeIf { it.isNotBlank() && it != "null" })) } }
            CommentPage(parsed, root.optString("nextCursor").takeIf { it.isNotBlank() && it != "null" })
        }
    }

    suspend fun addComment(context: Context, id: String, text: String): Result<RemoteComment> {
        val numericId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        val safeText = text.trim().takeIf { it.isNotBlank() && it.length <= 1000 } ?: return Result.failure(IllegalArgumentException("Comment must be 1-1000 characters."))
        return FynxBackendClient.postJson(context, "/api/social/posts/$numericId/comments", JSONObject().put("text", safeText).toString()).mapCatching { parseRemoteComment(JSONObject(it).getJSONObject("comment")) }
    }

    suspend fun addReply(context: Context, postId: String, parentCommentId: String, text: String): Result<RemoteComment> {
        val post = postId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        val parent = parentCommentId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid parent comment id"))
        val safeText = text.trim().takeIf { it.isNotBlank() && it.length <= 1000 } ?: return Result.failure(IllegalArgumentException("Reply must be 1-1000 characters."))
        return FynxBackendClient.postJson(context, "/api/social/posts/$post/comments/$parent/replies", JSONObject().put("text", safeText).toString()).mapCatching { parseRemoteComment(JSONObject(it).getJSONObject("comment")) }
    }

    suspend fun replies(context: Context, postId: String, parentCommentId: String, limit: Int = 50): Result<List<RemoteComment>> {
        val post = postId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        val parent = parentCommentId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid parent comment id"))
        val safeLimit = limit.coerceIn(1, 100)
        return FynxBackendClient.get(context, "/api/social/posts/$post/comments/$parent/replies?limit=$safeLimit").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("comments") ?: JSONArray()
            buildList { for (i in 0 until array.length()) add(parseRemoteComment(array.getJSONObject(i))) }
        }
    }

    private fun parseRemoteComment(o: JSONObject) = RemoteComment(o.optString("id"), o.optString("text"), o.optDouble("timestamp").toLong(), o.optString("authorId"), o.optString("authorUsername"), o.optString("authorDisplayName"), o.optString("parentCommentId").takeIf { it.isNotBlank() && it != "null" })

    suspend fun likes(context: Context, id: String): Result<List<RemoteUser>> { val numericId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id")); return FynxBackendClient.get(context, "/api/social/posts/$numericId/likes").mapCatching { raw -> val array = JSONObject(raw).optJSONArray("users") ?: JSONArray(); buildList { for (i in 0 until array.length()) { val o = array.getJSONObject(i); add(RemoteUser(o.optString("id"), o.optString("username"), o.optString("displayName"))) } } }
}
