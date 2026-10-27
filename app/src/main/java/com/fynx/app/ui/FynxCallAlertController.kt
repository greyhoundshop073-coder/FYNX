package com.fynx.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.fynx.app.R

/**
 * Owns one incoming-call alert at a time. The alert is tied to the active
 * call session, uses the FYNX ringtone, and follows Android's ring/vibrate/silent mode.
 */
object FynxCallAlertController {
    private var activeCallId: String? = null
    private var ringtone: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var vibrationActive = false

    @Synchronized
    fun start(context: Context, callId: String) {
        if (callId.isBlank()) return
        val appContext = context.applicationContext
        val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (activeCallId == callId &&
            (runCatching { ringtone?.isPlaying == true }.getOrDefault(false) ||
                vibrationActive || audio.ringerMode == AudioManager.RINGER_MODE_SILENT)
        ) return

        stopLocked()
        activeCallId = callId
        when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> Unit
            AudioManager.RINGER_MODE_VIBRATE -> startVibrationLocked(appContext)
            else -> {
                if (audio.getStreamVolume(AudioManager.STREAM_RING) > 0) {
                    startRingtoneLocked(appContext)
                }
            }
        }
    }

    @Synchronized
    fun stop(callId: String? = null) {
        if (callId != null && activeCallId != callId) return
        stopLocked()
    }

    @Synchronized
    fun isAlerting(callId: String? = null): Boolean =
        activeCallId != null && (callId == null || activeCallId == callId)

    private fun startRingtoneLocked(context: Context) {
        val player = runCatching { MediaPlayer.create(context, R.raw.fynx_call_ring) }.getOrNull()
        if (player == null) {
            startVibrationLocked(context)
            return
        }

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val started = runCatching {
            player.setAudioAttributes(attributes)
            player.isLooping = true
            player.start()
            true
        }.getOrDefault(false)

        if (started) {
            ringtone = player
        } else {
            runCatching { player.release() }
            startVibrationLocked(context)
        }
    }

    private fun startVibrationLocked(context: Context) {
        val targetVibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        } ?: return
        if (!targetVibrator.hasVibrator()) return
        val started = runCatching {
            targetVibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0L, 650L, 450L), 0)
            )
            true
        }.getOrDefault(false)
        if (started) {
            vibrator = targetVibrator
            vibrationActive = true
        }
    }

    private fun stopLocked() {
        ringtone?.let { player ->
            runCatching { if (player.isPlaying) player.stop() }
            runCatching { player.release() }
        }
        ringtone = null
        vibrator?.let { runCatching { it.cancel() } }
        vibrator = null
        vibrationActive = false
        activeCallId = null
    }
}
