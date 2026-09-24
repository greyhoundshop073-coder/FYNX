package com.fynx.app.ui

import android.content.Context

/** Shared preference access for conversation UI behavior. Keeps per-chat and per-group state consistent. */
object FynxConversationPreferences {
    private const val CHAT_PREFS = "fynx_chat_settings"

    private fun accountKey(context: Context): String =
        FynxAuthStore.accountStorageKey(context)?.trim()?.lowercase()?.map { c -> if (c.isLetterOrDigit()) c else '_' }?.joinToString("")?.take(80)?.ifBlank { "account" } ?: "signed_out"

    private fun chatKey(username: String): String =
        username.trim().removePrefix("@").lowercase().ifBlank { "unknown" }

    private fun groupKey(groupId: String): String =
        groupId.trim().ifBlank { "unknown" }

    fun chat(context: Context, username: String) =
        context.getSharedPreferences("${CHAT_PREFS}_${accountKey(context)}", Context.MODE_PRIVATE)

    fun group(context: Context, groupId: String) =
        context.getSharedPreferences("fynx_group_settings_${accountKey(context)}_${groupKey(groupId)}", Context.MODE_PRIVATE)

    private fun chatBoolean(context: Context, username: String, suffix: String, default: Boolean): Boolean {
        val prefs = chat(context, username)
        val normalizedKey = "${suffix}_${chatKey(username)}"
        if (prefs.contains(normalizedKey)) return prefs.getBoolean(normalizedKey, default)
        val legacyKey = "${suffix}_${username.trim()}"
        if (!prefs.contains(legacyKey)) return default
        val value = prefs.getBoolean(legacyKey, default)
        prefs.edit().putBoolean(normalizedKey, value).remove(legacyKey).apply()
        return value
    }

    private fun chatString(context: Context, username: String, suffix: String, default: String): String {
        val prefs = chat(context, username)
        val normalizedKey = "${suffix}_${chatKey(username)}"
        if (prefs.contains(normalizedKey)) return prefs.getString(normalizedKey, default) ?: default
        val legacyKey = "${suffix}_${username.trim()}"
        if (!prefs.contains(legacyKey)) return default
        val value = prefs.getString(legacyKey, default) ?: default
        prefs.edit().putString(normalizedKey, value).remove(legacyKey).apply()
        return value
    }

    fun chatNotifications(context: Context, username: String): Boolean =
        chatBoolean(context, username, "notifications", true)

    fun setChatNotifications(context: Context, username: String, enabled: Boolean) {
        chat(context, username).edit().putBoolean("notifications_${chatKey(username)}", enabled).apply()
    }

    fun chatMessagePreviews(context: Context, username: String): Boolean =
        chatBoolean(context, username, "previews", true)

    fun chatSounds(context: Context, username: String): Boolean =
        chatBoolean(context, username, "sounds", true)

    fun chatVibration(context: Context, username: String): Boolean =
        chatBoolean(context, username, "vibration", true)

    fun chatReadReceipts(context: Context, username: String): Boolean =
        chatBoolean(context, username, "read", true)

    fun chatAutoDownload(context: Context, username: String): Boolean =
        chatBoolean(context, username, "autodownload", true)

    fun chatSaveGallery(context: Context, username: String): Boolean =
        chatBoolean(context, username, "gallery", false)

    fun chatLinkPreviews(context: Context, username: String): Boolean =
        chatBoolean(context, username, "linkpreviews", true)

    fun chatAnimations(context: Context, username: String): Boolean =
        chatBoolean(context, username, "animations", true)

    fun chatLastSeen(context: Context, username: String): String =
        chatString(context, username, "lastseen", "Everybody")

    fun chatWallpaper(context: Context, username: String): String =
        chatString(context, username, "wallpaper", "FYNX Default")

    fun setChatWallpaper(context: Context, username: String, value: String) {
        chat(context, username).edit().putString("wallpaper_${chatKey(username)}", value).apply()
    }

    fun chatTextSize(context: Context, username: String): String =
        chatString(context, username, "textsize", "Medium")

    fun groupNotifications(context: Context, groupId: String): Boolean =
        group(context, groupId).getBoolean("notifications", true)

    fun setGroupNotifications(context: Context, groupId: String, enabled: Boolean) {
        group(context, groupId).edit().putBoolean("notifications", enabled).apply()
    }

    fun groupMuted(context: Context, groupId: String): Boolean =
        group(context, groupId).getBoolean("mute", false)

    fun groupMembersCanSendMessages(context: Context, groupId: String): Boolean =
        group(context, groupId).getBoolean("send_messages", true)

    fun groupMembersCanSendMedia(context: Context, groupId: String): Boolean =
        group(context, groupId).getBoolean("send_media", true)

    fun groupMembersCanAddPeople(context: Context, groupId: String): Boolean =
        group(context, groupId).getBoolean("add_members", true)

    fun groupInviteLinksEnabled(context: Context, groupId: String): Boolean =
        group(context, groupId).getBoolean("invite_links", true)

    fun groupSaveMedia(context: Context, groupId: String): Boolean =
        group(context, groupId).getBoolean("save_media", false)

    fun groupChatHistoryEnabled(context: Context, groupId: String): Boolean =
        group(context, groupId).getBoolean("chat_history", true)

    fun groupAppearance(context: Context, groupId: String): String =
        group(context, groupId).getString("appearance", "FYNX Default") ?: "FYNX Default"
}
