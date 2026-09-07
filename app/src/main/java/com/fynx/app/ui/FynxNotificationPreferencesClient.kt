package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

object FynxNotificationPreferencesClient {
    private const val PREFS = "fynx_notification_preferences"
    private const val KEY_SERVER = "server_preferences"

    suspend fun load(context: Context): Result<FynxNotificationPreferences> =
        FynxBackendClient.get(context, "/api/notification-preferences").mapCatching { raw ->
            parse(JSONObject(raw).optJSONObject("preferences") ?: JSONObject()).also { cache(context, it) }
        }

    suspend fun update(context: Context, preferences: FynxNotificationPreferences): Result<FynxNotificationPreferences> {
        val body = JSONObject().apply {
            put("enabled", preferences.enabled); put("pushEnabled", preferences.pushEnabled); put("reactionsEnabled", preferences.reactionsEnabled)
            put("commentsEnabled", preferences.commentsEnabled); put("friendRequestsEnabled", preferences.friendRequestsEnabled); put("messagesEnabled", preferences.messagesEnabled)
            put("storiesEnabled", preferences.storiesEnabled); put("remindersEnabled", preferences.remindersEnabled); put("groupEnabled", preferences.groupEnabled)
            put("marketplaceEnabled", preferences.marketplaceEnabled); put("walletEnabled", preferences.walletEnabled); put("quietMode", preferences.quietMode)
        }
        return FynxBackendClient.patchJson(context, "/api/notification-preferences", body.toString()).mapCatching { raw ->
            parse(JSONObject(raw).optJSONObject("preferences") ?: JSONObject()).also { cache(context, it) }
        }
    }

    fun cached(context: Context): FynxNotificationPreferences =
        runCatching {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SERVER, null) ?: return FynxNotificationPreferences()
            parse(JSONObject(raw))
        }.getOrDefault(FynxNotificationPreferences())

    private fun cache(context: Context, preferences: FynxNotificationPreferences) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_SERVER, toJson(preferences).toString()).apply()
    }

    private fun toJson(preferences: FynxNotificationPreferences) = JSONObject().apply {
        put("enabled", preferences.enabled); put("pushEnabled", preferences.pushEnabled); put("reactionsEnabled", preferences.reactionsEnabled)
        put("commentsEnabled", preferences.commentsEnabled); put("friendRequestsEnabled", preferences.friendRequestsEnabled); put("messagesEnabled", preferences.messagesEnabled)
        put("storiesEnabled", preferences.storiesEnabled); put("remindersEnabled", preferences.remindersEnabled); put("groupEnabled", preferences.groupEnabled)
        put("marketplaceEnabled", preferences.marketplaceEnabled); put("walletEnabled", preferences.walletEnabled); put("quietMode", preferences.quietMode)
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
