package com.fynx.app.ui

import android.content.Context

/** Compatibility facade retained for existing private-chat callers. */
object FynxInChatSound {
    fun play(context: Context) {
        FynxInteractionSound.play(context, FynxInteractionSound.Event.MESSAGE_RECEIVED)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        // Preserve the historical API semantics: this switch controls chat
        // sounds without unexpectedly changing Group or Social preferences.
        context.getSharedPreferences("fynx_chat_sound_preferences", Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", enabled).apply()
        FynxInteractionSound.setCategoryEnabled(context, FynxInteractionSound.Category.CHAT, enabled)
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences("fynx_chat_sound_preferences", Context.MODE_PRIVATE)
            .getBoolean("enabled", FynxInteractionSound.isCategoryEnabled(context, FynxInteractionSound.Category.CHAT))
}
