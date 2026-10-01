package com.fynx.app.ui

import android.content.Context
import org.json.JSONObject

object FynxNotificationPreferencesClient {
    private const val PREFS = "fynx_notification_preferences"
    private const val KEY_SERVER = "server_preferences"

    private fun accountKey(context: Context): String =
        FynxAuthStore.accountStorageKey(context)
            ?.trim()
            ?.lowercase()
            ?.map { c -> if (c.isLetterOrDigit()) c else '_' }
            ?.joinToString("")
            ?.take(80)
            ?.ifBlank { "account" }
            ?: "signed_out"

    private fun prefs(context: Context) =
        context.getSharedPreferences("${PREFS}_${accountKey(context)}", Context.MODE_PRIVATE)

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
            val accountPrefs = prefs(context)
            val raw = accountPrefs.getString(KEY_SERVER, null)
            if (!raw.isNullOrBlank()) return@runCatching parse(JSONObject(raw))

            val legacyPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val legacyRaw = legacyPrefs.getString(KEY_SERVER, null)
            if (legacyRaw.isNullOrBlank()) return@runCatching FynxNotificationPreferences()

            val migrated = parse(JSONObject(legacyRaw))
            accountPrefs.edit().putString(KEY_SERVER, toJson(migrated).toString()).apply()
            legacyPrefs.edit().remove(KEY_SERVER).apply()
            migrated
        }.getOrDefault(FynxNotificationPreferences())

    private fun cache(context: Context, preferences: FynxNotificationPreferences) {
        prefs(context).edit()
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
