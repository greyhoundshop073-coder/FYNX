package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

/** Server-authoritative client for public announcements and owner/admin controls. */
object FynxAdminClient {
    data class Announcement(val id: String, val title: String, val body: String, val priority: String, val publishedAt: String)
    data class Dashboard(val role: String, val users: Int, val openReports: Int, val openAppeals: Int, val safetyEvents24h: Int)
    data class Admin(val id: String, val username: String, val displayName: String, val grantedAt: String)
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

    suspend fun announcements(context: Context): Result<List<Announcement>> =
        FynxBackendClient.get(context, "/api/announcements").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("announcements") ?: return@mapCatching emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(Announcement(item.optString("id"), item.optString("title"), item.optString("body"), item.optString("priority", "NORMAL"), item.optString("createdAt")))
                }
            }
        }

    suspend fun dashboard(context: Context): Result<Dashboard> =
        FynxBackendClient.get(context, "/api/admin/dashboard").mapCatching { raw ->
            val value = JSONObject(raw)
            val counts = value.optJSONObject("counts") ?: JSONObject()
            Dashboard(value.optString("role"), counts.optInt("users"), counts.optInt("openReports"), counts.optInt("openAppeals"), counts.optInt("safetyEvents24h"))
        }

    suspend fun admins(context: Context): Result<List<Admin>> =
        FynxBackendClient.get(context, "/api/admin/admins").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("admins") ?: return@mapCatching emptyList()
            buildList { for (i in 0 until array.length()) { val item = array.optJSONObject(i) ?: continue; add(Admin(item.optString("id"), item.optString("username"), item.optString("displayName"), item.optString("grantedAt"))) }
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

    suspend fun resolveMarketplaceProtectionCase(context: Context, caseId: String, resolution: String, note: String = ""): Result<String> =
        FynxBackendClient.postJson(
            context,
            "/api/admin/marketplace/protection/cases/${encode(caseId)}//resolve".replace("//", "/"),
            JSONObject().put("resolution", resolution.trim().uppercase()).put("note", note.trim()).toString()
        ).mapCatching { raw ->
            val value = JSONObject(raw)
            value.optString("status").ifBlank { value.optString("error", "Request accepted") }
        }

    suspend fun publishAnnouncement(context: Context, title: String, body: String, priority: String): Result<Unit> =
        FynxBackendClient.postJson(context, "/api/admin/announcements", JSONObject().put("title", title.trim()).put("body", body.trim()).put("priority", priority).toString()).map { Unit }

    suspend fun setAccountStatus(context: Context, userId: String, status: String): Result<Unit> =
        FynxBackendClient.patchJson(context, "/api/admin/accounts/${encode(userId)}", JSONObject().put("status", status).toString()).map { Unit }

    suspend fun grantAdmin(context: Context, userId: String): Result<Unit> =
        FynxBackendClient.postJson(context, "/api/admin/admins", JSONObject().put("userId", userId).toString()).map { Unit }

    suspend fun revokeAdmin(context: Context, userId: String): Result<Unit> =
        FynxBackendClient.delete(context, "/api/admin/admins/${encode(userId)}").map { Unit }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
