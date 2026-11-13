package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A server-scheduled celebration or seasonal Marketplace message. */
data class FynxMarketplaceSeasonalPromotion(
    val id: String,
    val promotionType: String,
    val occasionKey: String,
    val headline: String,
    val body: String,
    val actionLabel: String,
    val artworkMediaId: String,
    val startsAt: String,
    val endsAt: String,
    val active: Boolean,
    val destinationType: String = "MARKETPLACE",
    val destinationValue: String = ""
)

object FynxMarketplaceSeasonalPromotionClient {
    private fun parsePromotion(value: JSONObject): FynxMarketplaceSeasonalPromotion =
        FynxMarketplaceSeasonalPromotion(
            id = value.optString("id").trim(),
            promotionType = value.optString("promotionType", "SEASONAL").trim().uppercase().ifBlank { "SEASONAL" },
            occasionKey = value.optString("occasionKey").trim(),
            headline = value.optString("headline").trim(),
            body = value.optString("body").trim(),
            actionLabel = value.optString("actionLabel").trim().ifBlank { "Explore Marketplace" },
            artworkMediaId = value.optString("artworkMediaId").trim(),
            startsAt = value.optString("startsAt"),
            endsAt = value.optString("endsAt"),
            active = value.optBoolean("active"),
            destinationType = value.optString("destinationType", "MARKETPLACE").trim().uppercase().ifBlank { "MARKETPLACE" },
            destinationValue = value.optString("destinationValue").trim()
        )

    suspend fun load(context: Context): Result<List<FynxMarketplaceSeasonalPromotion>> {
        val response = FynxBackendClient.get(context, "/api/marketplace/seasonal-feed")
        val raw = if (response.isSuccess) {
            response.getOrThrow()
        } else {
            withContext(Dispatchers.IO) {
                FynxMarketplaceFeedCache.readResponse(context, "seasonal_promotions", 5L * 60L * 1000L)
            } ?: return Result.failure(response.exceptionOrNull() ?: IllegalStateException("Seasonal promotion feed is unavailable."))
        }
        if (response.isSuccess) {
            withContext(Dispatchers.IO) {
                FynxMarketplaceFeedCache.writeResponse(context, "seasonal_promotions", raw)
            }
        }
        return runCatching {
            val rows = JSONObject(raw).optJSONArray("promotions")
                ?: throw IllegalStateException("Seasonal promotion response was incomplete.")
            val now = Instant.now()
            buildList {
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    val promotion = parsePromotion(row)
                    if (isMarketplaceSeasonalPromotionEligible(promotion, now)) add(promotion)
                }
            }
        }
    }

    suspend fun listForAdmin(
        context: Context,
        offset: Int = 0,
        limit: Int = 50
    ): Result<FynxAdminClient.AdminPage<FynxMarketplaceSeasonalPromotion>> =
        FynxBackendClient.get(
            context,
            "/api/admin/marketplace/seasonal-promotions?limit=${limit.coerceIn(1, 100)}&offset=${offset.coerceIn(0, 10000)}"
        ).mapCatching { raw ->
            val value = JSONObject(raw)
            val rows = value.optJSONArray("promotions")
                ?: throw IllegalStateException("Seasonal campaign list was incomplete.")
            val items = buildList {
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    val promotion = parsePromotion(row)
                    if (promotion.id.isNotBlank() && promotion.occasionKey.isNotBlank()) add(promotion)
                }
            }
            FynxAdminClient.AdminPage(
                items = items,
                offset = value.optInt("offset", offset),
                limit = value.optInt("limit", limit),
                hasMore = value.optBoolean("hasMore", false)
            )
        }

    suspend fun uploadArtwork(context: Context, uri: Uri): Result<String> {
        val mime = context.contentResolver.getType(uri)?.trim()?.lowercase().orEmpty()
        if (!mime.startsWith("image/")) return Result.failure(IllegalArgumentException("Seasonal artwork must be an image."))
        return FynxProductionMessaging.uploadMedia(context, uri, mime).map { it.id }
    }

    suspend fun create(
        context: Context,
        promotionType: String,
        occasionKey: String,
        headline: String,
        body: String,
        actionLabel: String,
        destinationType: String,
        destinationValue: String,
        artworkMediaId: String,
        startsAt: String,
        endsAt: String
    ): Result<FynxMarketplaceSeasonalPromotion> {
        val payload = JSONObject()
            .put("promotionType", promotionType.trim().uppercase())
            .put("occasionKey", occasionKey.trim())
            .put("headline", headline.trim())
            .put("body", body.trim())
            .put("actionLabel", actionLabel.trim())
            .put("destinationType", destinationType.trim().uppercase())
            .put("destinationValue", destinationValue.trim().ifBlank { JSONObject.NULL })
            .put("artworkMediaId", artworkMediaId.trim().ifBlank { JSONObject.NULL })
            .put("startsAt", startsAt.trim())
            .put("endsAt", endsAt.trim())
            .put("active", true)
        return FynxBackendClient.postJson(
            context,
            "/api/admin/marketplace/seasonal-promotions",
            payload.toString()
        ).mapCatching { raw ->
            val row = JSONObject(raw).optJSONObject("promotion")
                ?: throw IllegalStateException("Seasonal campaign save response was incomplete.")
            parsePromotion(row)
        }
    }

    suspend fun update(
        context: Context,
        id: String,
        promotionType: String,
        occasionKey: String,
        headline: String,
        body: String,
        actionLabel: String,
        destinationType: String,
        destinationValue: String,
        artworkMediaId: String,
        startsAt: String,
        endsAt: String,
        active: Boolean
    ): Result<FynxMarketplaceSeasonalPromotion> {
        val payload = JSONObject()
            .put("promotionType", promotionType.trim().uppercase())
            .put("occasionKey", occasionKey.trim())
            .put("headline", headline.trim())
            .put("body", body.trim())
            .put("actionLabel", actionLabel.trim())
            .put("destinationType", destinationType.trim().uppercase())
            .put("destinationValue", destinationValue.trim().ifBlank { JSONObject.NULL })
            .put("artworkMediaId", artworkMediaId.trim().ifBlank { JSONObject.NULL })
            .put("startsAt", startsAt.trim())
            .put("endsAt", endsAt.trim())
            .put("active", active)
        return FynxBackendClient.patchJson(
            context,
            "/api/admin/marketplace/seasonal-promotions/${Uri.encode(id)}/edit",
            payload.toString()
        ).mapCatching { raw ->
            val row = JSONObject(raw).optJSONObject("promotion")
                ?: throw IllegalStateException("Seasonal campaign update response was incomplete.")
            parsePromotion(row)
        }
    }

    suspend fun setActive(context: Context, id: String, active: Boolean): Result<Unit> =
        FynxBackendClient.patchJson(
            context,
            "/api/admin/marketplace/seasonal-promotions/${Uri.encode(id)}",
            JSONObject().put("active", active).toString()
        ).map { Unit }

    suspend fun delete(context: Context, id: String): Result<Unit> =
        FynxBackendClient.delete(
            context,
            "/api/admin/marketplace/seasonal-promotions/${Uri.encode(id)}"
        ).map { Unit }
}
