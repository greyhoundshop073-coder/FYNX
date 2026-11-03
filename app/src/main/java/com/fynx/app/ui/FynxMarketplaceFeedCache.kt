package com.fynx.app.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

internal data class FynxMarketplaceCachedPage(
    val listings: List<FynxRemoteSocialClient.MarketplaceListing>,
    val hasMore: Boolean,
    val savedAtMillis: Long
)

/** Account-scoped cache for the first page of Marketplace discovery results. */
internal object FynxMarketplaceFeedCache {
    private const val PREFS = "fynx_feed_cache"
    private const val LISTING_PREFIX = "marketplace_listing_page_v1_"
    private const val RESPONSE_PREFIX = "marketplace_response_v1_"
    private const val MAX_LISTING_STALE_AGE_MS = 30L * 24L * 60L * 60L * 1000L
    private const val MAX_RESPONSE_BYTES = 512 * 1024

    private fun accountKey(context: Context): String? =
        FynxAuthStore.accountStorageKey(context)?.takeIf { it.isNotBlank() }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun listingKey(context: Context, query: String, category: String, location: String): String? {
        val account = accountKey(context) ?: return null
        val scope = listOf(account, query.trim().lowercase(Locale.US), category.trim().lowercase(Locale.US),
            location.trim().lowercase(Locale.US)).joinToString("|")
        return LISTING_PREFIX + digest(scope)
    }

    private fun responseKey(context: Context, namespace: String): String? {
        val account = accountKey(context) ?: return null
        val safe = namespace.trim().lowercase(Locale.US)
            .takeIf { it.matches(Regex("[a-z0-9_-]{1,64}")) } ?: return null
        return RESPONSE_PREFIX + digest("$account|$safe")
    }

    fun read(context: Context, query: String, category: String, location: String = ""): FynxMarketplaceCachedPage? = runCatching {
        val key = listingKey(context, query, category, location) ?: return null
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null) ?: return null
        val root = JSONObject(raw)
        val savedAt = root.optLong("savedAt", 0L)
        val age = System.currentTimeMillis() - savedAt
        if (savedAt <= 0L || age < 0L || age > MAX_LISTING_STALE_AGE_MS) return null
        val rows = root.optJSONArray("listings") ?: return null
        val listings = buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val id = row.optString("id").trim()
                val title = row.optString("title").trim()
                val seller = row.optString("sellerUsername").trim()
                val price = row.optDouble("price", Double.NaN)
                val currency = row.optString("currency").trim().uppercase(Locale.US)
                val active = row.optBoolean("active", true)
                val quantity = row.optInt("quantity", 0)
                if (id.isBlank() || title.isBlank() || seller.isBlank() || !price.isFinite() ||
                    price <= 0.0 || quantity <= 0 || currency != "NGN" || !active) continue
                val media = row.optJSONArray("mediaIds") ?: JSONArray()
                val mediaIds = buildList {
                    for (i in 0 until media.length()) media.optString(i).trim()
                        .takeIf { it.isNotBlank() }?.let { add(it) }
                }
                val types = row.optJSONArray("mediaTypes") ?: JSONArray()
                val mediaTypes = buildList { for (i in 0 until types.length()) add(types.optString(i)) }
                add(FynxRemoteSocialClient.MarketplaceListing(
                    id=id, sellerId=row.optString("sellerId"), sellerUsername=seller,
                    sellerDisplayName=row.optString("sellerDisplayName").ifBlank { seller },
                    storeName=row.optString("storeName"), title=title, description=row.optString("description"),
                    price=price, currency=currency, category=row.optString("category"),
                    condition=row.optString("condition", "NEW"), quantity=row.optInt("quantity"),
                    location=row.optString("location"), deliveryAvailable=row.optBoolean("deliveryAvailable"),
                    pickupAvailable=row.optBoolean("pickupAvailable", true),
                    deliveryFee=if (row.isNull("deliveryFee")) null else row.optDouble("deliveryFee"),
                    mediaIds=mediaIds, active=active, mediaTypes=mediaTypes
                ))
            }
        }
        if (listings.isEmpty()) return null
        FynxMarketplaceCachedPage(listings, root.optBoolean("hasMore"), savedAt)
    }.getOrNull()

    fun write(context: Context, query: String, category: String, location: String = "",
        listings: List<FynxRemoteSocialClient.MarketplaceListing>, hasMore: Boolean) {
        runCatching {
            val key = listingKey(context, query, category, location) ?: return
            val rows = JSONArray()
            listings.take(60).forEach { item ->
                rows.put(JSONObject().apply {
                    put("id", item.id); put("sellerId", item.sellerId)
                    put("sellerUsername", item.sellerUsername); put("sellerDisplayName", item.sellerDisplayName)
                    put("storeName", item.storeName); put("title", item.title); put("description", item.description)
                    put("price", item.price); put("currency", item.currency); put("category", item.category)
                    put("condition", item.condition); put("quantity", item.quantity); put("location", item.location)
                    put("deliveryAvailable", item.deliveryAvailable); put("pickupAvailable", item.pickupAvailable)
                    put("deliveryFee", item.deliveryFee ?: JSONObject.NULL)
                    put("mediaIds", JSONArray(item.mediaIds)); put("active", item.active)
                    put("mediaTypes", JSONArray(item.mediaTypes))
                })
            }
            val payload = JSONObject().put("savedAt", System.currentTimeMillis())
                .put("hasMore", hasMore).put("listings", rows).toString()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, payload).apply()
        }
    }

    fun readResponse(context: Context, namespace: String, maxAgeMillis: Long): String? = runCatching {
        val key = responseKey(context, namespace) ?: return null
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null) ?: return null
        val record = JSONObject(raw)
        val savedAt = record.optLong("savedAt", 0L)
        val age = System.currentTimeMillis() - savedAt
        if (savedAt <= 0L || age < 0L || age > maxAgeMillis) return null
        record.optString("payload").takeIf { it.isNotBlank() }
    }.getOrNull()

    fun writeResponse(context: Context, namespace: String, payload: String) {
        runCatching {
            if (payload.toByteArray(Charsets.UTF_8).size > MAX_RESPONSE_BYTES) return
            val key = responseKey(context, namespace) ?: return
            val record = JSONObject().put("savedAt", System.currentTimeMillis()).put("payload", payload).toString()
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, record).apply()
        }
    }
}
