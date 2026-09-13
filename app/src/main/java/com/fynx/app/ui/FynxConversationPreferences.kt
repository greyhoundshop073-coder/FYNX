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

    fun chatNotifications(context: Context, username: String): Boolean =
        chat(context, username).getBoolean("notifications_${chatKey(username)}", true)

    fun chatMessagePreviews(context: Context, username: String): Boolean =
        chat(context, username).getBoolean("previews_${chatKey(username)}", true)

    fun chatSounds(context: Context, username: String): Boolean =
        chat(context, username).getBoolean("sounds_${chatKey(username)}", true)

    fun chatVibration(context: Context, username: String): Boolean =
        chat(context, username).getBoolean("vibration_${chatKey(username)}", true)

    fun chatReadReceipts(context: Context, username: String): Boolean =
        chat(context, username).getBoolean("read_${chatKey(username)}", true)

    fun chatAutoDownload(context: Context, username: String): Boolean =
        chat(context, username).getBoolean("autodownload_${chatKey(username)}", true)

    fun chatSaveGallery(context: Context, username: String): Boolean =
        chat(context, username).getBoolean("gallery_${chatKey(username)}", false)

    fun chatLinkPreviews(context: Context, username: String): Boolean =
        chat(context, username).getBoolean("linkpreviews_${chatKey(username)}", true)

    fun chatAnimations(context: Context, username: String): Boolean =
        chat(context, username).getBoolean("animations_${chatKey(username)}", true)

    fun chatLastSeen(context: Context, username: String): String =
        chat(context, username).getString("lastseen_${chatKey(username)}", "Everybody") ?: "Everybody"

    fun chatWallpaper(context: Context, username: String): String =
        chat(context, username).getString("wallpaper_${chatKey(username)}", "FYNX Default") ?: "FYNX Default"

    fun chatTextSize(context: Context, username: String): String =
        chat(context, username).getString("textsize_${chatKey(username)}", "Medium") ?: "Medium"

    fun groupNotifications(context: Context, groupId: String): Boolean =
        group(context, groupId).getBoolean("notifications", true)

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
