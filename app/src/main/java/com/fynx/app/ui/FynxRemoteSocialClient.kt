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

    data class RemoteComment(val id: String, val text: String, val timestamp: Long, val authorId: String, val authorUsername: String, val authorDisplayName: String)
    data class RemoteUser(val id: String, val username: String, val displayName: String)

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
        val trackingReference: String?
    )

    suspend fun feed(context: Context): Result<List<RemotePost>> =
        FynxBackendClient.get(context, "/api/social/feed?limit=100").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("posts") ?: JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(RemotePost(
                        id = o.optString("id"), authorId = o.optString("authorId"),
                        authorUsername = o.optString("authorUsername"), authorDisplayName = o.optString("authorDisplayName"),
                        text = o.optString("text"), visibility = o.optString("visibility"),
                        mediaId = o.optString("mediaId").takeIf { it.isNotBlank() && it != "null" },
                        mediaType = o.optString("mediaType").takeIf { it.isNotBlank() && it != "null" },
                        mediaUrl = o.optString("mediaUrl").takeIf { it.isNotBlank() },
                        timestamp = o.optDouble("timestamp", 0.0).toLong(), likeCount = o.optInt("likeCount"),
                        commentCount = o.optInt("commentCount"), likedByCurrentUser = o.optBoolean("likedByCurrentUser"),
                        followedByCurrentUser = o.optBoolean("followedByCurrentUser")
                    ))
                }
            }
        }

    suspend fun createPost(context: Context, text: String, visibility: FynxPostVisibility, uri: Uri?): Result<Unit> = runCatching {
        val media = uri?.let {
            val mime = mediaMimeType(context, it)
            val type = when {
                mime.startsWith("image/") -> "image"
                mime.startsWith("video/") -> "video"
                mime.startsWith("audio/") -> "audio"
                else -> throw IllegalArgumentException("Select an image, video or audio.")
            }
            FynxProductionMessaging.uploadMedia(context, it, mime).getOrThrow() to type
        }
        val body = JSONObject().apply {
            put("text", text.trim())
            put("visibility", visibility.name)
            put("mediaId", media?.first?.id ?: JSONObject.NULL)
            put("mediaType", media?.second ?: JSONObject.NULL)
        }
        FynxBackendClient.postJson(context, "/api/social/posts", body.toString()).getOrThrow()
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
        val body = JSONObject().apply {
            put("text", text.take(4000)); put("visibility", "PUBLIC")
            put("mediaId", mediaId ?: JSONObject.NULL); put("mediaType", if (mediaId != null) "image" else JSONObject.NULL)
        }
        return FynxBackendClient.postJson(context, "/api/social/posts", body.toString()).map { Unit }
    }

    suspend fun listings(context: Context, query: String = "", category: String = "All", seller: String = ""): Result<List<MarketplaceListing>> =
        FynxBackendClient.get(context, "/api/marketplace/listings?q=${URLEncoder.encode(query, "UTF-8")}&category=${URLEncoder.encode(category, "UTF-8")}&seller=${URLEncoder.encode(seller.removePrefix("@"), "UTF-8")}").mapCatching(::parseListings)

    suspend fun myListings(context: Context): Result<List<MarketplaceListing>> =
        FynxBackendClient.get(context, "/api/marketplace/my-listings").mapCatching(::parseListings)

    suspend fun createMarketplaceListing(
        context: Context, title: String, description: String, storeName: String, price: Double, currency: String,
        category: String, condition: String, quantity: Int, location: String, deliveryAvailable: Boolean,
        pickupAvailable: Boolean, deliveryFee: Double?, mediaUris: List<Uri>
    ): Result<MarketplaceListing?> = runCatching {
        require(title.trim().length >= 2) { "Product name is required." }
        require(description.trim().length >= 5) { "Add a product description." }
        require(price.isFinite() && price > 0) { "Enter a valid product price." }
        require(quantity > 0) { "Product quantity must be at least 1." }
        require(mediaUris.isNotEmpty()) { "Add at least one product photo or video." }
        val mediaIds = mediaUris.distinct().take(12).map { uri ->
            FynxProductionMessaging.uploadMedia(context, uri, mediaMimeType(context, uri)).getOrThrow().id
        }
        val body = JSONObject().apply {
            put("title", title.trim()); put("description", description.trim()); put("storeName", storeName.trim())
            put("price", price); put("currency", currency.trim().uppercase()); put("category", category.trim())
            put("condition", condition.trim().uppercase()); put("quantity", quantity); put("location", location.trim())
            put("deliveryAvailable", deliveryAvailable); put("pickupAvailable", pickupAvailable)
            put("deliveryFee", deliveryFee ?: JSONObject.NULL); put("mediaIds", JSONArray(mediaIds))
        }
        val raw = FynxBackendClient.postJson(context, "/api/marketplace/listings", body.toString()).getOrThrow()
        val id = JSONObject(raw).getJSONObject("listing").optString("id")
        listings(context).getOrNull()?.firstOrNull { it.id == id }
    }

    suspend fun deleteMarketplaceListing(context: Context, id: String): Result<Unit> {
        val numericId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid listing id"))
        return FynxBackendClient.delete(context, "/api/marketplace/listings/$numericId").map { Unit }
    }

    suspend fun createMarketplaceOrder(context: Context, listingId: String, quantity: Int): Result<MarketplaceOrder> {
        val numericId = listingId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid listing id"))
        return FynxBackendClient.postJson(context, "/api/marketplace/orders", JSONObject().apply {
            put("listingId", numericId); put("quantity", quantity); put("orderId", java.util.UUID.randomUUID().toString())
        }.toString()).mapCatching { parseOrder(JSONObject(it).getJSONObject("order")) }
    }

    suspend fun orders(context: Context): Result<List<MarketplaceOrder>> =
        FynxBackendClient.get(context, "/api/marketplace/orders").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("orders") ?: JSONArray()
            buildList { for (i in 0 until array.length()) add(parseOrder(array.getJSONObject(i))) }
        }

    private fun parseOrder(o: JSONObject): MarketplaceOrder {
        val product = o.optJSONObject("product")
        return MarketplaceOrder(
            id = o.optString("id"), buyerId = o.optString("buyerId"), sellerId = o.optString("sellerId"), listingId = o.optString("listingId"),
            quantity = o.optInt("quantity"), unitPrice = o.optDouble("unitPrice"), deliveryFee = o.optDouble("deliveryFee"),
            totalAmount = o.optDouble("totalAmount"), currency = o.optString("currency", "NGN"),
            productTitle = product?.optString("title").orEmpty().ifBlank { o.optString("productTitle") },
            sellerUsername = o.optString("sellerUsername").takeIf { it.isNotBlank() },
            status = o.optString("status"), trackingReference = o.optString("trackingReference").takeIf { it.isNotBlank() }
        )
    }

    suspend fun cancelMarketplaceOrder(context: Context, id: String): Result<Unit> =
        FynxBackendClient.postJson(context, "/api/marketplace/orders/$id/cancel", "{}").map { Unit }

    suspend fun disputeMarketplaceOrder(context: Context, id: String, reason: String, details: String): Result<Unit> =
        FynxBackendClient.postJson(context, "/api/marketplace/orders/$id/disputes", JSONObject().apply { put("reason", reason); put("details", details) }.toString()).map { Unit }

    suspend fun reviewMarketplaceOrder(context: Context, id: String, rating: Int, comment: String): Result<Unit> =
        FynxBackendClient.postJson(context, "/api/marketplace/orders/$id/review", JSONObject().apply { put("rating", rating); put("comment", comment) }.toString()).map { Unit }

    suspend fun like(context: Context, id: String): Result<Pair<Boolean, Int>> {
        val postId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        return FynxBackendClient.postJson(context, "/api/social/posts/$postId/like", "{}").mapCatching { val o = JSONObject(it); o.optBoolean("liked") to o.optInt("likeCount") }
    }

    suspend fun comments(context: Context, id: String): Result<List<RemoteComment>> {
        val postId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        return FynxBackendClient.get(context, "/api/social/posts/$postId/comments").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("comments") ?: JSONArray()
            buildList { for (i in 0 until array.length()) { val o = array.getJSONObject(i); add(RemoteComment(o.optString("id"), o.optString("text"), o.optDouble("timestamp").toLong(), o.optString("authorId"), o.optString("authorUsername"), o.optString("authorDisplayName"))) } }
        }
    }

    suspend fun addComment(context: Context, id: String, text: String): Result<RemoteComment> {
        val postId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        return FynxBackendClient.postJson(context, "/api/social/posts/$postId/comments", JSONObject().put("text", text.trim()).toString()).mapCatching { raw ->
            val o = JSONObject(raw).getJSONObject("comment")
            RemoteComment(o.optString("id"), o.optString("text"), o.optDouble("timestamp").toLong(), o.optString("authorId"), o.optString("authorUsername"), o.optString("authorDisplayName"))
        }
    }

    suspend fun likes(context: Context, id: String): Result<List<RemoteUser>> {
        val postId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        return FynxBackendClient.get(context, "/api/social/posts/$postId/likes").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("users") ?: JSONArray()
            buildList { for (i in 0 until array.length()) { val o = array.getJSONObject(i); add(RemoteUser(o.optString("id"), o.optString("username"), o.optString("displayName"))) } }
        }
    }

    suspend fun follow(context: Context, username: String, following: Boolean): Result<Boolean> {
        val encoded = URLEncoder.encode(username.trim().removePrefix("@"), "UTF-8")
        return if (following) FynxBackendClient.delete(context, "/api/social/follow/$encoded").map { false }
        else FynxBackendClient.postJson(context, "/api/social/follow/$encoded", "{}").map { true }
    }

    suspend fun deletePost(context: Context, id: String): Result<Unit> {
        val postId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        return FynxBackendClient.delete(context, "/api/social/posts/$postId").map { Unit }
    }

    private fun parseListings(raw: String): List<MarketplaceListing> {
        val array = JSONObject(raw).optJSONArray("listings") ?: JSONArray()
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val media = o.optJSONArray("media_ids") ?: JSONArray()
                val ids = buildList { for (j in 0 until media.length()) add(media.get(j).toString()) }
                add(MarketplaceListing(
                    id = o.optString("id"), sellerId = o.optString("seller_id"), sellerUsername = o.optString("seller_username"),
                    sellerDisplayName = o.optString("seller_display_name"), storeName = o.optString("store_name"), title = o.optString("title"),
                    description = o.optString("description"), price = o.optDouble("price"), currency = o.optString("currency", "NGN"),
                    category = o.optString("category"), condition = o.optString("condition", "NEW"), quantity = o.optInt("quantity"),
                    location = o.optString("location"), deliveryAvailable = o.optBoolean("delivery_available"),
                    pickupAvailable = o.optBoolean("pickup_available", true), deliveryFee = if (o.isNull("delivery_fee")) null else o.optDouble("delivery_fee"),
                    mediaIds = ids, active = o.optBoolean("active", true)
                ))
            }
        }
    }
}
