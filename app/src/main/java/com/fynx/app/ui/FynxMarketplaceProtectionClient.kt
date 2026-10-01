package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

/** Account-scoped marketplace protection client. Protection state is authoritative on the backend. */
object FynxMarketplaceProtectionClient {
    data class ProtectionCase(
        val id: String,
        val type: String,
        val reason: String,
        val details: String,
        val status: String,
        val createdAt: String,
        val updatedAt: String
    )

    suspend fun openDispute(context: Context, orderId: String, reason: String, details: String): Result<ProtectionCase> =
        open(context, orderId, "dispute", reason, details)

    suspend fun requestRefund(context: Context, orderId: String, reason: String, details: String): Result<ProtectionCase> =
        open(context, orderId, "refund-request", reason, details)

    suspend fun cases(context: Context, orderId: String): Result<List<ProtectionCase>> =
        FynxBackendClient.get(context, "/api/marketplace/protection/order/$orderId/cases")
            .mapCatching { raw ->
                val array = JSONObject(raw).optJSONArray("cases") ?: org.json.JSONArray()
                buildList {
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        add(
                            ProtectionCase(
                                item.optString("id"),
                                item.optString("case_type"),
                                item.optString("reason"),
                                item.optString("details"),
                                item.optString("status"),
                                item.optString("created_at"),
                                item.optString("updated_at")
                            )
                        )
                    }
                }
            }

    private suspend fun open(context: Context, orderId: String, action: String, reason: String, details: String): Result<ProtectionCase> {
        val safeReason = reason.trim().take(160)
        if (safeReason.length < 3) return Result.failure(IllegalArgumentException("A short reason is required."))
        return FynxBackendClient.postJson(
            context,
            "/api/marketplace/protection/order/$orderId/$action",
            JSONObject().apply {
                put("reason", safeReason)
                put("details", details.trim().take(2000))
            }.toString()
        ).mapCatching { raw ->
            val item = JSONObject(raw).getJSONObject("case")
            ProtectionCase(
                item.optString("id"),
                item.optString("case_type"),
                item.optString("reason"),
                item.optString("details"),
                item.optString("status"),
                item.optString("created_at"),
                item.optString("updated_at")
            )
        }
    }
}
