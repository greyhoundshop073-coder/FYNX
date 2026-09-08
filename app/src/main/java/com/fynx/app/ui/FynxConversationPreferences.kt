package com.fynx.app.ui

import android.content.Context

/** Shared preference access for conversation UI behavior. Keeps per-chat and per-group state consistent. */
object FynxConversationPreferences {
    private const val CHAT_PREFS = "fynx_chat_settings"

    fun chat(context: Context, username: String) =
        context.getSharedPreferences(CHAT_PREFS, Context.MODE_PRIVATE)

    fun group(context: Context, groupId: String) =
        context.getSharedPreferences("fynx_group_settings_$groupId", Context.MODE_PRIVATE)

    fun chatNotifications(context: Context, username: String): Boolean =
        chat(context, username).getBoolean("notifications_$username", true)

    fun chatMessagePreviews(context: Context, username: String): Boolean =
        chat(context, username).getBoolean("previews_$username", true)

    fun chatSounds(context: Context, username: String): Boolean =
        chat(context, username).getBoolean("sounds_$username", true)

    fun chatVibration(context: Context, username: String): Boolean =
        chat(context, username).getBoolean("vibration_$username", true)

    fun chatReadReceipts(context: Context, username: String): Boolean =
        chat(context, username).getBoolean("read_$username", true)

    fun chatAutoDownload(context: Context, username: String): Boolean =
        chat(context, username).getBoolean("autodownload_$username", true)

    fun chatSaveGallery(context: Context, username: String): Boolean =
        chat(context, username).getBoolean("gallery_$username", false)

    fun chatLinkPreviews(context: Context, username: String): Boolean =
        chat(context, username).getBoolean("linkpreviews_$username", true)

    fun chatAnimations(context: Context, username: String): Boolean =
        chat(context, username).getBoolean("animations_$username", true)

    fun chatLastSeen(context: Context, username: String): String =
        chat(context, username).getString("lastseen_$username", "Everybody") ?: "Everybody"

    fun chatWallpaper(context: Context, username: String): String =
        chat(context, username).getString("wallpaper_$username", "FYNX Default") ?: "FYNX Default"

    fun chatTextSize(context: Context, username: String): String =
        chat(context, username).getString("textsize_$username", "Medium") ?: "Medium"

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