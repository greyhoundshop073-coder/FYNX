package com.fynx.app.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

internal fun shouldPlayInChatAlert(notificationsEnabled: Boolean, muted: Boolean): Boolean =
    notificationsEnabled && !muted

/** Compatibility facade retained for existing private-chat callers. */
object FynxInChatSound {
    fun play(context: Context, username: String? = null) {
        if (username == null) {
            FynxInteractionSound.play(context, FynxInteractionSound.Event.MESSAGE_RECEIVED)
            return
        }

        if (!shouldPlayInChatAlert(
                notificationsEnabled = FynxConversationPreferences.chatNotifications(context, username),
                muted = FynxPreferencesStore.isChatMuted(context, username)
            )
        ) return

        if (FynxConversationPreferences.chatSounds(context, username)) {
            FynxInteractionSound.play(context, FynxInteractionSound.Event.MESSAGE_RECEIVED)
        }

        if (FynxConversationPreferences.chatVibration(context, username)) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            35L,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(35L)
                }
            }
        }
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
