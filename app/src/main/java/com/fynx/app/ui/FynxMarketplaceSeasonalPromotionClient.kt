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
    val occasionKey: String,
    val headline: String,
    val body: String,
    val actionLabel: String,
    val artworkMediaId: String,
    val startsAt: String,
    val endsAt: String,
    val active: Boolean
)

object FynxMarketplaceSeasonalPromotionClient {
    private fun parsePromotion(value: JSONObject): FynxMarketplaceSeasonalPromotion =
        FynxMarketplaceSeasonalPromotion(
            id = value.optString("id").trim(),
            occasionKey = value.optString("occasionKey").trim(),
            headline = value.optString("headline").trim(),
            body = value.optString("body").trim(),
            actionLabel = value.optString("actionLabel").trim().ifBlank { "Explore Marketplace" },
            artworkMediaId = value.optString("artworkMediaId").trim(),
            startsAt = value.optString("startsAt"),
            endsAt = value.optString("endsAt"),
            active = value.optBoolean("active")
        )

    suspend fun load(context: Context): Result<List<FynxMarketplaceSeasonalPromotion>> {
        val response = FynxBackendClient.get(context, "/api/marketplace/seasonal-feed")
        val raw = if (response.isSuccess) {
            response.getOrThrow()
        } else {
            withContext(Dispatchers.IO) {
                FynxMarketplaceFeedCache.readResponse(context, "seasonal_promotions", 24L * 60L * 60L * 1000L)
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

    suspend fun listForAdmin(context: Context): Result<List<FynxMarketplaceSeasonalPromotion>> =
        FynxBackendClient.get(context, "/api/admin/marketplace/seasonal-promotions").mapCatching { raw ->
            val rows = JSONObject(raw).optJSONArray("promotions")
                ?: throw IllegalStateException("Seasonal campaign list was incomplete.")
            buildList {
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    val promotion = parsePromotion(row)
                    if (promotion.id.isNotBlank() && promotion.occasionKey.isNotBlank()) add(promotion)
                }
            }
        }

    suspend fun uploadArtwork(context: Context, uri: Uri): Result<String> {
        val mime = context.contentResolver.getType(uri)?.trim()?.lowercase().orEmpty()
        if (!mime.startsWith("image/")) return Result.failure(IllegalArgumentException("Seasonal artwork must be an image."))
        return FynxProductionMessaging.uploadMedia(context, uri, mime).map { it.id }
    }

    suspend fun create(
        context: Context,
        occasionKey: String,
        headline: String,
        body: String,
        actionLabel: String,
        artworkMediaId: String,
        startsAt: String,
        endsAt: String
    ): Result<FynxMarketplaceSeasonalPromotion> {
        val payload = JSONObject()
            .put("occasionKey", occasionKey.trim())
            .put("headline", headline.trim())
            .put("body", body.trim())
            .put("actionLabel", actionLabel.trim())
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
