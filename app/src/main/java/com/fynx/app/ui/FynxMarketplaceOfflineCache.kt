package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Last-known real Marketplace listings, scoped to the signed-in account and never authoritative for checkout. */
internal object FynxMarketplaceOfflineCache {
    private const val PREFS = "fynx_marketplace_catalogue_cache_v1"
    private const val MAX_CACHED_LISTINGS = 60

    private fun accountKey(ownerKey: String): String? = ownerKey.trim().lowercase(Locale.US)
        .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        .take(80)
        .takeIf { it.isNotBlank() && it != "preview" }

    fun read(context: Context, ownerKey: String): Snapshot? = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = accountKey(ownerKey) ?: return null
        val raw = prefs.getString("listings_$key", null) ?: return null
        val savedAt = prefs.getLong("saved_at_$key", 0L)
        if (savedAt <= 0L) return null
        val array = JSONArray(raw)
        val items = buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val media = o.optJSONArray("mediaIds") ?: JSONArray()
                add(FynxRemoteSocialClient.MarketplaceListing(
                    id = o.optString("id"),
                    sellerId = o.optString("sellerId"),
                    sellerUsername = o.optString("sellerUsername"),
                    sellerDisplayName = o.optString("sellerDisplayName"),
                    storeName = o.optString("storeName"),
                    title = o.optString("title"),
                    description = o.optString("description"),
                    price = o.optDouble("price", 0.0),
                    currency = o.optString("currency", "NGN"),
                    category = o.optString("category"),
                    condition = o.optString("condition", "NEW"),
                    quantity = o.optInt("quantity", 0),
                    location = o.optString("location"),
                    deliveryAvailable = o.optBoolean("deliveryAvailable"),
                    pickupAvailable = o.optBoolean("pickupAvailable", true),
                    deliveryFee = if (o.isNull("deliveryFee")) null else o.optDouble("deliveryFee"),
                    mediaIds = buildList { for (j in 0 until media.length()) add(media.optString(j)) },
                    active = o.optBoolean("active", true)
                ))
            }
        }.filter { it.id.isNotBlank() && it.price.isFinite() && it.price > 0.0 }
        if (items.isEmpty()) null else Snapshot(items, savedAt)
    }.getOrNull()

    fun write(context: Context, ownerKey: String, listings: List<FynxRemoteSocialClient.MarketplaceListing>) {
        runCatching {
            val key = accountKey(ownerKey) ?: return
            val array = JSONArray()
            listings.distinctBy { it.id }.take(MAX_CACHED_LISTINGS).forEach { item ->
                array.put(JSONObject().apply {
                    put("id", item.id)
                    put("sellerId", item.sellerId)
                    put("sellerUsername", item.sellerUsername)
                    put("sellerDisplayName", item.sellerDisplayName)
                    put("storeName", item.storeName)
                    put("title", item.title)
                    put("description", item.description)
                    put("price", item.price)
                    put("currency", item.currency)
                    put("category", item.category)
                    put("condition", item.condition)
                    put("quantity", item.quantity)
                    put("location", item.location)
                    put("deliveryAvailable", item.deliveryAvailable)
                    put("pickupAvailable", item.pickupAvailable)
                    if (item.deliveryFee == null) put("deliveryFee", JSONObject.NULL) else put("deliveryFee", item.deliveryFee)
                    put("mediaIds", JSONArray().apply { item.mediaIds.forEach(::put) })
                    put("active", item.active)
                })
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("listings_$key", array.toString())
                .putLong("saved_at_$key", System.currentTimeMillis())
                .apply()
        }
    }

    fun filter(
        listings: List<FynxRemoteSocialClient.MarketplaceListing>,
        query: String,
        category: String
    ): List<FynxRemoteSocialClient.MarketplaceListing> {
        val term = query.trim()
        val selectedCategory = category.trim()
        return listings.filter { listing ->
            val queryMatches = term.isBlank() || listOf(
                listing.title, listing.description, listing.storeName, listing.category,
                listing.sellerUsername, listing.sellerDisplayName
            ).any { it.contains(term, ignoreCase = true) }
            val categoryMatches = selectedCategory.isBlank() || selectedCategory.equals("All", ignoreCase = true) ||
                listing.category.equals(selectedCategory, ignoreCase = true)
            queryMatches && categoryMatches
        }
    }

    data class Snapshot(val listings: List<FynxRemoteSocialClient.MarketplaceListing>, val savedAtMs: Long)
}
