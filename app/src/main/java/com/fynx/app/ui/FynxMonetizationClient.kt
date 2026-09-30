package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

/** Server-authoritative Business/Creator monetization API client. */
object FynxMonetizationClient {
    data class Plan(val plan: String, val amountKobo: Long?, val currency: String, val configured: Boolean)
    data class PlanCatalog(val chargingEnabled: Boolean, val durationDays: Int, val plans: List<Plan>)
    data class Entitlements(val plan: String, val active: Boolean, val expiresAt: String?)
    data class PaymentStart(val reference: String, val authorizationUrl: String?, val accessCode: String?, val plan: String, val amountKobo: Long, val currency: String)

    suspend fun plans(context: Context): Result<PlanCatalog> =
        FynxBackendClient.get(context, "/api/monetization/plans").mapCatching { raw ->
            val json = JSONObject(raw)
            val items = json.optJSONArray("plans") ?: org.json.JSONArray()
            val plans = buildList(items.length()) {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    add(Plan(item.optString("plan"), item.optLong("amountKobo").takeIf { !item.isNull("amountKobo") }, item.optString("currency"), item.optBoolean("configured")))
                }
            }
            PlanCatalog(json.optBoolean("chargingEnabled"), json.optInt("durationDays", 30), plans)
        }

    suspend fun entitlements(context: Context): Result<Entitlements> =
        FynxBackendClient.get(context, "/api/monetization/entitlements").mapCatching { raw ->
            val business = JSONObject(raw).optJSONObject("business") ?: JSONObject()
            Entitlements(business.optString("plan", "FREE"), business.optBoolean("active", true), business.optString("expires_at").takeIf { it.isNotBlank() })
        }

    suspend fun initializePayment(context: Context, plan: String): Result<PaymentStart> {
        val normalized = plan.trim().uppercase()
        require(normalized == "BUSINESS" || normalized == "CREATOR") { "Unsupported paid plan" }
        return FynxBackendClient.postJson(context, "/api/monetization/plans/$normalized/payment", "{}").mapCatching { raw ->
            val json = JSONObject(raw)
            PaymentStart(
                reference = json.getString("reference"),
                authorizationUrl = json.optString("authorizationUrl").takeIf { it.isNotBlank() },
                accessCode = json.optString("accessCode").takeIf { it.isNotBlank() },
                plan = json.getString("plan"),
                amountKobo = json.getLong("amountKobo"),
                currency = json.getString("currency")
            )
        }
    }

    suspend fun verifyPayment(context: Context, reference: String): Result<JSONObject> {
        val body = JSONObject().put("reference", reference.trim())
        return FynxBackendClient.postJson(context, "/api/monetization/plans/payment/verify", body.toString()).mapCatching { JSONObject(it) }
    }
}
