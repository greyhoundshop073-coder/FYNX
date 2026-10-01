package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

object FynxAdvertisingClient {
    data class PaymentInit(val authorizationUrl: String, val reference: String, val amountKobo: Long, val currency: String)

    suspend fun campaigns(context: Context): Result<List<JSONObject>> =
        FynxBackendClient.get(context, "/api/advertising/campaigns").mapCatching { raw ->
            val array = JSONObject(raw).optJSONArray("campaigns")
            (0 until (array?.length() ?: 0)).map { i -> array!!.getJSONObject(i) }
        }

    suspend fun createCampaign(context: Context, payload: JSONObject): Result<JSONObject> =
        FynxBackendClient.postJson(context, "/api/advertising/campaigns", payload.toString()).mapCatching { raw ->
            JSONObject(raw).optJSONObject("campaign") ?: error("campaign missing")
        }

    suspend fun submitReview(context: Context, campaignId: Long): Result<JSONObject> =
        FynxBackendClient.postJson(context, "/api/advertising/campaigns/$campaignId/submit-review", "{}").mapCatching { raw ->
            JSONObject(raw).optJSONObject("campaign") ?: error("campaign missing")
        }

    suspend fun initializePayment(context: Context, campaignId: Long, email: String = ""): Result<PaymentInit> =
        FynxBackendClient.postJson(context, "/api/advertising/campaigns/$campaignId/payment/initialize", JSONObject().apply { if (email.isNotBlank()) put("email", email) }.toString()).mapCatching { raw ->
            val value = JSONObject(raw)
            PaymentInit(
                authorizationUrl = value.getString("authorizationUrl"),
                reference = value.getString("reference"),
                amountKobo = value.getLong("amountKobo"),
                currency = value.optString("currency", "NGN")
            )
        }

    suspend fun verifyPayment(context: Context, campaignId: Long, reference: String): Result<JSONObject> =
        FynxBackendClient.postJson(context, "/api/advertising/campaigns/$campaignId/payment/verify", JSONObject().put("reference", reference).toString()).mapCatching { raw ->
            JSONObject(raw).optJSONObject("campaign") ?: error("campaign missing")
        }

    suspend fun setStatus(context: Context, campaignId: Long, status: String): Result<JSONObject> =
        FynxBackendClient.patchJson(context, "/api/advertising/campaigns/$campaignId/status", JSONObject().put("status", status).toString()).mapCatching { raw ->
            JSONObject(raw).optJSONObject("campaign") ?: error("campaign missing")
        }

    suspend fun dashboard(context: Context): Result<JSONObject> =
        FynxBackendClient.get(context, "/api/advertising/dashboard").mapCatching { raw ->
            JSONObject(raw).optJSONObject("dashboard") ?: JSONObject()
        }
}
