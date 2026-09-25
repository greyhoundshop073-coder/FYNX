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

    fun chatTextSizeSp(context: Context, username: String): Float = when (chatTextSize(context, username)) {
        "Small" -> 14f
        "Medium" -> 16f
        "Large" -> 18f
        "Extra Large" -> 20f
        else -> 16f
    }

    fun setChatTextSize(context: Context, username: String, value: String) {
        chat(context, username).edit().putString("textsize_${chatKey(username)}", value).apply()
    }

    fun chatBubbleTransparency(context: Context, username: String): Float =
        chatString(context, username, "bubblealpha", "0.90").toFloatOrNull()?.coerceIn(0.70f, 1f) ?: 0.90f

    fun setChatBubbleTransparency(context: Context, username: String, value: Float) {
        chat(context, username).edit().putString("bubblealpha_${chatKey(username)}", value.coerceIn(0.70f, 1f).toString()).apply()
    }

    fun chatBubbleLighting(context: Context, username: String): Float =
        chatString(context, username, "bubblelight", "0.58").toFloatOrNull()?.coerceIn(0.25f, 0.90f) ?: 0.58f

    fun setChatBubbleLighting(context: Context, username: String, value: Float) {
        chat(context, username).edit().putString("bubblelight_${chatKey(username)}", value.coerceIn(0.25f, 0.90f).toString()).apply()
    }

    fun chatBubbleGradient(context: Context, username: String): Float = chatString(context, username, "bubblegradient", "0.70").toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.70f
    fun setChatBubbleGradient(context: Context, username: String, value: Float) { chat(context, username).edit().putString("bubblegradient_${chatKey(username)}", value.coerceIn(0f, 1f).toString()).apply() }
    fun chatDoodleDensity(context: Context, username: String): Float = chatString(context, username, "doodledensity", "1.0").toFloatOrNull()?.coerceIn(0.5f, 1.5f) ?: 1f
    fun setChatDoodleDensity(context: Context, username: String, value: Float) { chat(context, username).edit().putString("doodledensity_${chatKey(username)}", value.coerceIn(0.5f, 1.5f).toString()).apply() }
    fun chatDoodleScale(context: Context, username: String): Float = chatString(context, username, "doodlescale", "1.0").toFloatOrNull()?.coerceIn(0.7f, 1.3f) ?: 1f
    fun setChatDoodleScale(context: Context, username: String, value: Float) { chat(context, username).edit().putString("doodlescale_${chatKey(username)}", value.coerceIn(0.7f, 1.3f).toString()).apply() }
    fun chatDoodleIntensity(context: Context, username: String): Float = chatString(context, username, "doodleintensity", "1.0").toFloatOrNull()?.coerceIn(0.4f, 1.6f) ?: 1f
    fun setChatDoodleIntensity(context: Context, username: String, value: Float) { chat(context, username).edit().putString("doodleintensity_${chatKey(username)}", value.coerceIn(0.4f, 1.6f).toString()).apply() }
    fun chatDoodleLight(context: Context, username: String): Float = chatString(context, username, "doodlelight", "1.0").toFloatOrNull()?.coerceIn(0f, 1.4f) ?: 1f
    fun setChatDoodleLight(context: Context, username: String, value: Float) { chat(context, username).edit().putString("doodlelight_${chatKey(username)}", value.coerceIn(0f, 1.4f).toString()).apply() }
    fun chatGradientRotation(context: Context, username: String): Float = chatString(context, username, "gradientrotation", "45").toFloatOrNull()?.coerceIn(0f, 360f) ?: 45f
    fun setChatGradientRotation(context: Context, username: String, value: Float) { chat(context, username).edit().putString("gradientrotation_${chatKey(username)}", value.coerceIn(0f, 360f).toString()).apply() }
    fun chatBackgroundGlow(context: Context, username: String): Float = chatString(context, username, "backgroundglow", "1.0").toFloatOrNull()?.coerceIn(0.6f, 1.4f) ?: 1f
    fun setChatBackgroundGlow(context: Context, username: String, value: Float) { chat(context, username).edit().putString("backgroundglow_${chatKey(username)}", value.coerceIn(0.6f, 1.4f).toString()).apply() }

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
