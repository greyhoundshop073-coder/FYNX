package com.fynx.app.ui

import android.content.Context
import android.media.AudioManager

class FynxCallAudioRouter(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousMode = AudioManager.MODE_NORMAL
    private var previousSpeaker = false
    private var started = false

    fun start(speaker: Boolean) {
        if (!started) {
            previousMode = audioManager.mode
            previousSpeaker = audioManager.isSpeakerphoneOn
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            started = true
        }
        audioManager.isSpeakerphoneOn = speaker
    }

    fun setSpeakerEnabled(enabled: Boolean) { if (started) audioManager.isSpeakerphoneOn = enabled }

    fun stop() {
        if (!started) return
        audioManager.isSpeakerphoneOn = previousSpeaker
        audioManager.mode = previousMode
        started = false
    }
}
