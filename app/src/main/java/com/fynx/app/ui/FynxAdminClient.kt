package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

/** Server-authoritative client for public announcements and owner/admin controls. */
object FynxAdminClient {
    data class Announcement(val id: String, val title: String, val body: String, val priority: String, val publishedAt: String)
    data class Dashboard(val role: String, val users: Int, val openReports: Int, val openAppeals: Int, val safetyEvents24h: Int)

    suspend fun announcements(context: Context): Result<List<Announcement>> =
        FynxBackendClient.get(context, "/api/announcements").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("announcements") ?: return@mapCatching emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(Announcement(
                        id = item.optString("id"),
                        title = item.optString("title"),
                        body = item.optString("body"),
                        priority = item.optString("priority", "NORMAL"),
                        publishedAt = item.optString("publishedAt")
                    ))
                }
            }
        }

    suspend fun dashboard(context: Context): Result<Dashboard> =
        FynxBackendClient.get(context, "/api/admin/dashboard").mapCatching { raw ->
            val value = JSONObject(raw)
            val counts = value.optJSONObject("counts") ?: JSONObject()
            Dashboard(
                role = value.optString("role"),
                users = counts.optInt("users"),
                openReports = counts.optInt("openReports"),
                openAppeals = counts.optInt("openAppeals"),
                safetyEvents24h = counts.optInt("safetyEvents24h")
            )
        }

    suspend fun publishAnnouncement(context: Context, title: String, body: String, priority: String): Result<Unit> =
        FynxBackendClient.postJson(context, "/api/admin/announcements", JSONObject()
            .put("title", title.trim()).put("body", body.trim()).put("priority", priority).toString()).map { Unit }

    suspend fun setAccountStatus(context: Context, userId: String, status: String): Result<Unit> =
        FynxBackendClient.patchJson(context, "/api/admin/accounts/${encode(userId)}", JSONObject().put("status", status).toString()).map { Unit }

    suspend fun grantAdmin(context: Context, userId: String): Result<Unit> =
        FynxBackendClient.postJson(context, "/api/admin/admins", JSONObject().put("userId", userId).toString()).map { Unit }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
