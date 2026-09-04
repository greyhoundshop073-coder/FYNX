package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

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

    suspend fun feed(context: Context): Result<List<RemotePost>> = FynxBackendClient.get(context, "/api/social/feed?limit=100").mapCatching { raw ->
        val array = JSONObject(raw).optJSONArray("posts") ?: JSONArray()
        buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(RemotePost(o.optString("id"), o.optString("authorId"), o.optString("authorUsername"), o.optString("authorDisplayName"), o.optString("text"), o.optString("visibility"), o.optString("mediaId").takeIf { it.isNotBlank() && it != "null" }, o.optString("mediaType").takeIf { it.isNotBlank() && it != "null" }, o.optString("mediaUrl").takeIf { it.isNotBlank() }, o.optDouble("timestamp").toLong(), o.optInt("likeCount"), o.optInt("commentCount"), o.optBoolean("likedByCurrentUser"), o.optBoolean("followedByCurrentUser")))
            }
        }
    }

    suspend fun createPost(context: Context, text: String, visibility: FynxPostVisibility, uri: Uri?): Result<Unit> = runCatching {
        val media = uri?.let {
            val mime = context.contentResolver.getType(it)?.lowercase() ?: ""
            val type = when {
                mime.startsWith("image/") -> "image"
                mime.startsWith("video/") -> "video"
                else if (mime.startsWith("audio/")) -> "audio"
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

    suspend fun createMarketplaceAd(context: Context, listingId: String, title: String, description: String, storeName: String, price: Double, currency: String, mediaId: String?): Result<Unit> {
        val cleanTitle = title.trim().take(120)
        val cleanDescription = description.trim().take(1000)
        val cleanStore = storeName.trim().take(120)
        val priceText = "${currency.trim().uppercase()} ${String.format(java.util.Locale.US, "%,.2f", price)}"
        val adText = buildString {
            append("[FYNX_MARKETPLACE_AD]\n")
            append("🛍️ $cleanTitle\n")
            append("Price: $priceText\n")
            if (cleanStore.isNotBlank()) append("Store: $cleanStore\n")
            if (cleanDescription.isNotBlank()) append(cleanDescription)
            append("\nListing ID: $listingId")
        }
        val body = JSONObject().apply {
            put("text", adText.take(4000))
            put("visibility", FynxPostVisibility.PUBLIC.name)
            put("mediaId", mediaId ?: JSONObject.NULL)
            put("mediaType", if (mediaId != null) "image" else JSONObject.NULL)
        }
        return FynxBackendClient.postJson(context, "/api/social/posts", body.toString()).map { Unit }
    }

    suspend fun like(context: Context, id: String): Result<Pair<Boolean, Int>> {
        val postId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        return FynxBackendClient.postJson(context, "/api/social/posts/$postId/like", "{}").mapCatching {
            val o = JSONObject(it)
            o.optBoolean("liked") to o.optInt("likeCount")
        }
    }

    suspend fun comments(context: Context, id: String): Result<List<RemoteComment>> {
        val postId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        return FynxBackendClient.get(context, "/api/social/posts/$postId/comments").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("comments") ?: JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(RemoteComment(o.optString("id"), o.optString("text"), o.optDouble("timestamp").toLong(), o.optString("authorId"), o.optString("authorUsername"), o.optString("authorDisplayName")))
                }
            }
        }
    }

    suspend fun addComment(context: Context, id: String, text: String): Result<RemoteComment> {
        val postId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        return FynxBackendClient.postJson(context, "/api/social/posts/$postId/comments", JSONObject().put("text", text.trim()).toString()).mapCatching {
            val o = JSONObject(it).getJSONObject("comment")
            RemoteComment(o.optString("id"), o.optString("text"), o.optDouble("timestamp").toLong(), o.optString("authorId"), o.optString("authorUsername"), o.optString("authorDisplayName"))
        }
    }

    suspend fun likes(context: Context, id: String): Result<List<RemoteUser>> {
        val postId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        return FynxBackendClient.get(context, "/api/social/posts/$postId/likes").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("users") ?: JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(RemoteUser(o.optString("id"), o.optString("username"), o.optString("displayName")))
                }
            }
        }
    }

    suspend fun follow(context: Context, username: String, following: Boolean): Result<Boolean> {
        val encoded = java.net.URLEncoder.encode(username.trim().removePrefix("@"), "UTF-8")
        val path = "/api/social/follow/$encoded"
        return if (following) FynxBackendClient.delete(context, path).map { false } else FynxBackendClient.postJson(context, path, "{}").map { true }
    }

    suspend fun deletePost(context: Context, id: String): Result<Unit> {
        val postId = id.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid post id"))
        return FynxBackendClient.delete(context, "/api/social/posts/$postId").map { Unit }
    }
}
