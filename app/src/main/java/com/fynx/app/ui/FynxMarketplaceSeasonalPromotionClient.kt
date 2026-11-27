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
    val destinationValue: String = "",
    val localArtworkUri: String = "",
    val localOnly: Boolean = false
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

    private fun parsePromotions(raw: String): List<FynxMarketplaceSeasonalPromotion> {
        val rows = JSONObject(raw).optJSONArray("promotions")
            ?: throw IllegalStateException("Seasonal promotion response was incomplete.")
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val promotion = parsePromotion(row)
                if (promotion.id.isNotBlank() && promotion.occasionKey.isNotBlank()) add(promotion)
            }
        }
    }

    suspend fun load(context: Context): Result<List<FynxMarketplaceSeasonalPromotion>> {
        // Buyer banners contain only server-published campaigns (or their short-lived cache).
        // Device-local drafts are private to the Admin editor and never leak into the buyer feed.
        val response = FynxBackendClient.get(context, "/api/marketplace/seasonal-feed")
        val cached = if (response.isFailure) {
            withContext(Dispatchers.IO) {
                FynxMarketplaceFeedCache.readResponse(context, "seasonal_promotions", 5L * 60L * 1000L)
            }
        } else null
        val remoteRaw = if (response.isSuccess) {
            response.getOrThrow().also { raw ->
                withContext(Dispatchers.IO) {
                    FynxMarketplaceFeedCache.writeResponse(context, "seasonal_promotions", raw)
                }
            }
        } else cached
        val remote = remoteRaw?.let { raw ->
            runCatching { parsePromotions(raw) }.getOrDefault(emptyList())
        }.orEmpty()
        val now = Instant.now()
        return Result.success(remote.distinctBy { it.id }.filter { isMarketplaceSeasonalPromotionEligible(it, now) })
    }

    suspend fun listForAdmin(context: Context): Result<List<FynxMarketplaceSeasonalPromotion>> {
        val local = withContext(Dispatchers.IO) { FynxMarketplaceLocalCampaignStore.all(context) }
        val response = FynxBackendClient.get(context, "/api/admin/marketplace/seasonal-promotions")
        val remote = response.mapCatching(::parsePromotions)
        return when {
            remote.isSuccess -> Result.success((local + remote.getOrThrow()).distinctBy { it.id })
            local.isNotEmpty() -> Result.success(local)
            else -> Result.failure(response.exceptionOrNull() ?: IllegalStateException("Seasonal campaign list is unavailable."))
        }
    }

    suspend fun uploadArtwork(context: Context, uri: Uri): Result<String> {
        return runCatching {
            // Verify the actual file signature and bound GIF dimensions, frame count,
            // duration and size before handing the original bytes to the shared uploader.
            withContext(Dispatchers.IO) {
                FynxCampaignArtworkMedia.inspectUri(context, uri).mimeType
            }
        }.fold(
            onSuccess = { mimeType ->
                FynxProductionMessaging.uploadMedia(context, uri, mimeType).map { it.id }
            },
            onFailure = { Result.failure(it) }
        )
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

    suspend fun setActive(context: Context, id: String, active: Boolean): Result<Unit> {
        if (id.startsWith("local-")) return FynxMarketplaceLocalCampaignStore.setActive(context, id, active)
        return FynxBackendClient.patchJson(
            context,
            "/api/admin/marketplace/seasonal-promotions/${Uri.encode(id)}",
            JSONObject().put("active", active).toString()
        ).map { Unit }
    }

    suspend fun delete(context: Context, id: String): Result<Unit> {
        if (id.startsWith("local-")) return FynxMarketplaceLocalCampaignStore.delete(context, id)
        return FynxBackendClient.delete(
            context,
            "/api/admin/marketplace/seasonal-promotions/${Uri.encode(id)}"
        ).map { Unit }
    }
}
