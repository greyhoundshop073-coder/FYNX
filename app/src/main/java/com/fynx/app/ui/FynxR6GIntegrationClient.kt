package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

/**
 * Canonical cross-surface references for R6-G. This client never owns marketplace truth;
 * it only links existing listing/business/message/group records through the backend.
 */
object FynxR6GIntegrationClient {
    data class ListingContext(
        val listingId: String,
        val sellerId: String,
        val sellerUsername: String,
        val sellerDisplayName: String,
        val businessId: String?,
        val businessName: String?,
        val businessUsername: String?,
        val businessCategory: String?,
        val businessVerified: Boolean,
        val businessActive: Boolean?,
        val active: Boolean,
        val quantity: Int,
        val storeName: String,
        val title: String,
        val description: String,
        val price: Double,
        val currency: String,
        val category: String
    )

    suspend fun listingContext(context: Context, listingId: String): Result<ListingContext> {
        val id = listingId.toLongOrNull()
            ?: return Result.failure(IllegalArgumentException("invalid listing id"))
        return FynxBackendClient.get(context, "/api/r6g/listings/$id/context")
            .mapCatching { raw ->
                val o = JSONObject(raw).getJSONObject("context")
                ListingContext(
                    o.optString("listingId"), o.optString("sellerId"), o.optString("sellerUsername"), o.optString("sellerDisplayName"),
                    o.optString("businessId").takeIf { it.isNotBlank() }, o.optString("businessName").takeIf { it.isNotBlank() },
                    o.optString("businessUsername").takeIf { it.isNotBlank() }, o.optString("businessCategory").takeIf { it.isNotBlank() },
                    o.optBoolean("businessVerified"), if (o.isNull("businessActive")) null else o.optBoolean("businessActive"),
                    o.optBoolean("active"), o.optInt("quantity"), o.optString("storeName"), o.optString("title"),
                    o.optString("description"), o.optDouble("price"), o.optString("currency", "NGN"), o.optString("category")
                )
            }
    }

    suspend fun linkListingToBusiness(context: Context, listingId: String, businessId: String?): Result<Unit> {
        val id = listingId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid listing id"))
        return FynxBackendClient.patchJson(context, "/api/r6g/listings/$id/business", JSONObject().apply {
            if (businessId.isNullOrBlank()) put("businessId", JSONObject.NULL) else put("businessId", businessId.toLongOrNull() ?: JSONObject.NULL)
        }.toString()).map { Unit }
    }

    suspend fun shareListingToHome(context: Context, listingId: String, text: String = "", friendsOnly: Boolean = false): Result<String> {
        val id = listingId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid listing id"))
        return FynxBackendClient.postJson(context, "/api/r6g/listings/$id/share", JSONObject().apply {
            put("text", text.trim().take(4000))
            put("visibility", if (friendsOnly) "FRIENDS_ONLY" else "PUBLIC")
        }.toString()).mapCatching { JSONObject(it).getString("postId") }
    }

    suspend fun attachListingToMessage(context: Context, messageId: String, listingId: String, businessId: String? = null): Result<Unit> {
        val message = messageId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid message id"))
        val listing = listingId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid listing id"))
        return FynxBackendClient.postJson(context, "/api/r6g/messages/$message/context", JSONObject().apply {
            put("listingId", listing)
            if (!businessId.isNullOrBlank()) put("businessId", businessId.toLongOrNull() ?: JSONObject.NULL)
        }.toString()).map { Unit }
    }

    suspend fun shareListingToGroup(context: Context, groupId: String, listingId: String, text: String = ""): Result<String> {
        val listing = listingId.toLongOrNull() ?: return Result.failure(IllegalArgumentException("invalid listing id"))
        if (groupId.isBlank()) return Result.failure(IllegalArgumentException("invalid group id"))
        return FynxBackendClient.postJson(context, "/api/r6g/groups/${android.net.Uri.encode(groupId)}/marketplace-posts", JSONObject().apply {
            put("listingId", listing)
            put("text", text.trim().take(4000))
        }.toString()).mapCatching { JSONObject(it).getString("postId") }
    }

    suspend fun health(context: Context): Result<Boolean> =
        FynxBackendClient.get(context, "/api/r6g/health").mapCatching { JSONObject(it).optBoolean("ok") }
}
