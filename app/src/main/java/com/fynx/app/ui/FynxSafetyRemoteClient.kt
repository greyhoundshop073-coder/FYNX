package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

object FynxSafetyRemoteClient {
    private val updateKeys = setOf("messageSafety", "marketplaceSafety", "loginAlerts")

    data class Safety(
        val messageSafety: Boolean,
        val marketplaceSafety: Boolean,
        val loginAlerts: Boolean,
        val accountStatus: String,
        val statusNote: String
    )

    data class Report(
        val id: String,
        val targetUsername: String,
        val reason: String,
        val status: String
    )

    data class ContentReport(
        val id: String,
        val targetType: String,
        val targetId: String,
        val reason: String,
        val status: String
    )

    data class Appeal(
        val id: String,
        val reportId: String?,
        val subject: String,
        val status: String,
        val decisionNote: String
    )

    suspend fun load(context: Context): Result<Safety> =
        FynxBackendClient.get(context, "/api/safety").mapCatching { raw ->
            parseSafety(raw)
        }

    suspend fun update(context: Context, key: String, enabled: Boolean): Result<Safety> {
        require(key in updateKeys) { "Unknown safety setting" }
        return FynxBackendClient.patchJson(
            context,
            "/api/safety",
            JSONObject().put(key, enabled).toString()
        ).mapCatching { raw -> parseSafety(raw) }
    }

    private fun parseSafety(raw: String): Safety {
        val json = JSONObject(raw).optJSONObject("safety") ?: JSONObject()
        return Safety(
            messageSafety = json.optBoolean("messageSafety", true),
            marketplaceSafety = json.optBoolean("marketplaceSafety", true),
            loginAlerts = json.optBoolean("loginAlerts", true),
            accountStatus = json.optString("accountStatus", "ACTIVE"),
            statusNote = json.optString("statusNote", "")
        )
    }

    suspend fun reports(context: Context): Result<List<Report>> =
        FynxBackendClient.get(context, "/api/social/reports/mine").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("reports") ?: org.json.JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(Report(item.optString("id"), item.optString("targetUsername"), item.optString("reason"), item.optString("status")))
                }
            }
        }

    suspend fun submitReport(context: Context, targetUsername: String, reason: String, details: String): Result<Report> =
        FynxBackendClient.postJson(context, "/api/social/reports", JSONObject().put("targetUsername", targetUsername.trim().removePrefix("@")).put("reason", reason.trim()).put("details", details.trim()).toString()).mapCatching { raw ->
            val item = JSONObject(raw).optJSONObject("report") ?: JSONObject()
            Report(item.optString("id"), item.optString("targetUsername"), item.optString("reason"), item.optString("status"))
        }

    suspend fun submitContentReport(
        context: Context,
        targetType: String,
        targetId: String,
        reason: String,
        details: String = ""
    ): Result<ContentReport> {
        val normalizedType = targetType.trim().uppercase()
        val numericId = targetId.trim().toLongOrNull()
        require(normalizedType in setOf("SOCIAL_POST", "SOCIAL_COMMENT", "MARKETPLACE_LISTING")) { "Unsupported content type" }
        require(numericId != null && numericId > 0L) { "Invalid content identifier" }
        require(reason.trim().length >= 3) { "Please select a report reason" }
        return FynxBackendClient.postJson(
            context,
            "/api/social/content-reports",
            JSONObject().put("targetType", normalizedType).put("targetId", numericId)
                .put("reason", reason.trim()).put("details", details.trim().take(2000)).toString()
        ).mapCatching { raw ->
            val item = JSONObject(raw).optJSONObject("report") ?: error("Report response was incomplete")
            ContentReport(
                id = item.optString("id"),
                targetType = item.optString("targetType"),
                targetId = item.optString("targetId"),
                reason = item.optString("reason"),
                status = item.optString("status")
            ).also { report -> require(report.id.isNotBlank() && report.targetId.isNotBlank()) { "Report response was incomplete" } }
        }
    }

    suspend fun contentReports(context: Context): Result<List<ContentReport>> =
        FynxBackendClient.get(context, "/api/social/content-reports/mine").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("reports") ?: org.json.JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(ContentReport(item.optString("id"), item.optString("targetType"), item.optString("targetId"), item.optString("reason"), item.optString("status")))
                }
            }
        }

    suspend fun appeals(context: Context): Result<List<Appeal>> =
        FynxBackendClient.get(context, "/api/safety/appeals").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("appeals") ?: org.json.JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(Appeal(item.optString("id"), item.optString("reportId").takeIf { it.isNotBlank() && it != "null" }, item.optString("subject"), item.optString("status"), item.optString("decisionNote")))
                }
            }
        }

    suspend fun submitAppeal(context: Context, reportId: String?, subject: String, details: String): Result<Appeal> =
        FynxBackendClient.postJson(
            context,
            "/api/safety/appeals",
            JSONObject().apply {
                if (!reportId.isNullOrBlank()) put("reportId", reportId.toLongOrNull() ?: -1)
                put("subject", subject.trim())
                put("details", details.trim())
            }.toString()
        ).mapCatching { raw ->
            val item = JSONObject(raw).optJSONObject("appeal") ?: JSONObject()
            Appeal(item.optString("id"), item.optString("reportId").takeIf { it.isNotBlank() && it != "null" }, item.optString("subject"), item.optString("status"), item.optString("decisionNote"))
        }
}
