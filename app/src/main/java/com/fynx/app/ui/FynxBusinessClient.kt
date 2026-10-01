package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

data class FynxBusinessProfile(
    val id: String?, val businessName: String, val businessUsername: String, val category: String,
    val description: String, val location: String, val phone: String, val website: String,
    val verified: Boolean, val active: Boolean
)

object FynxBusinessClient {
    private fun parse(value: JSONObject?): FynxBusinessProfile? = value?.let {
        FynxBusinessProfile(
            id = it.optString("id").takeIf(String::isNotBlank), businessName = it.optString("business_name"),
            businessUsername = it.optString("business_username"), category = it.optString("category"),
            description = it.optString("description"), location = it.optString("location"),
            phone = it.optString("phone"), website = it.optString("website"),
            verified = it.optBoolean("verified"), active = it.optBoolean("active", true)
        )
    }

    suspend fun load(context: Context): Result<FynxBusinessProfile?> =
        FynxBackendClient.get(context, "/api/business/profile").mapCatching { parse(JSONObject(it).optJSONObject("profile")) }

    suspend fun save(context: Context, businessName: String, businessUsername: String, category: String, description: String, location: String, phone: String, website: String): Result<FynxBusinessProfile> {
        val body = JSONObject().apply {
            put("businessName", businessName); put("businessUsername", businessUsername.removePrefix("@")); put("category", category)
            put("description", description); put("location", location); put("phone", phone); put("website", website)
        }
        return FynxBackendClient.postJson(context, "/api/business/profile", body.toString()).mapCatching { parse(JSONObject(it).optJSONObject("profile")) ?: error("business profile missing") }
    }

    suspend fun overview(context: Context): Result<JSONObject> =
        FynxBackendClient.get(context, "/api/business/overview").mapCatching { JSONObject(it).optJSONObject("overview") ?: JSONObject() }
}
