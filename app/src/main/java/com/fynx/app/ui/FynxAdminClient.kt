package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

/** Server-authoritative client for public announcements and owner/admin controls. */
object FynxAdminClient {
    data class Announcement(
        val id: String,
        val title: String,
        val body: String,
        val priority: String,
        val publishedAt: String,
        val mediaId: String? = null,
        val linkUrl: String = "",
        val scheduledAt: String? = null,
        val published: Boolean = true
    )
    data class Dashboard(val role: String, val users: Int, val openReports: Int, val openAppeals: Int, val safetyActions24h: Int)
    data class Admin(val id: String, val username: String, val displayName: String, val grantedAt: String)
    data class MusicTrack(
        val id: String,
        val mediaId: String,
        val title: String,
        val artist: String,
        val durationMs: Long,
        val category: String,
        val active: Boolean
    )
    data class AdminActivity(
        val id: String,
        val actorRole: String,
        val actorUsername: String,
        val actorDisplayName: String,
        val action: String,
        val targetType: String,
        val targetId: String,
        val summary: String,
        val createdAt: String
    )
    data class AdminPage<T>(
        val items: List<T>,
        val offset: Int,
        val limit: Int,
        val hasMore: Boolean
    )
    data class AdminReport(
        val id: String,
        val reporterUsername: String,
        val reporterDisplayName: String,
        val targetUsername: String,
        val reason: String,
        val details: String,
        val status: String,
        val createdAt: String
    )
    data class AppealReview(
        val id: String,
        val userId: String,
        val username: String,
        val displayName: String,
        val reportId: String?,
        val subject: String,
        val details: String,
        val status: String,
        val decisionNote: String,
        val accountStatus: String,
        val createdAt: String
    )
    data class AppealDecision(val restored: Boolean)
    data class ProtectionCase(
        val id: String,
        val orderId: String,
        val disputeId: String?,
        val role: String,
        val caseType: String,
        val reason: String,
        val details: String,
        val status: String,
        val buyerId: String,
        val sellerId: String,
        val totalAmount: Double,
        val currency: String,
        val orderStatus: String
    )
    data class MarketplaceDriver(
        val userId: String,
        val username: String,
        val displayName: String,
        val active: Boolean,
        val approvedAt: String,
        val updatedAt: String
    )

    data class RevenueBreakdown(
        val source: String,
        val status: String,
        val currency: String,
        val transactions: Int,
        val amount: Double
    )
    data class RevenueReport(
        val role: String,
        val breakdown: List<RevenueBreakdown>
    )

    private fun parseAnnouncements(raw: String): List<Announcement> {
        val array = JSONObject(raw).optJSONArray("announcements") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val mediaId = item.optString("mediaId").takeIf { it.isNotBlank() && it != "null" }
                val scheduledAt = item.optString("scheduledAt").takeIf { it.isNotBlank() && it != "null" }
                add(
                    Announcement(
                        id = item.optString("id"),
                        title = item.optString("title"),
                        body = item.optString("body"),
                        priority = item.optString("priority", "NORMAL"),
                        publishedAt = item.optString("publishedAt", item.optString("createdAt")),
                        mediaId = mediaId,
                        linkUrl = item.optString("linkUrl"),
                        scheduledAt = scheduledAt,
                        published = item.optBoolean("published", true)
                    )
                )
            }
        }
    }

    suspend fun announcements(context: Context): Result<List<Announcement>> =
        FynxBackendClient.get(context, "/api/announcements").mapCatching(::parseAnnouncements)

    suspend fun adminAnnouncements(context: Context): Result<List<Announcement>> =
        FynxBackendClient.get(context, "/api/admin/announcements").mapCatching(::parseAnnouncements)

    suspend fun dashboard(context: Context): Result<Dashboard> =
        FynxBackendClient.get(context, "/api/admin/dashboard").mapCatching { raw ->
            val value = JSONObject(raw)
            val counts = value.optJSONObject("counts") ?: JSONObject()
            Dashboard(value.optString("role"), counts.optInt("users"), counts.optInt("openReports"), counts.optInt("openAppeals"), counts.optInt("safetyActions24h"))
        }

    suspend fun revenue(context: Context): Result<RevenueReport> =
        FynxBackendClient.get(context, "/api/admin/revenue").mapCatching { raw ->
            val value = JSONObject(raw)
            val rows = value.optJSONArray("breakdown")
            val breakdown = buildList {
                if (rows != null) for (i in 0 until rows.length()) {
                    val item = rows.optJSONObject(i) ?: continue
                    add(
                        RevenueBreakdown(
                            source = item.optString("source"),
                            status = item.optString("status"),
                            currency = item.optString("currency", "NGN"),
                            transactions = item.optInt("transactions"),
                            amount = item.optDouble("amount", 0.0)
                        )
                    )
                }
            }
            RevenueReport(value.optString("role"), breakdown)
        }

    suspend fun admins(context: Context): Result<List<Admin>> =
        FynxBackendClient.get(context, "/api/admin/admins").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("admins") ?: return@mapCatching emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(Admin(item.optString("id"), item.optString("username"), item.optString("displayName"), item.optString("grantedAt")))
                }
            }
        }

    suspend fun musicCatalogue(context: Context): Result<List<MusicTrack>> =
        FynxBackendClient.get(context, "/api/admin/social/music/catalogue").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("tracks") ?: return@mapCatching emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        MusicTrack(
                            id = item.optString("id"),
                            mediaId = item.optString("mediaId"),
                            title = item.optString("title"),
                            artist = item.optString("artist"),
                            durationMs = item.optLong("durationMs", 0L),
                            category = item.optString("category", "FYNX"),
                            active = item.optBoolean("active", true)
                        )
                    )
                }
            }
        }

    suspend fun addMusicTrack(context: Context, mediaId: String, title: String, artist: String, durationMs: Long, category: String = "FYNX"): Result<MusicTrack> =
        FynxBackendClient.postJson(
            context,
            "/api/admin/social/music/catalogue",
            JSONObject()
                .put("mediaId", mediaId)
                .put("title", title.trim())
                .put("artist", artist.trim())
                .put("durationMs", durationMs.coerceAtLeast(0L))
                .put("category", category.trim().ifBlank { "FYNX" })
                .toString()
        ).mapCatching { raw ->
            val item = JSONObject(raw).optJSONObject("track") ?: throw IllegalStateException("Music track was not returned.")
            MusicTrack(
                id = item.optString("id"),
                mediaId = item.optString("mediaId"),
                title = item.optString("title"),
                artist = item.optString("artist"),
                durationMs = item.optLong("durationMs", 0L),
                category = item.optString("category", "FYNX"),
                active = item.optBoolean("active", true)
            )
        }

    suspend fun removeMusicTrack(context: Context, trackId: String): Result<Unit> =
        FynxBackendClient.delete(context, "/api/admin/social/music/catalogue/" + encode(trackId)).map { Unit }

    suspend fun setMusicTrackActive(context: Context, trackId: String, active: Boolean): Result<Unit> =
        FynxBackendClient.patchJson(
            context,
            "/api/admin/social/music/catalogue/" + encode(trackId),
            JSONObject().put("active", active).toString()
        ).map { Unit }

    suspend fun activity(context: Context, query: String = "", offset: Int = 0, action: String = ""): Result<AdminPage<AdminActivity>> =
        FynxBackendClient.get(
            context,
            "/api/admin/activity?q=${encode(query)}&action=${encode(action)}&limit=50&offset=${offset.coerceIn(0, 10000)}"
        ).mapCatching { raw ->
            val value = JSONObject(raw)
            val array = value.optJSONArray("activities")
            val items = buildList {
                if (array != null) for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(AdminActivity(item.optString("id"), item.optString("actorRole"), item.optString("actorUsername"), item.optString("actorDisplayName"), item.optString("action"), item.optString("targetType"), item.optString("targetId"), item.optString("summary"), item.optString("createdAt")))
                }
            }
            AdminPage(
                items = items,
                offset = value.optInt("offset", offset),
                limit = value.optInt("limit", 50),
                hasMore = value.optBoolean("hasMore", false)
            )
        }

    suspend fun reports(context: Context, offset: Int = 0): Result<AdminPage<AdminReport>> =
        FynxBackendClient.get(context, "/api/admin/reports?limit=50&offset=${offset.coerceIn(0, 10000)}").mapCatching { raw ->
            val value = JSONObject(raw)
            val array = value.optJSONArray("reports")
            val items = buildList {
                if (array != null) for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(AdminReport(
                        id = item.optString("id"),
                        reporterUsername = item.optString("reporterUsername"),
                        reporterDisplayName = item.optString("reporterDisplayName"),
                        targetUsername = item.optString("targetUsername"),
                        reason = item.optString("reason"),
                        details = item.optString("details"),
                        status = item.optString("status"),
                        createdAt = item.optString("createdAt")
                    ))
                }
            }
            AdminPage(items, value.optInt("offset", offset), value.optInt("limit", 50), value.optBoolean("hasMore", false))
        }

    suspend fun reviewReport(context: Context, reportId: String, status: String): Result<Unit> =
        FynxBackendClient.patchJson(
            context,
            "/api/admin/reports/${encode(reportId)}",
            JSONObject().put("status", status.trim().uppercase()).toString()
        ).map { Unit }

    suspend fun appeals(context: Context, offset: Int = 0): Result<AdminPage<AppealReview>> =
        FynxBackendClient.get(context, "/api/admin/appeals?limit=50&offset=${offset.coerceIn(0, 10000)}").mapCatching { raw ->
            val value = JSONObject(raw)
            val array = value.optJSONArray("appeals")
            val items = buildList {
                if (array != null) for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(AppealReview(item.optString("id"), item.optString("userId"), item.optString("username"), item.optString("displayName"), item.optString("reportId").takeIf { it.isNotBlank() && it != "null" }, item.optString("subject"), item.optString("details"), item.optString("status"), item.optString("decisionNote"), item.optString("accountStatus"), item.optString("createdAt")))
                }
            }
            AdminPage(items, value.optInt("offset", offset), value.optInt("limit", 50), value.optBoolean("hasMore", false))
        }

    suspend fun reviewAppeal(context: Context, appealId: String, status: String, decisionNote: String): Result<AppealDecision> =
        FynxBackendClient.patchJson(context, "/api/admin/appeals/${encode(appealId)}", JSONObject().put("status", status).put("decisionNote", decisionNote.take(500)).toString()).mapCatching { raw ->
            val appeal = JSONObject(raw).optJSONObject("appeal")
                ?: throw IllegalStateException("Appeal decision response was incomplete.")
            AppealDecision(restored = appeal.optBoolean("restored", false))
        }

    suspend fun marketplaceProtectionCases(context: Context, status: String? = null): Result<List<ProtectionCase>> {
        val path = if (status.isNullOrBlank()) "/api/admin/marketplace/protection/cases" else "/api/admin/marketplace/protection/cases?status=${encode(status.trim().uppercase())}"
        return FynxBackendClient.get(context, path).mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("cases") ?: return@mapCatching emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        ProtectionCase(
                            id = item.optString("id"),
                            orderId = item.optString("orderId"),
                            disputeId = item.optString("disputeId").takeIf { it.isNotBlank() && it != "null" },
                            role = item.optString("role"),
                            caseType = item.optString("caseType"),
                            reason = item.optString("reason"),
                            details = item.optString("details"),
                            status = item.optString("status"),
                            buyerId = item.optString("buyerId"),
                            sellerId = item.optString("sellerId"),
                            totalAmount = item.optDouble("totalAmount", 0.0),
                            currency = item.optString("currency", "NGN"),
                            orderStatus = item.optString("orderStatus")
                        )
                    )
                }
            }
        }
    }

    suspend fun marketplaceDrivers(context: Context): Result<List<MarketplaceDriver>> =
        FynxBackendClient.get(context, "/api/admin/marketplace/drivers").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("drivers") ?: return@mapCatching emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(MarketplaceDriver(
                        userId = item.optString("userId"),
                        username = item.optString("username"),
                        displayName = item.optString("displayName"),
                        active = item.optBoolean("active"),
                        approvedAt = item.optString("approvedAt"),
                        updatedAt = item.optString("updatedAt")
                    ))
                }
            }
        }

    suspend fun setMarketplaceDriverApproval(context: Context, userId: String, active: Boolean): Result<Unit> =
        FynxBackendClient.postJson(
            context,
            "/api/admin/marketplace/drivers/${encode(userId)}/approval",
            JSONObject().put("active", active).toString()
        ).map { Unit }

    suspend fun resolveMarketplaceProtectionCase(context: Context, caseId: String, resolution: String, note: String = ""): Result<String> =
        FynxBackendClient.postJson(
            context,
            "/api/admin/marketplace/protection/cases/${encode(caseId)}/resolve",
            JSONObject().put("resolution", resolution.trim().uppercase()).put("note", note.trim()).toString()
        ).mapCatching { raw ->
            val value = JSONObject(raw)
            value.optString("status").ifBlank { value.optString("error", "Request accepted") }
        }

    suspend fun publishAnnouncement(
        context: Context,
        title: String,
        body: String,
        priority: String,
        mediaId: String? = null,
        linkUrl: String = "",
        scheduledAt: String? = null
    ): Result<Unit> {
        val payload = JSONObject()
            .put("title", title.trim())
            .put("body", body.trim())
            .put("priority", priority)
            .put("mediaId", mediaId ?: JSONObject.NULL)
            .put("linkUrl", linkUrl.trim())
            .put("scheduledAt", scheduledAt ?: JSONObject.NULL)
        return FynxBackendClient.postJson(context, "/api/admin/announcements", payload.toString()).map { Unit }
    }

    suspend fun updateAnnouncement(
        context: Context,
        announcementId: String,
        title: String,
        body: String,
        priority: String,
        mediaId: String?,
        linkUrl: String,
        scheduledAt: String?,
        published: Boolean
    ): Result<Unit> {
        val payload = JSONObject()
            .put("title", title.trim())
            .put("body", body.trim())
            .put("priority", priority)
            .put("mediaId", mediaId ?: JSONObject.NULL)
            .put("linkUrl", linkUrl.trim())
            .put("scheduledAt", scheduledAt ?: JSONObject.NULL)
            .put("published", published)
        return FynxBackendClient.patchJson(
            context,
            "/api/admin/announcements/${encode(announcementId)}",
            payload.toString()
        ).map { Unit }
    }

    suspend fun setAnnouncementPublishing(
        context: Context,
        announcementId: String,
        published: Boolean,
        scheduledAt: String? = null
    ): Result<Unit> = FynxBackendClient.patchJson(
        context,
        "/api/admin/announcements/${encode(announcementId)}",
        JSONObject().put("published", published).put("scheduledAt", scheduledAt ?: JSONObject.NULL).toString()
    ).map { Unit }

    suspend fun setAccountStatus(context: Context, userId: String, status: String): Result<Unit> =
        FynxBackendClient.patchJson(context, "/api/admin/accounts/${encode(userId)}/status", JSONObject().put("status", status).toString()).map { Unit }

    suspend fun grantAdmin(context: Context, userId: String): Result<Unit> =
        FynxBackendClient.postJson(context, "/api/admin/admins", JSONObject().put("userId", userId).toString()).map { Unit }

    suspend fun revokeAdmin(context: Context, userId: String): Result<Unit> =
        FynxBackendClient.delete(context, "/api/admin/admins/${encode(userId)}").map { Unit }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
