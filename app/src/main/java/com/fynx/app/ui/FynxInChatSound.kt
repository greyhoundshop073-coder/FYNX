package com.fynx.app.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/** Compatibility facade retained for existing private-chat callers. */
object FynxInChatSound {
    fun play(context: Context, username: String? = null) {
        if (username != null) {
            if (!FynxConversationPreferences.chatNotifications(context, username) ||
                FynxPreferencesStore.isChatMuted(context, username)) return
            if (FynxConversationPreferences.chatSounds(context, username)) {
                FynxInteractionSound.play(context, FynxInteractionSound.Event.MESSAGE_RECEIVED)
            }
            if (FynxConversationPreferences.chatVibration(context, username)) {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (vibrator?.hasVibrator() == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(35L, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(35L)
                    }
                }
            }
        } else {
            FynxInteractionSound.play(context, FynxInteractionSound.Event.MESSAGE_RECEIVED)
        }
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("fynx_chat_sound_preferences", Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", enabled).apply()
        FynxInteractionSound.setCategoryEnabled(context, FynxInteractionSound.Category.CHAT, enabled)
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences("fynx_chat_sound_preferences", Context.MODE_PRIVATE)
            .getBoolean("enabled", FynxInteractionSound.isCategoryEnabled(context, FynxInteractionSound.Category.CHAT))
}
