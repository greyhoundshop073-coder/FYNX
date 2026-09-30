package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

data class FynxBusinessEntitlement(
    val plan: String,
    val active: Boolean,
    val expiresAt: String?,
    val chargingEnabled: Boolean
)

object FynxMonetizationClient {
    suspend fun entitlement(context: Context): Result<FynxBusinessEntitlement> =
        FynxBackendClient.get(context, "/api/monetization/entitlements").mapCatching { raw ->
            val json = JSONObject(raw)
            val business = json.optJSONObject("business") ?: JSONObject()
            FynxBusinessEntitlement(
                plan = business.optString("plan", "FREE"),
                active = business.optBoolean("active", false),
                expiresAt = business.optString("expires_at").ifBlank { null },
                chargingEnabled = json.optBoolean("chargingEnabled", false)
            )
        }
}
