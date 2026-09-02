package com.fynx.app.ui

import android.content.Context

/**
 * Local persistence for the existing profile/settings foundation.
 * Keep this store separate from authentication so profile preferences can
 * evolve independently when the production backend is connected.
 */
object FynxPreferencesStore {
    private const val PREFS = "fynx_preferences"

    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_USERNAME = "username"
    private const val KEY_BIO = "bio"
    private const val KEY_DESCRIPTION = "description"
    private const val KEY_NOTIFICATIONS = "notifications"
    private const val KEY_PRIVATE_PROFILE = "private_profile"
    private const val KEY_READ_RECEIPTS = "read_receipts"
    private const val KEY_STORY_REPLIES = "story_replies"
    private const val KEY_ACCENT = "accent"
    private const val KEY_PROFILE_PHOTO = "profile_photo_uri"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_APPEARANCE = "appearance"
    private const val KEY_ASSET = "selected_asset_uri"

    fun loadProfile(context: Context, fallbackUsername: String?): FynxProfile {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val username = prefs.getString(KEY_USERNAME, null)
            ?: fallbackUsername?.removePrefix("@")
            ?: "username"
        return FynxProfile(
            displayName = prefs.getString(KEY_DISPLAY_NAME, null)?.takeIf { it.isNotBlank() } ?: "Your name",
            username = username.removePrefix("@"),
            bio = prefs.getString(KEY_BIO, null) ?: "Welcome to FYNX"
        )
    }

    fun loadDescription(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DESCRIPTION, "") ?: ""

    fun saveDescription(context: Context, description: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_DESCRIPTION, description.trim()).apply()
    }

    fun saveProfile(context: Context, profile: FynxProfile) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_DISPLAY_NAME, profile.displayName.trim())
            .putString(KEY_USERNAME, profile.username.removePrefix("@").trim())
            .putString(KEY_BIO, profile.bio.trim())
            .apply()
    }

    fun loadSettings(context: Context): FynxSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return FynxSettings(
            notifications = prefs.getBoolean(KEY_NOTIFICATIONS, true),
            privateProfile = prefs.getBoolean(KEY_PRIVATE_PROFILE, false),
            readReceipts = prefs.getBoolean(KEY_READ_RECEIPTS, true),
            storyReplies = prefs.getBoolean(KEY_STORY_REPLIES, true)
        )
    }

    fun saveSettings(context: Context, settings: FynxSettings) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_NOTIFICATIONS, settings.notifications)
            .putBoolean(KEY_PRIVATE_PROFILE, settings.privateProfile)
            .putBoolean(KEY_READ_RECEIPTS, settings.readReceipts)
            .putBoolean(KEY_STORY_REPLIES, settings.storyReplies)
            .apply()
    }

    fun loadVisibility(context: Context, key: String, default: String = "Everyone"): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, default) ?: default

    fun saveVisibility(context: Context, key: String, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, value).apply()
    }

    fun loadAccent(context: Context): FynxAccent {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACCENT, FynxAccent.Blue.name)
        return runCatching { FynxAccent.valueOf(stored ?: FynxAccent.Blue.name) }.getOrDefault(FynxAccent.Blue)
    }

    fun saveAccent(context: Context, accent: FynxAccent) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ACCENT, accent.name).apply()
    }

    fun loadProfilePhoto(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PROFILE_PHOTO, null)

    fun saveProfilePhoto(context: Context, uri: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (uri.isNullOrBlank()) remove(KEY_PROFILE_PHOTO) else putString(KEY_PROFILE_PHOTO, uri)
        }.apply()
    }

    fun loadAppearance(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_APPEARANCE, "System") ?: "System"

    fun saveAppearance(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_APPEARANCE, value).apply()
    }

    fun loadLanguage(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, "Device default") ?: "Device default"

    fun saveLanguage(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LANGUAGE, value).apply()
    }

    fun loadAsset(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ASSET, null)

    fun saveAsset(context: Context, uri: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (uri.isNullOrBlank()) remove(KEY_ASSET) else putString(KEY_ASSET, uri)
        }.apply()
    }
}
