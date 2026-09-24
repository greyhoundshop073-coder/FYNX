package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

object FynxRemoteSocialClient {
    data class RemotePost(val id: String, val authorId: String, val authorUsername: String, val authorDisplayName: String, val text: String, val visibility: String, val mediaId: String?, val mediaType: String?, val mediaUrl: String?, val timestamp: Long, val likeCount: Int, val commentCount: Int, val likedByCurrentUser: Boolean, val followedByCurrentUser: Boolean, val isDiscovery: Boolean = false, val discoveryScore: Double = 0.0, val textBackground: String? = null, val textBackgroundColor: Long? = null, val textForegroundColor: Long? = null, val location: String? = null, val musicMediaId: String? = null, val musicTitle: String? = null, val musicArtist: String? = null, val musicDurationMs: Long = 0L, val feelingActivityType: String? = null, val feelingActivity: String? = null)
    data class FeedPage(val posts: List<RemotePost>, val hasMore: Boolean)
    data class RemoteComment(val id: String, val text: String, val timestamp: Long, val authorId: String, val authorUsername: String, val authorDisplayName: String, val parentCommentId: String? = null)
    data class CommentPage(val comments: List<RemoteComment>, val nextCursor: String?)
    data class RemoteUser(val id: String, val username: String, val displayName: String)
    data class MarketplaceListing(val id: String, val sellerId: String, val sellerUsername: String, val sellerDisplayName: String, val storeName: String, val title: String, val description: String, val price: Double, val currency: String, val category: String, val condition: String, val quantity: Int, val location: String, val deliveryAvailable: Boolean, val pickupAvailable: Boolean, val deliveryFee: Double?, val mediaIds: List<String>, val active: Boolean = true)
    data class MarketplaceOrder(val id: String, val buyerId: String, val sellerId: String, val listingId: String, val quantity: Int, val unitPrice: Double, val deliveryFee: Double, val totalAmount: Double, val currency: String, val productTitle: String, val sellerUsername: String?, val status: String, val trackingReference: String?, val fulfillmentMethod: String, val shippingAddress: JSONObject?, val buyerNote: String, val inspectionDeadline: String?, val deliveryAvailable: Boolean, val pickupAvailable: Boolean)
    data class SocialInteractionState(val saved: Boolean, val reposted: Boolean, val savedCount: Int, val repostCount: Int)

    private const val FEED_PAGE_SIZE = 20
    private const val FEED_CACHE_TTL_MS = 120_000L
    private const val FEED_CACHE_KEY_PREFIX = "fynx_feed_cache_v1_"
    private const val FEED_CACHE_TIME_KEY_PREFIX = "fynx_feed_cache_time_v1_"
    private fun feedCacheAccountKey(context: Context): String? = FynxAuthStore.accountStorageKey(context)?.takeIf { it.isNotBlank() }
    private fun feedCacheKey(context: Context): String? = feedCacheAccountKey(context)?.let { FEED_CACHE_KEY_PREFIX + it }
    private fun feedCacheTimeKey(context: Context): String? = feedCacheAccountKey(context)?.let { FEED_CACHE_TIME_KEY_PREFIX + it }

    suspend fun feed(context: Context): Result<List<RemotePost>> = feedPage(context, FEED_PAGE_SIZE, 0, true).map { it.posts }
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
                add(RemotePost(o.optString("id"), o.optString("authorId"), o.optString("authorUsername"), o.optString("authorDisplayName"), o.optString("text"), o.optString("visibility"), o.optString("mediaId").takeIf { it.isNotBlank() && it != "null" }, o.optString("mediaType").takeIf { it.isNotBlank() && it != "null" }, o.optString("mediaUrl").takeIf { it.isNotBlank() }, o.optDouble("timestamp", 0.0).toLong(), o.optInt("likeCount"), o.optInt("commentCount"), o.optBoolean("likedByCurrentUser"), o.optBoolean("followedByCurrentUser"), o.optBoolean("isDiscovery", false), o.optDouble("discoveryScore", 0.0), o.optString("textBackground").takeIf { it.isNotBlank() }, if (o.has("textBackgroundColor") && !o.isNull("textBackgroundColor")) o.optLong("textBackgroundColor") else null, if (o.has("textForegroundColor") && !o.isNull("textForegroundColor")) o.optLong("textForegroundColor") else null, o.optString("location").takeIf { it.isNotBlank() && it != "null" }, o.optString("musicMediaId").takeIf { it.isNotBlank() && it != "null" }, o.optString("musicTitle").takeIf { it.isNotBlank() && it != "null" }, o.optString("musicArtist").takeIf { it.isNotBlank() && it != "null" }, o.optLong("musicDurationMs", 0L), o.optString("feelingActivityType").takeIf { it.isNotBlank() && it != "null" }, o.optString("feelingActivity").takeIf { it.isNotBlank() && it != "null" }))
            }
        }
        return FeedPage(posts, root.optBoolean("hasMore", posts.size >= FEED_PAGE_SIZE))
    }
    private fun readCachedFeed(context: Context): FeedPage? = runCatching {
        val k = feedCacheKey(context) ?: return null
        val t = feedCacheTimeKey(context) ?: return null
        val p = context.getSharedPreferences("fynx_feed_cache", Context.MODE_PRIVATE)
        val saved = p.getLong(t, 0L)
        val raw = p.getString(k, null) ?: return null
        if (System.currentTimeMillis() - saved > FEED_CACHE_TTL_MS) return null
        parseFeedPage(raw)
    }.getOrNull()
    private fun readStaleCachedFeed(context: Context): FeedPage? = runCatching {
        val k = feedCacheKey(context) ?: return null
        val p = context.getSharedPreferences("fynx_feed_cache", Context.MODE_PRIVATE)
        parseFeedPage(p.getString(k, null) ?: return null)
    }.getOrNull()
    private fun writeCachedFeed(context: Context, raw: String) { runCatching { val k = feedCacheKey(context) ?: return; val t = feedCacheTimeKey(context) ?: return; context.getSharedPreferences("fynx_feed_cache", Context.MODE_PRIVATE).edit().putString(k, raw).putLong(t, System.currentTimeMillis()).apply() } }


    suspend fun discoveryFeed(context: Context, limit: Int = 12): Result<FeedPage> {
        val safeLimit = limit.coerceIn(1, 20)
        return FynxBackendClient.get(context, "/api/discovery/trending?limit=$safeLimit").mapCatching { raw -> parseFeedPage(raw) }
    }
    suspend fun createPost(context: Context, text: String, visibility: FynxPostVisibility, uri: Uri?): Result<Unit> = runCatching {
        val media = uri?.let { u ->
            val mime = mediaMimeType(context, u)
            val type = when {
                mime.startsWith("image/") -> "image"
                mime.startsWith("video/") -> "video"
                mime.startsWith("audio/") -> "audio"
                else -> throw IllegalArgumentException("Select an image, video or audio.")
            }
            FynxProductionMessaging.uploadMedia(context, u, mime).getOrThrow() to type
        }
        FynxBackendClient.postJson(context, "/api/social/posts", JSONObject().apply { put("text", text.trim()); put("visibility", visibility.name); put("mediaId", media?.first?.id ?: JSONObject.NULL); put("mediaType", media?.second ?: JSONObject.NULL) }.toString()).getOrThrow()
        Unit
    }
    private fun mediaMimeType(context: Context, uri: Uri): String {
        context.contentResolver.getType(uri)?.lowercase()?.takeIf { it.isNotBlank() }?.let { return it }
        val path = uri.path?.lowercase().orEmpty()
        return when {
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".heic") || path.endsWith(".heif") -> "image/heif"
            path.endsWith(".mp4") -> "video/mp4"
            path.endsWith(".3gp") -> "video/3gpp"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".m4a") -> "audio/mp4"
            path.endsWith(".aac") -> "audio/aac"
            path.endsWith(".mp3") -> "audio/mpeg"
            path.endsWith(".wav") -> "audio/wav"
            else -> ""
        }
    }
    suspend fun createMarketplaceAd(context: Context, listingId: String, title: String, description: String, storeName: String, price: Double, currency: String, mediaId: String?): Result<Unit> {
        val priceText = "${currency.trim().uppercase()} ${String.format(Locale.US, "%,.2f", price)}"
        val text = "[FYNX_MARKETPLACE_AD]\n🛍️ ${title.trim().take(120)}\nPrice: $priceText\nStore: ${storeName.trim().take(120)}\n${description.trim().take(1000)}\nListing ID: $listingId"
        return FynxBackendClient.postJson(context, "/api/social/posts", JSONObject().apply { put("text", text.take(4000)); put("visibility", "PUBLIC"); put("mediaId", mediaId ?: JSONObject.NULL); put("mediaType", if (mediaId != null) "image" else JSONObject.NULL) }.toString()).map { Unit }
    }
    suspend fun listings(context: Context, query: String = "", category: String = "All", seller: String = ""): Result<List<MarketplaceListing>> = FynxBackendClient.get(context, "/api/marketplace/listings?q=${URLEncoder.encode(query, "UTF-8")}&category=${URLEncoder.encode(category, "UTF-8")}&seller=${URLEncoder.encode(seller.removePrefix("@"), "UTF-8")}").mapCatching(::parseListings)
    suspend fun nearbyMarketplaceListings(context: Context, query: String = "", category: String = "All", location: String): Result<List<MarketplaceListing>> = FynxBackendClient.get(context, "/api/marketplace/discovery?q=${URLEncoder.encode(query, "UTF-8")}&category=${URLEncoder.encode(category, "UTF-8")}&limit=60&location=${URLEncoder.encode(location.trim(), "UTF-8")}").mapCatching(::parseListings)
    suspend fun myListings(context: Context): Result<List<MarketplaceListing>> = FynxBackendClient.get(context, "/api/marketplace/my-listings").mapCatching(::parseListings)
    suspend fun createMarketplaceListing(context: Context, title: String, description: String, storeName: String, price: Double, currency: String, category: String, condition: String, quantity: Int, location: String, deliveryAvailable: Boolean, pickupAvailable: Boolean, deliveryFee: Double?, mediaUris: List<Uri>): Result<MarketplaceListing?> = runCatching {
        require(title.trim().length >= 2) { "Product name is required." }; require(description.trim().length >= 5) { "Add a product description." }; require(price.isFinite() && price > 0) { "Enter a valid product price." }; require(quantity > 0) { "Product quantity must be at least 1." }; require(mediaUris.isNotEmpty()) { "Add at least one product photo or video." }
        val mediaIds = mediaUris.distinct().take(12).map { u -> val mime = mediaMimeType(context, u); require(mime.startsWith("image/") || mime.startsWith("video/")) { "Marketplace media must be an image or video." }; FynxProductionMessaging.uploadMedia(context, u, mime).getOrThrow().id }
        val raw = FynxBackendClient.postJson(context, "/api/marketplace/listings", JSONObject().apply { put("title", title.trim()); put("description", description.trim()); put("storeName", storeName.trim()); put("price", price); put("currency", currency.trim().uppercase()); put("category", category.trim()); put("condition", condition.trim().uppercase()); put("quantity", quantity); put("location", location.trim()); put("deliveryAvailable", deliveryAvailable); put("pickupAvailable", pickupAvailable); put("deliveryFee", deliveryFee ?: JSONObject.NULL); put("mediaIds", JSONArray(mediaIds)) }.toString()).getOrThrow()
        val id = JSONObject(raw).getJSONObject("listing").optString("id")
        listings(context).getOrNull()?.firstOrNull { it.id == id }
    }
    suspend fun deleteMarketplaceListing(context: Context, id: String): Result<Unit> { val numericId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid listing id")); return FynxBackendClient.delete(context, "/api/marketplace/listings/$numericId").map { Unit } }
    suspend fun createMarketplaceOrder(context: Context, listingId: String, quantity: Int): Result<MarketplaceOrder> { val numericId = listingId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid listing id")); require(quantity > 0) { "Quantity must be at least 1." }; return FynxBackendClient.postJson(context, "/api/marketplace/orders", JSONObject().apply { put("listingId", numericId); put("quantity", quantity); put("orderId", java.util.UUID.randomUUID().toString()) }.toString()).mapCatching { parseOrder(JSONObject(it).getJSONObject("order")) } }
    suspend fun orders(context: Context): Result<List<MarketplaceOrder>> = FynxBackendClient.get(context, "/api/marketplace/orders").mapCatching { raw -> val a = JSONObject(raw).optJSONArray("orders") ?: JSONArray(); buildList { for (i in 0 until a.length()) add(parseOrder(a.getJSONObject(i))) } }
    private fun parseOrder(o: JSONObject): MarketplaceOrder { val p = o.optJSONObject("product"); return MarketplaceOrder(o.optString("id"), o.optString("buyerId"), o.optString("sellerId"), o.optString("listingId"), o.optInt("quantity"), o.optDouble("unitPrice"), o.optDouble("deliveryFee"), o.optDouble("totalAmount"), o.optString("currency", "NGN"), p?.optString("title").orEmpty().ifBlank { o.optString("productTitle") }, o.optString("sellerUsername").takeIf { it.isNotBlank() } ?: p?.optString("sellerUsername")?.takeIf { it.isNotBlank() }, o.optString("status"), o.optString("trackingReference").takeIf { it.isNotBlank() }, o.optString("fulfillmentMethod", "DELIVERY"), o.optJSONObject("shippingAddress"), o.optString("buyerNote"), o.optString("inspectionDeadline").takeIf { it.isNotBlank() }, p?.optBoolean("deliveryAvailable", false) ?: false, p?.optBoolean("pickupAvailable", false) ?: false) }
    suspend fun setMarketplaceFulfillment(context: Context, id: String, method: String, name: String = "", phone: String = "", address: String = "", city: String = "", state: String = "", country: String = "", buyerNote: String = ""): Result<Unit> { val safe = method.trim().uppercase(); require(safe == "DELIVERY" || safe == "PICKUP") { "Choose delivery or pickup." }; if (safe == "DELIVERY") require(name.trim().isNotBlank() && phone.trim().isNotBlank() && address.trim().isNotBlank()) { "Name, phone and delivery address are required." }; val shipping = if (safe == "DELIVERY") JSONObject().apply { put("name", name.trim().take(120)); put("phone", phone.trim().take(40)); put("address", address.trim().take(500)); put("city", city.trim().take(100)); put("state", state.trim().take(100)); put("country", country.trim().take(100)) } else JSONObject.NULL; return FynxBackendClient.postJson(context, "/api/marketplace/orders/$id/fulfillment", JSONObject().apply { put("method", safe); put("shippingAddress", shipping); put("buyerNote", buyerNote.trim().take(1000)) }.toString()).map { Unit } }
    suspend fun shipMarketplaceOrder(context: Context, id: String, trackingReference: String): Result<Unit> = FynxBackendClient.postJson(context, "/api/marketplace/orders/$id/ship", JSONObject().put("trackingReference", trackingReference.trim().take(160)).toString()).map { Unit }
    suspend fun confirmMarketplaceDelivery(context: Context, id: String): Result<Unit> = FynxBackendClient.postJson(context, "/api/marketplace/orders/$id/confirm-delivery", "{}").map { Unit }
    suspend fun completeMarketplaceOrder(context: Context, id: String, receivedItemMatchesOrder: Boolean, quantityMatchesOrder: Boolean): Result<Unit> {
        require(receivedItemMatchesOrder && quantityMatchesOrder) { "Confirm that the received item and quantity match the order." }
        val body = JSONObject()
            .put("receivedItemMatchesOrder", receivedItemMatchesOrder)
            .put("quantityMatchesOrder", quantityMatchesOrder)
            .toString()
        return FynxBackendClient.postJson(context, "/api/marketplace/orders/$id/complete", body).map { Unit }
    }
    suspend fun cancelMarketplaceOrder(context: Context, id: String): Result<Unit> = FynxBackendClient.postJson(context, "/api/marketplace/orders/$id/cancel", "{}").map { Unit }
    suspend fun disputeMarketplaceOrder(context: Context, id: String, reason: String, details: String): Result<Unit> { val safeReason = reason.trim().uppercase().takeIf { it in setOf("ITEM_NOT_RECEIVED", "WRONG_ITEM", "DAMAGED", "NOT_AS_DESCRIBED", "SUSPECTED_SCAM", "OTHER") } ?: "OTHER"; return FynxBackendClient.postJson(context, "/api/marketplace/orders/$id/disputes", JSONObject().apply { put("reason", safeReason); put("details", details.trim().take(4000)) }.toString()).map { Unit } }
    suspend fun reviewMarketplaceOrder(context: Context, id: String, rating: Int, comment: String): Result<Unit> { require(rating in 1..5) { "Rating must be between 1 and 5." }; return FynxBackendClient.postJson(context, "/api/marketplace/orders/$id/review", JSONObject().apply { put("rating", rating); put("comment", comment.trim().take(1000)) }.toString()).map { Unit } }

    suspend fun like(context: Context, id: String): Result<Pair<Boolean, Int>> { val numericId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id")); return FynxBackendClient.postJson(context, "/api/social/posts/$numericId/like", "{}").mapCatching { val o = JSONObject(it); o.optBoolean("liked") to o.optInt("likeCount") } }
    suspend fun save(context: Context, id: String, saved: Boolean): Result<Pair<Boolean, Int>> {
        val numericId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        val path = "/api/social/posts/$numericId/save"
        return if (saved) FynxBackendClient.postJson(context, path, "{}").mapCatching { val o = JSONObject(it); o.optBoolean("saved", true) to o.optInt("savedCount") }
        else FynxBackendClient.delete(context, path).mapCatching { val o = JSONObject(it); o.optBoolean("saved", false) to o.optInt("savedCount") }
    }
    suspend fun repost(context: Context, id: String, reposted: Boolean): Result<Pair<Boolean, Int>> {
        val numericId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        val path = "/api/social/posts/$numericId/repost"
        return if (reposted) FynxBackendClient.postJson(context, path, "{}").mapCatching { val o = JSONObject(it); o.optBoolean("reposted", true) to o.optInt("repostCount") }
        else FynxBackendClient.delete(context, path).mapCatching { val o = JSONObject(it); o.optBoolean("reposted", false) to o.optInt("repostCount") }
    }
    suspend fun interactionState(context: Context, id: String): Result<SocialInteractionState> {
        val numericId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        return FynxBackendClient.get(context, "/api/social/posts/$numericId/interaction-state").mapCatching { raw ->
            val o = JSONObject(raw)
            SocialInteractionState(o.optBoolean("saved"), o.optBoolean("reposted"), o.optInt("savedCount"), o.optInt("repostCount"))
        }
    }
    suspend fun comments(context: Context, id: String): Result<List<RemoteComment>> = commentsPage(context, id, null).map { it.comments }
    suspend fun commentsPage(context: Context, id: String, before: String?, limit: Int = 50): Result<CommentPage> {
        val numericId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        val safe = limit.coerceIn(1, 100)
        val query = "?limit=$safe" + (if (before.isNullOrBlank()) "" else "&before=${URLEncoder.encode(before, "UTF-8")}")
        return FynxBackendClient.get(context, "/api/social/posts/$numericId/comments/page$query").mapCatching { raw ->
            val root = JSONObject(raw); val a = root.optJSONArray("comments") ?: JSONArray()
            val list = buildList { for (i in 0 until a.length()) add(parseRemoteComment(a.getJSONObject(i))) }
            CommentPage(list, root.optString("nextCursor").takeIf { it.isNotBlank() && it != "null" })
        }
    }
    suspend fun addComment(context: Context, id: String, text: String): Result<RemoteComment> {
        val numericId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        val safe = text.trim().takeIf { it.isNotBlank() && it.length <= 1000 } ?: return Result.failure(IllegalArgumentException("Comment must be 1-1000 characters."))
        return FynxBackendClient.postJson(context, "/api/social/posts/$numericId/comments", JSONObject().put("text", safe).toString()).mapCatching { parseRemoteComment(JSONObject(it).getJSONObject("comment")) }
    }
    suspend fun addReply(context: Context, postId: String, parentCommentId: String, text: String): Result<RemoteComment> {
        val post = postId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        val parent = parentCommentId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid parent comment id"))
        val safe = text.trim().takeIf { it.isNotBlank() && it.length <= 1000 } ?: return Result.failure(IllegalArgumentException("Reply must be 1-1000 characters."))
        return FynxBackendClient.postJson(context, "/api/social/posts/$post/comments/$parent/replies", JSONObject().put("text", safe).toString()).mapCatching { parseRemoteComment(JSONObject(it).getJSONObject("comment")) }
    }
    suspend fun replies(context: Context, postId: String, parentCommentId: String, limit: Int = 50): Result<List<RemoteComment>> {
        val post = postId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        val parent = parentCommentId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid parent comment id"))
        val safe = limit.coerceIn(1, 100)
        return FynxBackendClient.get(context, "/api/social/posts/$post/comments/$parent/replies?limit=$safe").mapCatching { raw -> val a = JSONObject(raw).optJSONArray("comments") ?: JSONArray(); buildList { for (i in 0 until a.length()) add(parseRemoteComment(a.getJSONObject(i))) } }
    }
    private fun parseRemoteComment(o: JSONObject) = RemoteComment(o.optString("id"), o.optString("text"), o.optDouble("timestamp").toLong(), o.optString("authorId"), o.optString("authorUsername"), o.optString("authorDisplayName"), o.optString("parentCommentId").takeIf { it.isNotBlank() && it != "null" })
    suspend fun likes(context: Context, id: String): Result<List<RemoteUser>> { val numericId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id")); return FynxBackendClient.get(context, "/api/social/posts/$numericId/likes").mapCatching { raw -> val a = JSONObject(raw).optJSONArray("users") ?: JSONArray(); buildList { for (i in 0 until a.length()) { val o = a.getJSONObject(i); add(RemoteUser(o.optString("id"), o.optString("username"), o.optString("displayName"))) } } } }
    suspend fun follow(context: Context, username: String, following: Boolean): Result<Boolean> { val encoded = URLEncoder.encode(username.trim().removePrefix("@"), "UTF-8"); return if (following) FynxBackendClient.delete(context, "/api/social/follow/$encoded").map { false } else FynxBackendClient.postJson(context, "/api/social/follow/$encoded", "{}").map { true } }
    suspend fun deletePost(context: Context, id: String): Result<Unit> { val numericId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id")); return FynxBackendClient.delete(context, "/api/social/posts/$numericId").map { Unit } }
    private fun parseListings(raw: String): List<MarketplaceListing> { val a = JSONObject(raw).optJSONArray("listings") ?: JSONArray(); return buildList { for (i in 0 until a.length()) { val o = a.getJSONObject(i); val ma = o.optJSONArray("media_ids") ?: JSONArray(); val ids = buildList { for (j in 0 until ma.length()) add(ma.get(j).toString()) }; add(MarketplaceListing(o.optString("id"), o.optString("seller_id"), o.optString("seller_username"), o.optString("seller_display_name"), o.optString("store_name"), o.optString("title"), o.optString("description"), o.optDouble("price"), o.optString("currency", "NGN"), o.optString("category"), o.optString("condition", "NEW"), o.optInt("quantity"), o.optString("location"), o.optBoolean("delivery_available"), o.optBoolean("pickup_available", true), if (o.isNull("delivery_fee")) null else o.optDouble("delivery_fee"), ids, o.optBoolean("active", true))) } } }
}