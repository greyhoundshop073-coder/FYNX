package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

data class FynxNotificationPreferences(
    val enabled: Boolean = true,
    val messages: Boolean = true,
    val friends: Boolean = true,
    val groups: Boolean = true,
    val marketplace: Boolean = true,
    val reminders: Boolean = true,
    val sound: Boolean = true,
    val vibration: Boolean = true
)

object FynxNotificationPreferencesClient {
    suspend fun load(context: Context): Result<FynxNotificationPreferences> =
        FynxBackendClient.get(context, "/api/notification-preferences").mapCatching { raw -> parse(JSONObject(raw).optJSONObject("preferences") ?: JSONObject()) }

    suspend fun update(context: Context, preferences: FynxNotificationPreferences): Result<FynxNotificationPreferences> {
        val body = JSONObject().apply {
            put("enabled", preferences.enabled); put("messages", preferences.messages); put("friends", preferences.friends)
            put("groups", preferences.groups); put("marketplace", preferences.marketplace); put("reminders", preferences.reminders)
            put("sound", preferences.sound); put("vibration", preferences.vibration)
        }
        return FynxBackendClient.patchJson(context, "/api/notification-preferences", body.toString()).mapCatching { raw -> parse(JSONObject(raw).optJSONObject("preferences") ?: JSONObject()) }
    }

    private fun parse(json: JSONObject) = FynxNotificationPreferences(
        enabled = json.optBoolean("enabled", true), messages = json.optBoolean("messages", true), friends = json.optBoolean("friends", true),
        groups = json.optBoolean("groups", true), marketplace = json.optBoolean("marketplace", true), reminders = json.optBoolean("reminders", true),
        sound = json.optBoolean("sound", true), vibration = json.optBoolean("vibration", true)
    )
}
