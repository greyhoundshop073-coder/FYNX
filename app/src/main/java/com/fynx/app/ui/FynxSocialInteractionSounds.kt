package com.fynx.app.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

/** Short, non-intrusive local feedback sounds for successful social actions. */
object FynxSocialInteractionSounds {
    private fun play(context: Context, tone: Int) {
        runCatching {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 55).apply {
                startTone(tone, 90)
                Thread { Thread.sleep(140); release() }.start()
            }
        }
    }

    fun postPublished(context: Context) = play(context, ToneGenerator.TONE_PROP_ACK)
    fun liked(context: Context) = play(context, ToneGenerator.TONE_PROP_ACK)
    fun reaction(context: Context) = play(context, ToneGenerator.TONE_PROP_BEEP)
}