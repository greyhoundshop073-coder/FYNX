package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import com.fynx.app.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Device-local Marketplace banner campaigns for offline preview and testing.
 *
 * Artwork is copied into app-private storage, so a Photo Picker URI does not
 * expire when the user leaves the picker. Nothing in this store is sent to the
 * backend or shown on another device.
 */
internal object FynxMarketplaceLocalCampaignStore {
    private const val PREFS = "fynx_marketplace_local_campaigns_v1"
    private const val CAMPAIGNS_KEY = "campaigns"
    private const val SAMPLES_SEEDED_KEY = "sample_campaigns_seeded"
    private const val MAX_CAMPAIGNS = 20
    private const val MAX_ARTWORK_BYTES = 12L * 1024L * 1024L
    private const val MAX_TOTAL_ARTWORK_BYTES = 48L * 1024L * 1024L
    private val activeCampaigns = MutableStateFlow<List<FynxMarketplaceSeasonalPromotion>>(emptyList())

    fun observe(context: Context): StateFlow<List<FynxMarketplaceSeasonalPromotion>> {
        if (activeCampaigns.value.isEmpty()) {
            activeCampaigns.value = active(context)
        }
        return activeCampaigns.asStateFlow()
    }

    private fun publishActive(context: Context) {
        activeCampaigns.value = active(context)
    }

    fun all(context: Context): List<FynxMarketplaceSeasonalPromotion> {
        val appContext = context.applicationContext
        ensureSampleCampaign(appContext)
        return readStored(appContext)
    }

    fun active(context: Context): List<FynxMarketplaceSeasonalPromotion> {
        val now = Instant.now()
        return all(context).filter { isMarketplaceSeasonalPromotionEligible(it, now) }
    }

    fun importArtwork(context: Context, source: Uri): Result<String> = runCatching {
        val appContext = context.applicationContext
        val mime = appContext.contentResolver.getType(source)?.trim()?.lowercase().orEmpty()
        require(mime.startsWith("image/")) { "Please choose an image file." }

        val directory = File(appContext.filesDir, "marketplace-campaign-artwork")
        require(directory.exists() || directory.mkdirs()) { "FYNX could not prepare local campaign storage." }
        val currentBytes = directory.listFiles()?.sumOf { it.length() } ?: 0L
        require(currentBytes < MAX_TOTAL_ARTWORK_BYTES) { "Local campaign artwork storage is full. Remove an old campaign photo first." }

        val extension = when (mime) {
            "image/webp" -> "webp"
            "image/png" -> "png"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/heic", "image/heif" -> "heic"
            else -> "img"
        }
        val destination = File(directory, "campaign-" + UUID.randomUUID().toString() + "." + extension)
        try {
            val input = appContext.contentResolver.openInputStream(source)
                ?: throw IllegalStateException("FYNX could not read the selected photo.")
            input.use { stream ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0L
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_ARTWORK_BYTES) { "Campaign photos must be 12 MB or smaller." }
                        require(currentBytes + total <= MAX_TOTAL_ARTWORK_BYTES) { "Local campaign artwork storage is full." }
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(destination.length() > 0L) { "The selected photo is empty." }
            Uri.fromFile(destination).toString()
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    fun upsert(context: Context, campaign: FynxMarketplaceSeasonalPromotion): Result<Unit> = runCatching {
        require(campaign.localOnly && campaign.id.startsWith("local-")) {
            "Only device-local campaigns can be saved in offline test mode."
        }
        require(campaign.occasionKey.isNotBlank() && campaign.headline.trim().length >= 3) {
            "Enter a campaign key and a headline of at least three characters."
        }
        val current = all(context)
        val remaining = current.filterNot { it.id == campaign.id }
        require(remaining.size < MAX_CAMPAIGNS) {
            "This device already has the maximum of $MAX_CAMPAIGNS local campaigns. Delete an old campaign first."
        }
        writeStored(context.applicationContext, remaining + campaign)
        current.firstOrNull { it.id == campaign.id }?.let { previous ->
            if (previous.localArtworkUri != campaign.localArtworkUri) {
                deleteOwnedArtwork(context.applicationContext, previous.localArtworkUri)
            }
        }
        publishActive(context.applicationContext)
        Unit
    }

    fun setActive(context: Context, id: String, active: Boolean): Result<Unit> = runCatching {
        val existing = all(context).firstOrNull { it.id == id }
            ?: throw IllegalStateException("The local campaign could not be found.")
        upsert(context, existing.copy(active = active)).getOrThrow()
    }

    fun delete(context: Context, id: String): Result<Unit> = runCatching {
        val current = all(context)
        val removed = current.firstOrNull { it.id == id }
        writeStored(context.applicationContext, current.filterNot { it.id == id })
        removed?.let { deleteOwnedArtwork(context.applicationContext, it.localArtworkUri) }
        publishActive(context.applicationContext)
        Unit
    }

    private fun ensureSampleCampaign(context: Context) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (preferences.getBoolean(SAMPLES_SEEDED_KEY, false)) return

        synchronized(this) {
            val currentPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (currentPreferences.getBoolean(SAMPLES_SEEDED_KEY, false)) return
            if (readStored(context).isEmpty()) {
                val now = Instant.now()
                val sample = FynxMarketplaceSeasonalPromotion(
                    id = "local-sample-fynx-marketplace",
                    promotionType = "ANNOUNCEMENT",
                    occasionKey = "fynx-marketplace-welcome",
                    headline = "Discover more on FYNX",
                    body = "Explore useful finds and fresh discoveries from sellers on FYNX Marketplace.",
                    actionLabel = "Explore Marketplace",
                    artworkMediaId = "",
                    startsAt = now.minusSeconds(60).toString(),
                    endsAt = now.plus(44, ChronoUnit.DAYS).toString(),
                    active = true,
                    destinationType = "MARKETPLACE",
                    destinationValue = "",
                    localArtworkUri = "android.resource://" + context.packageName + "/" + R.drawable.fynx_marketplace_showcase,
                    localOnly = true
                )
                writeStored(context, listOf(sample))
            }
            currentPreferences.edit().putBoolean(SAMPLES_SEEDED_KEY, true).apply()
        }
    }

    private fun readStored(context: Context): List<FynxMarketplaceSeasonalPromotion> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CAMPAIGNS_KEY, null) ?: return emptyList()
        return runCatching {
            val rows = JSONArray(raw)
            buildList {
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    val id = row.optString("id").trim()
                    val occasion = row.optString("occasionKey").trim()
                    val headline = row.optString("headline").trim()
                    if (id.isBlank() || occasion.isBlank() || headline.isBlank()) continue
                    add(
                        FynxMarketplaceSeasonalPromotion(
                            id = id,
                            promotionType = row.optString("promotionType", "SEASONAL").trim().uppercase().ifBlank { "SEASONAL" },
                            occasionKey = occasion,
                            headline = headline,
                            body = row.optString("body").trim(),
                            actionLabel = row.optString("actionLabel", "Explore Marketplace").trim().ifBlank { "Explore Marketplace" },
                            artworkMediaId = row.optString("artworkMediaId").trim(),
                            startsAt = row.optString("startsAt"),
                            endsAt = row.optString("endsAt"),
                            active = row.optBoolean("active", true),
                            destinationType = row.optString("destinationType", "MARKETPLACE").trim().uppercase().ifBlank { "MARKETPLACE" },
                            destinationValue = row.optString("destinationValue").trim(),
                            localArtworkUri = row.optString("localArtworkUri").trim(),
                            localOnly = row.optBoolean("localOnly", true)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeStored(context: Context, campaigns: List<FynxMarketplaceSeasonalPromotion>) {
        val rows = JSONArray()
        campaigns.forEach { campaign ->
            rows.put(
                JSONObject()
                    .put("id", campaign.id)
                    .put("promotionType", campaign.promotionType)
                    .put("occasionKey", campaign.occasionKey)
                    .put("headline", campaign.headline)
                    .put("body", campaign.body)
                    .put("actionLabel", campaign.actionLabel)
                    .put("artworkMediaId", campaign.artworkMediaId)
                    .put("startsAt", campaign.startsAt)
                    .put("endsAt", campaign.endsAt)
                    .put("active", campaign.active)
                    .put("destinationType", campaign.destinationType)
                    .put("destinationValue", campaign.destinationValue)
                    .put("localArtworkUri", campaign.localArtworkUri)
                    .put("localOnly", true)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(CAMPAIGNS_KEY, rows.toString())
            .apply()
    }

    private fun deleteOwnedArtwork(context: Context, artworkUri: String) {
        if (artworkUri.isBlank()) return
        val uri = runCatching { Uri.parse(artworkUri) }.getOrNull() ?: return
        if (uri.scheme != "file") return
        runCatching {
            val directory = File(context.filesDir, "marketplace-campaign-artwork").canonicalFile
            val target = File(uri.path.orEmpty()).canonicalFile
            if (target.parentFile == directory) target.delete()
        }
    }
}
