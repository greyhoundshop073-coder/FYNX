package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

data class FynxBusinessProfile(
    val id: String?,
    val businessName: String,
    val businessUsername: String,
    val category: String,
    val description: String,
    val location: String,
    val phone: String,
    val website: String,
    val verified: Boolean,
    val active: Boolean
)

object FynxBusinessClient {
    private fun parse(value: JSONObject?): FynxBusinessProfile? {
        if (value == null) return null
        return FynxBusinessProfile(
            id = value.optString("id").takeIf { it.isNotBlank() },
            businessName = value.optString("business_name"),
            businessUsername = value.optString("business_username"),
            category = value.optString("category"),
            description = value.optString("description"),
            location = value.optString("location"),
            phone = value.optString("phone"),
            website = value.optString("website"),
            verified = value.optBoolean("verified"),
            active = value.optBoolean("active", true)
        )
    }

    suspend fun load(context: Context): Result<FynxBusinessProfile?> =
        FynxBackendClient.get(context, "/api/business/profile").mapCatching { raw ->
            parse(JSONObject(raw).optJSONObject("profile"))
        }

    suspend fun save(
        context: Context,
        businessName: String,
        businessUsername: String,
        category: String,
        description: String,
        location: String,
        phone: String,
        website: String
    ): Result<FynxBusinessProfile> {
        val body = JSONObject().apply {
            put("businessName", businessName)
            put("businessUsername", businessUsername.removePrefix("@"))
            put("category", category)
            put("description", description)
            put("location", location)
            put("phone", phone)
            put("website", website)
        }
        return FynxBackendClient.patchJson(context, "/api/business/profile", body.toString()).mapCatching { raw ->
            parse(JSONObject(raw).optJSONObject("profile")) ?: error("business profile missing")
        }
    }

    suspend fun overview(context: Context): Result<JSONObject> =
        FynxBackendClient.get(context, "/api/business/overview").mapCatching { raw ->
            JSONObject(raw).optJSONObject("overview") ?: JSONObject()
        }
}
