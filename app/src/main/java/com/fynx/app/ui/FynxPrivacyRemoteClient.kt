package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

object FynxPrivacyRemoteClient {
    private val keys = listOf(
        "profile_visibility",
        "online_visibility",
        "posts_visibility",
        "status_visibility",
        "profile_photo_visibility",
        "messages_visibility"
    )

    data class Settings(val values: Map<String, String>) {
        operator fun get(key: String): String = values[key] ?: "My friends"
    }

    suspend fun load(context: Context): Result<Settings> =
        FynxBackendClient.get(context, "/api/privacy").mapCatching { raw ->
            parse(JSONObject(raw).optJSONObject("settings") ?: JSONObject())
        }

    suspend fun update(context: Context, key: String, value: String): Result<Settings> {
        require(key in keys) { "Unknown privacy setting" }
        require(value == "Everyone" || value == "My friends" || value == "Nobody") { "Invalid privacy value" }
        return FynxBackendClient.patchJson(
            context,
            "/api/privacy",
            JSONObject().put(key, value).toString()
        ).mapCatching { raw ->
            parse(JSONObject(raw).optJSONObject("settings") ?: JSONObject())
        }
    }

    private fun parse(json: JSONObject): Settings =
        Settings(keys.associateWith { json.optString(it).ifBlank { "My friends" } })
}
