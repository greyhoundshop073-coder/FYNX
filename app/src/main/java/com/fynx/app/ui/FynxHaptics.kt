package com.fynx.app.ui

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * One policy point for in-app haptics.
 *
 * Ordinary confirmations use View.performHapticFeedback so Android's touch-feedback
 * preference and device-specific haptic implementation are respected. Incoming
 * messages use a short vibration only when both global and per-chat preferences allow it.
 */
object FynxHaptics {
    private const val PREFS = "fynx_feedback_preferences"
    private const val KEY_ENABLED = "in_app_haptics_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun perform(view: View, constant: Int = HapticFeedbackConstants.VIRTUAL_KEY) {
        if (!isEnabled(view.context)) return
        runCatching { view.performHapticFeedback(constant) }
    }

    fun incomingChatMessage(context: Context, chatUsername: String) {
        if (!isEnabled(context)) return
        if (!FynxConversationPreferences.chatNotifications(context, chatUsername)) return
        if (!FynxConversationPreferences.chatVibration(context, chatUsername)) return
        vibrateIncomingMessage(context)
    }

    /** Incoming group feedback obeys the shared haptics switch and the group's mute policy. */
    fun incomingGroupMessage(context: Context, groupId: String) {
        if (!isEnabled(context)) return
        if (!FynxConversationPreferences.groupNotifications(context, groupId)) return
        if (FynxConversationPreferences.groupMuted(context, groupId)) return
        vibrateIncomingMessage(context)
    }

    private fun vibrateIncomingMessage(context: Context) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audio?.ringerMode == AudioManager.RINGER_MODE_SILENT) return

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        } ?: return
        if (!vibrator.hasVibrator()) return

        runCatching {
            vibrator.vibrate(
                VibrationEffect.createOneShot(28L, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }
    }
}
