package com.fynx.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

class FynxCallAudioRouter(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousMode = AudioManager.MODE_NORMAL
    private var previousSpeaker = false
    private var started = false
    private var focusRequest: AudioFocusRequest? = null

    fun start(speaker: Boolean) {
        if (!started) {
            previousMode = audioManager.mode
            previousSpeaker = audioManager.isSpeakerphoneOn
            requestAudioFocus()
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            started = true
        }
        setSpeakerEnabled(speaker)
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        if (!started) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val device = if (enabled) {
                audioManager.availableCommunicationDevices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            } else {
                audioManager.availableCommunicationDevices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                    ?: audioManager.availableCommunicationDevices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                    ?: audioManager.availableCommunicationDevices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET }
            }
            if (device != null) audioManager.setCommunicationDevice(device) else audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION") audioManager.isSpeakerphoneOn = enabled
        }
    }

    fun stop() {
        if (!started) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION") audioManager.isSpeakerphoneOn = previousSpeaker
        }
        audioManager.mode = previousMode
        abandonAudioFocus()
        started = false
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION") audioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION") audioManager.abandonAudioFocus(null)
        }
    }
}
