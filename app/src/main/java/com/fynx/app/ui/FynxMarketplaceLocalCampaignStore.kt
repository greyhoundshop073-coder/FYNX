package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.UUID
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Private on-device campaign drafts. They are never inserted into the buyer feed.
 *
 * Artwork is copied into app-private storage, so a Photo Picker URI remains
 * available after the picker closes. Drafts stay on this device until published
 * through the authenticated campaign service.
 */
internal object FynxMarketplaceLocalCampaignStore {
    private const val PREFS_PREFIX = "fynx_marketplace_local_campaigns_v1_"
    private const val CAMPAIGNS_KEY = "campaigns"
    private const val ARTWORK_DIRECTORY_PREFIX = "marketplace-campaign-artwork_"
    private const val MAX_CAMPAIGNS = 20
    private const val MAX_ARTWORK_BYTES = 12L * 1024L * 1024L
    private const val MAX_TOTAL_ARTWORK_BYTES = 48L * 1024L * 1024L
    private val activeCampaigns = MutableStateFlow<List<FynxMarketplaceSeasonalPromotion>>(emptyList())
    @Volatile private var observedAccountNamespace: String? = null

    /**
     * Device-local drafts are isolated by the signed-in FYNX account. The
     * username is never used directly in a path or preference name.
     */
    private fun accountNamespace(context: Context): String? {
        val account = FynxAuthStore.accountStorageKey(context)?.trim()?.lowercase()
            ?.takeIf { it.isNotBlank() } ?: return null
        return MessageDigest.getInstance("SHA-256")
            .digest(account.toByteArray(Charsets.UTF_8))
            .take(16)
            .joinToString("") { "%02x".format(it) }
    }

    private fun preferencesName(context: Context): String? =
        accountNamespace(context)?.let { PREFS_PREFIX + it }

    private fun artworkDirectory(context: Context): File? =
        accountNamespace(context)?.let { File(context.filesDir, ARTWORK_DIRECTORY_PREFIX + it) }

    fun observe(context: Context): StateFlow<List<FynxMarketplaceSeasonalPromotion>> {
        val namespace = accountNamespace(context)
        if (namespace != observedAccountNamespace) {
            observedAccountNamespace = namespace
            activeCampaigns.value = if (namespace == null) emptyList() else active(context)
        } else if (namespace != null && activeCampaigns.value.isEmpty()) {
            activeCampaigns.value = active(context)
        }
        return activeCampaigns.asStateFlow()
    }

    private fun publishActive(context: Context) {
        observedAccountNamespace = accountNamespace(context)
        activeCampaigns.value = active(context)
    }

    fun all(context: Context): List<FynxMarketplaceSeasonalPromotion> {
        return readStored(context.applicationContext)
    }

    fun active(context: Context): List<FynxMarketplaceSeasonalPromotion> {
        val now = Instant.now()
        return all(context).filter { isMarketplaceSeasonalPromotionEligible(it, now) }
    }

    fun importArtwork(context: Context, source: Uri): Result<String> = runCatching {
        val appContext = context.applicationContext
        val artworkInfo = FynxCampaignArtworkMedia.inspectUri(appContext, source)
        val mime = artworkInfo.mimeType

        val directory = artworkDirectory(appContext)
            ?: throw IllegalStateException("Sign in to FYNX before saving private campaign artwork.")
        require(directory.exists() || directory.mkdirs()) { "FYNX could not prepare local campaign storage." }
        val currentBytes = directory.listFiles()?.sumOf { it.length() } ?: 0L
        require(currentBytes < MAX_TOTAL_ARTWORK_BYTES) { "Local campaign artwork storage is full. Remove an old campaign photo first." }

        val extension = when (mime) {
            "image/gif" -> "gif"
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
            val storedInfo = FynxCampaignArtworkMedia.inspectFile(destination)
            require(storedInfo.mimeType == mime) { "The selected artwork changed while it was being saved." }
            Uri.fromFile(destination).toString()
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    fun upsert(context: Context, campaign: FynxMarketplaceSeasonalPromotion): Result<Unit> = runCatching {
        require(campaign.localOnly && campaign.id.startsWith("local-")) {
            "Only device-local campaign drafts can be saved without publishing."
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

    private fun readStored(context: Context): List<FynxMarketplaceSeasonalPromotion> {
        val prefsName = preferencesName(context) ?: return emptyList()
        val raw = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getString(CAMPAIGNS_KEY, null) ?: return emptyList()
        return runCatching {
            val rows = JSONArray(raw)
            buildList {
                for (index in 0 until rows.length()) {
                    val row = rows.optJSONObject(index) ?: continue
                    val id = row.optString("id").trim()
                    val occasion = row.optString("occasionKey").trim()
                    val headline = row.optString("headline").trim()
                    // Remove the previous synthetic sample from existing installs. It was
                    // demonstration content, not an Owner/Admin-authored campaign.
                    if (id == "local-sample-fynx-marketplace") continue
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
        val prefsName = preferencesName(context)
            ?: throw IllegalStateException("Sign in to FYNX before saving a private campaign draft.")
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(CAMPAIGNS_KEY, rows.toString())
            .apply()
    }

    private fun deleteOwnedArtwork(context: Context, artworkUri: String) {
        if (artworkUri.isBlank()) return
        val uri = runCatching { Uri.parse(artworkUri) }.getOrNull() ?: return
        if (uri.scheme != "file") return
        runCatching {
            val directory = artworkDirectory(context)?.canonicalFile ?: return@runCatching
            val target = File(uri.path.orEmpty()).canonicalFile
            if (target.parentFile == directory) target.delete()
        }
    }
}
