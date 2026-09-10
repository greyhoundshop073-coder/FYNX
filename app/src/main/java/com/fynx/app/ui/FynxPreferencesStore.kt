package com.fynx.app.ui

import android.content.Context
import android.net.Uri
import java.io.File

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
    private const val KEY_NIGHT_MODE = "night_mode"
    private const val KEY_NIGHT_MODE_START = "night_mode_start"
    private const val KEY_NIGHT_MODE_END = "night_mode_end"
    private const val KEY_CHAT_WALLPAPER = "chat_wallpaper"
    private const val KEY_CHAT_LIST_VIEW = "chat_list_view"
    private const val KEY_STICKER_ANIMATION = "sticker_animation"
    private const val KEY_EMOJI_SIZE = "emoji_size"
    private const val KEY_CHAT_LIST_STATE = "chat_list_state"

    private const val DEFAULT_VISIBILITY = "My friends"

    fun loadProfile(context: Context, fallbackUsername: String?): FynxProfile {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val username = prefs.getString(KEY_USERNAME, null) ?: fallbackUsername?.removePrefix("@") ?: "username"
        return FynxProfile(
            displayName = prefs.getString(KEY_DISPLAY_NAME, null)?.takeIf { it.isNotBlank() } ?: "Your name",
            username = username.removePrefix("@"),
            bio = prefs.getString(KEY_BIO, null) ?: "Welcome to FYNX"
        )
    }
    fun loadDescription(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DESCRIPTION, "") ?: ""
    fun saveDescription(context: Context, description: String) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_DESCRIPTION, description.trim()).apply() }
    fun saveProfile(context: Context, profile: FynxProfile) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_DISPLAY_NAME, profile.displayName.trim()).putString(KEY_USERNAME, profile.username.removePrefix("@").trim()).putString(KEY_BIO, profile.bio.trim()).apply() }
    fun loadSettings(context: Context): FynxSettings {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return FynxSettings(
            notifications = prefs.getBoolean(KEY_NOTIFICATIONS, true),
            privateProfile = prefs.getBoolean(KEY_PRIVATE_PROFILE, false),
            readReceipts = prefs.getBoolean(KEY_READ_RECEIPTS, true),
            storyReplies = prefs.getBoolean(KEY_STORY_REPLIES, true)
        )
    }
    fun saveSettings(context: Context, settings: FynxSettings) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_NOTIFICATIONS, settings.notifications).putBoolean(KEY_PRIVATE_PROFILE, settings.privateProfile).putBoolean(KEY_READ_RECEIPTS, settings.readReceipts).putBoolean(KEY_STORY_REPLIES, settings.storyReplies).apply() }
    fun loadVisibility(context: Context, key: String, default: String = DEFAULT_VISIBILITY): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, default) ?: default
    fun saveVisibility(context: Context, key: String, value: String) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, value).apply() }
    fun loadAccent(context: Context): FynxAccent { val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ACCENT, FynxAccent.Blue.name); return runCatching { FynxAccent.valueOf(stored ?: FynxAccent.Blue.name) }.getOrDefault(FynxAccent.Blue) }
    fun saveAccent(context: Context, accent: FynxAccent) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ACCENT, accent.name).apply() }
    fun loadProfilePhoto(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PROFILE_PHOTO, null)
    fun saveProfilePhoto(context: Context, uri: String?) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply { if (uri.isNullOrBlank()) remove(KEY_PROFILE_PHOTO) else putString(KEY_PROFILE_PHOTO, uri) }.apply() }
    fun loadAppearance(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_APPEARANCE, "System") ?: "System"
    fun saveAppearance(context: Context, value: String) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_APPEARANCE, value).apply() }
    fun loadLanguage(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, "Device default") ?: "Device default"
    fun saveLanguage(context: Context, value: String) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LANGUAGE, value).apply() }
    fun loadAsset(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ASSET, null)

    fun loadNightMode(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NIGHT_MODE, "Follow system") ?: "Follow system"
    fun saveNightMode(context: Context, value: String) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_NIGHT_MODE, value).apply() }
    fun loadNightModeStart(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NIGHT_MODE_START, "22:00") ?: "22:00"
    fun saveNightModeStart(context: Context, value: String) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_NIGHT_MODE_START, value).apply() }
    fun loadNightModeEnd(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NIGHT_MODE_END, "07:00") ?: "07:00"
    fun saveNightModeEnd(context: Context, value: String) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_NIGHT_MODE_END, value).apply() }
    fun loadChatWallpaper(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CHAT_WALLPAPER, "FYNX Default") ?: "FYNX Default"
    fun saveChatWallpaper(context: Context, value: String) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_CHAT_WALLPAPER, value).apply() }
    fun loadChatListView(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CHAT_LIST_VIEW, "Comfortable") ?: "Comfortable"
    fun saveChatListView(context: Context, value: String) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_CHAT_LIST_VIEW, value).apply() }
    fun loadStickerAnimation(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_STICKER_ANIMATION, true)
    fun saveStickerAnimation(context: Context, value: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_STICKER_ANIMATION, value).apply() }
    fun loadEmojiSize(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_EMOJI_SIZE, "Normal") ?: "Normal"
    fun saveEmojiSize(context: Context, value: String) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_EMOJI_SIZE, value).apply() }

    fun isChatPinned(context: Context, username: String): Boolean = chatState(context, username).getBoolean("pinned", false)
    fun isChatMuted(context: Context, username: String): Boolean = chatState(context, username).getBoolean("muted", false)
    fun isChatArchived(context: Context, username: String): Boolean = chatState(context, username).getBoolean("archived", false)
    fun setChatPinned(context: Context, username: String, value: Boolean) { saveChatState(context, username, "pinned", value) }
    fun setChatMuted(context: Context, username: String, value: Boolean) { saveChatState(context, username, "muted", value) }
    fun setChatArchived(context: Context, username: String, value: Boolean) { saveChatState(context, username, "archived", value) }
    private fun chatState(context: Context, username: String) = context.getSharedPreferences("${KEY_CHAT_LIST_STATE}_${username.removePrefix("@").trim().lowercase()}", Context.MODE_PRIVATE)
    private fun saveChatState(context: Context, username: String, key: String, value: Boolean) { chatState(context, username).edit().putBoolean(key, value).apply() }

    /** Clear identity/privacy/media state before another account can enter this process. */
    fun clearAccountSessionData(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_USERNAME)
            .remove(KEY_BIO)
            .remove(KEY_DESCRIPTION)
            .remove(KEY_NOTIFICATIONS)
            .remove(KEY_PRIVATE_PROFILE)
            .remove(KEY_READ_RECEIPTS)
            .remove(KEY_STORY_REPLIES)
            .remove(KEY_PROFILE_PHOTO)
            .remove(KEY_ASSET)
            .remove(KEY_NIGHT_MODE)
            .remove(KEY_NIGHT_MODE_START)
            .remove(KEY_NIGHT_MODE_END)
            .remove(KEY_CHAT_WALLPAPER)
            .remove(KEY_CHAT_LIST_VIEW)
            .remove(KEY_STICKER_ANIMATION)
            .remove(KEY_EMOJI_SIZE)
            .apply()
    }

    /** Persist the selected customization image inside FYNX so the picker URI cannot expire. */
    fun saveAsset(context: Context, uri: String?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (uri.isNullOrBlank()) { prefs.edit().remove(KEY_ASSET).apply(); return }
        val source = runCatching { Uri.parse(uri) }.getOrNull() ?: return
        val target = File(context.filesDir, "fynx_customization.jpg")
        val copied = runCatching {
            context.contentResolver.openInputStream(source)?.use { input -> target.outputStream().use { output -> input.copyTo(output) } } ?: return@runCatching false
            true
        }.getOrDefault(false)
        if (copied) prefs.edit().putString(KEY_ASSET, Uri.fromFile(target).toString()).apply()
    }
}