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

data class FynxMarketplaceSellerWeeklyAward(
    val weekStart: String,
    val weekEnd: String,
    val weekLabel: String,
    val rank: Int,
    val username: String,
    val displayName: String,
    val completedOrders: Int,
    val uniqueBuyers: Int,
    val featuredListingId: String?,
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
    suspend fun history(context: Context, username: String, limit: Int = 24): Result<List<FynxMarketplaceSellerWeeklyAward>> {
        val cleanUsername = username.trim().removePrefix("@")
        if (cleanUsername.isBlank()) return Result.failure(IllegalArgumentException("Seller username is required."))
        val safeLimit = limit.coerceIn(1, 100)
        val encodedUsername = android.net.Uri.encode(cleanUsername)
        return FynxBackendClient.get(
            context,
            "/api/marketplace/sellers/weekly/history?username=$encodedUsername&limit=$safeLimit"
        ).mapCatching { raw ->
            val root = JSONObject(raw)
            val rows = root.optJSONArray("awards")
                ?: throw IllegalStateException("Weekly seller award history response was incomplete.")
            buildList {
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    val seller = row.optString("username").trim()
                    val rank = row.optInt("rank", 0)
                    val orders = row.optInt("completedOrders", 0)
                    val weekStart = row.optString("weekStart").trim()
                    if (seller.isBlank() || rank !in 1..5 || orders < 1 || weekStart.isBlank()) continue
                    add(
                        FynxMarketplaceSellerWeeklyAward(
                            weekStart = weekStart,
                            weekEnd = row.optString("weekEnd"),
                            weekLabel = row.optString("weekLabel"),
                            rank = rank,
                            username = seller,
                            displayName = row.optString("displayName").trim().ifBlank { seller },
                            completedOrders = orders,
                            uniqueBuyers = row.optInt("uniqueBuyers", 0).coerceAtLeast(0),
                            featuredListingId = row.optString("featuredListingId").trim().takeIf { it.isNotBlank() && it != "null" },
                            featuredProductTitle = row.optString("featuredProductTitle").trim()
                        )
                    )
                }
            }
        }
    }

}
