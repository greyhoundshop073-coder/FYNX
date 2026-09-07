package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

object FynxSafetyRemoteClient {
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

    data class Appeal(
        val id: String,
        val reportId: String?,
        val subject: String,
        val status: String,
        val decisionNote: String
    )

    suspend fun load(context: Context): Result<Safety> =
        FynxBackendClient.get(context, "/api/safety").mapCatching { raw ->
            val json = JSONObject(raw).optJSONObject("safety") ?: JSONObject()
            Safety(
                messageSafety = json.optBoolean("messageSafety", true),
                marketplaceSafety = json.optBoolean("marketplaceSafety", true),
                loginAlerts = json.optBoolean("loginAlerts", true),
                accountStatus = json.optString("accountStatus", "ACTIVE"),
                statusNote = json.optString("statusNote", "")
            )
        }

    suspend fun update(context: Context, key: String, enabled: Boolean): Result<Safety> =
        FynxBackendClient.patchJson(
            context,
            "/api/safety",
            JSONObject().put(key, enabled).toString()
        ).mapCatching { raw ->
            val json = JSONObject(raw).optJSONObject("safety") ?: JSONObject()
            Safety(
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
