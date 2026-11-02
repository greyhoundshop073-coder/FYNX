package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

/** A weekly seller award backed by completed Marketplace orders, never ad spend. */
data class FynxMarketplaceWeeklySeller(
    val sellerId: String,
    val username: String,
    val displayName: String,
    val completedOrders: Int,
    val uniqueBuyers: Int,
    val rank: Int,
    val featuredListingId: String,
    val featuredProductTitle: String
)

object FynxMarketplaceWeeklySellerClient {
    suspend fun load(context: Context): Result<List<FynxMarketplaceWeeklySeller>> =
        FynxBackendClient.get(context, "/api/marketplace/sellers/weekly").mapCatching { raw ->
            val root = JSONObject(raw)
            val rows = root.optJSONArray("winners")
                ?: throw IllegalStateException("Weekly seller response is incomplete.")
            (0 until rows.length()).mapNotNull { index ->
                val row = rows.optJSONObject(index) ?: return@mapNotNull null
                val sellerId = row.optString("sellerId").trim()
                val username = row.optString("username").trim().removePrefix("@")
                val listingId = row.optString("featuredListingId").trim()
                val completedOrders = row.optInt("completedOrders", 0)
                val rank = row.optInt("rank", 0)
                if (sellerId.isBlank() || username.isBlank() || listingId.isBlank() ||
                    completedOrders < 1 || rank < 1
                ) return@mapNotNull null
                FynxMarketplaceWeeklySeller(
                    sellerId = sellerId,
                    username = username,
                    displayName = row.optString("displayName").trim().ifBlank { username },
                    completedOrders = completedOrders,
                    uniqueBuyers = row.optInt("uniqueBuyers", 0).coerceAtLeast(0),
                    rank = rank,
                    featuredListingId = listingId,
                    featuredProductTitle = row.optString("featuredProductTitle").trim()
                )
            }
        }
}
