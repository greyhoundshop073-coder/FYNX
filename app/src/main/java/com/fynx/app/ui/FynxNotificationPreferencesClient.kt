package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

object FynxNotificationPreferencesClient {
    suspend fun load(context: Context): Result<FynxNotificationPreferences> =
        FynxBackendClient.get(context, "/api/notification-preferences").mapCatching { raw -> parse(JSONObject(raw).optJSONObject("preferences") ?: JSONObject()) }

    suspend fun update(context: Context, preferences: FynxNotificationPreferences): Result<FynxNotificationPreferences> {
        val body = JSONObject().apply {
            put("enabled", preferences.enabled); put("pushEnabled", preferences.pushEnabled); put("reactionsEnabled", preferences.reactionsEnabled)
            put("commentsEnabled", preferences.commentsEnabled); put("friendRequestsEnabled", preferences.friendRequestsEnabled); put("messagesEnabled", preferences.messagesEnabled)
            put("storiesEnabled", preferences.storiesEnabled); put("remindersEnabled", preferences.remindersEnabled); put("groupEnabled", preferences.groupEnabled)
            put("marketplaceEnabled", preferences.marketplaceEnabled); put("walletEnabled", preferences.walletEnabled); put("quietMode", preferences.quietMode)
        }
        return FynxBackendClient.patchJson(context, "/api/notification-preferences", body.toString()).mapCatching { raw -> parse(JSONObject(raw).optJSONObject("preferences") ?: JSONObject()) }
    }

    private fun parse(json: JSONObject) = FynxNotificationPreferences(
        enabled = json.optBoolean("enabled", true), pushEnabled = json.optBoolean("pushEnabled", true),
        reactionsEnabled = json.optBoolean("reactionsEnabled", true), commentsEnabled = json.optBoolean("commentsEnabled", true),
        friendRequestsEnabled = json.optBoolean("friendRequestsEnabled", true), messagesEnabled = json.optBoolean("messagesEnabled", true),
        storiesEnabled = json.optBoolean("storiesEnabled", true), remindersEnabled = json.optBoolean("remindersEnabled", true),
        groupEnabled = json.optBoolean("groupEnabled", true), marketplaceEnabled = json.optBoolean("marketplaceEnabled", true),
        walletEnabled = json.optBoolean("walletEnabled", true), quietMode = json.optBoolean("quietMode", false)
    )
}
