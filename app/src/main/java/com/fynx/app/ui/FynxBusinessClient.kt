package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

data class FynxBusinessProfile(
    val id: String?, val businessName: String, val businessUsername: String, val category: String,
    val description: String, val location: String, val phone: String, val website: String,
    val verified: Boolean, val active: Boolean,
    val openingHours: JSONObject, val serviceAreas: org.json.JSONArray, val services: org.json.JSONArray
)

object FynxBusinessClient {
    private fun parse(value: JSONObject?): FynxBusinessProfile? = value?.let {
        FynxBusinessProfile(
            id = it.optString("id").takeIf(String::isNotBlank), businessName = it.optString("business_name"),
            businessUsername = it.optString("business_username"), category = it.optString("category"),
            description = it.optString("description"), location = it.optString("location"),
            phone = it.optString("phone"), website = it.optString("website"),
            verified = it.optBoolean("verified"), active = it.optBoolean("active", true),
            openingHours = it.optJSONObject("opening_hours") ?: JSONObject(),
            serviceAreas = it.optJSONArray("service_areas") ?: org.json.JSONArray(),
            services = it.optJSONArray("services") ?: org.json.JSONArray()
        )
    }

    suspend fun load(context: Context): Result<FynxBusinessProfile?> =
        FynxBackendClient.get(context, "/api/business/profile").mapCatching { parse(JSONObject(it).optJSONObject("profile")) }

    suspend fun save(context: Context, businessName: String, businessUsername: String, category: String, description: String, location: String, phone: String, website: String, openingHoursText: String = "", serviceAreasText: String = "", servicesText: String = ""): Result<FynxBusinessProfile> {
        val openingHours = JSONObject()
        openingHoursText.lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
            val split = line.split(":", limit = 2)
            if (split.size == 2 && split[0].isNotBlank() && split[1].isNotBlank()) openingHours.put(split[0].trim().take(20), split[1].trim().take(60))
        }
        val serviceAreas = org.json.JSONArray()
        serviceAreasText.split(",", "\\n").map { it.trim() }.filter { it.isNotBlank() }.take(20).forEach { serviceAreas.put(it.take(80)) }
        val services = org.json.JSONArray()
        servicesText.lines().map { it.trim() }.filter { it.isNotBlank() }.take(30).forEach { line ->
            val parts = line.split("|").map { it.trim() }
            services.put(JSONObject().apply {
                put("name", parts.getOrNull(0).orEmpty().take(100))
                put("price", parts.getOrNull(1).orEmpty().take(32))
                put("currency", parts.getOrNull(2).orEmpty().uppercase().takeIf { it in listOf("NGN", "USD") } ?: "NGN")
                put("description", parts.getOrNull(3).orEmpty().take(240))
            })
        }
        val body = JSONObject().apply {
            put("businessName", businessName); put("businessUsername", businessUsername.removePrefix("@")); put("category", category)
            put("description", description); put("location", location); put("phone", phone); put("website", website)
            put("openingHours", openingHours); put("serviceAreas", serviceAreas); put("services", services)
        }
        return FynxBackendClient.postJson(context, "/api/business/profile", body.toString()).mapCatching { parse(JSONObject(it).optJSONObject("profile")) ?: error("business profile missing") }
    }

    suspend fun overview(context: Context): Result<JSONObject> =
        FynxBackendClient.get(context, "/api/business/overview").mapCatching { JSONObject(it).optJSONObject("overview") ?: JSONObject() }
}
