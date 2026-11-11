package com.fynx.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * One app-scoped player for official catalogue music.
 *
 * Voice notes and recorded speech intentionally keep their own audio path.
 * All methods are called from the main thread (Compose UI); MediaPlayer prepares
 * asynchronously so feed scrolling never blocks on decoder preparation.
 */
internal object FynxCatalogueMusicPlayback {
    data class Snapshot(
        val trackKey: String? = null,
        val isPreparing: Boolean = false,
        val isPlaying: Boolean = false,
        val error: String? = null
    )

    var snapshot by mutableStateOf(Snapshot())
        private set

    private var player: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var resumeAfterTransientFocus = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun toggle(context: Context, trackKey: String, file: File) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Catalogue playback must be controlled on the main thread." }
        if (snapshot.trackKey == trackKey && player != null) {
            val current = player ?: return
            if (snapshot.isPreparing) return
            runCatching {
                if (current.isPlaying) {
                    current.pause()
                    abandonAudioFocus(clear = false)
                    snapshot = snapshot.copy(isPlaying = false, error = null)
                } else {
                    if (current.currentPosition >= current.duration) current.seekTo(0)
                    if (!requestAudioFocus()) {
                        snapshot = snapshot.copy(isPlaying = false, error = "Audio focus is unavailable.")
                        return
                    }
                    current.start()
                    snapshot = snapshot.copy(isPlaying = true, error = null)
                }
            }.onFailure {
                snapshot = snapshot.copy(isPlaying = false, isPreparing = false, error = it.message)
            }
            return
        }

        releaseCurrent(resetSnapshot = false)
        snapshot = Snapshot(trackKey = trackKey, isPreparing = true)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val manager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ change ->
                mainHandler.post {
                    val active = player ?: return@post
                    when (change) {
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            resumeAfterTransientFocus = false
                            runCatching { if (active.isPlaying) active.pause() }
                            snapshot = snapshot.copy(isPlaying = false)
                            abandonAudioFocus(clear = false)
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> runCatching {
                            resumeAfterTransientFocus = active.isPlaying
                            if (active.isPlaying) active.pause()
                            snapshot = snapshot.copy(isPlaying = false)
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> runCatching { active.setVolume(0.2f, 0.2f) }
                        AudioManager.AUDIOFOCUS_GAIN -> runCatching {
                            active.setVolume(1f, 1f)
                            if (resumeAfterTransientFocus && player === active) {
                                active.start()
                                snapshot = snapshot.copy(isPlaying = true)
                            }
                            resumeAfterTransientFocus = false
                        }
                    }
                }
            }, mainHandler)
            .build()
        if (manager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            snapshot = Snapshot(trackKey = trackKey, error = "Audio focus is unavailable.")
            return
        }
        audioManager = manager
        focusRequest = request
        resumeAfterTransientFocus = false

        val created = MediaPlayer()
        player = created
        try {
            created.setAudioAttributes(attributes)
            created.setDataSource(file.absolutePath)
            created.setOnPreparedListener { prepared ->
                if (player !== prepared) return@setOnPreparedListener
                runCatching {
                    prepared.start()
                    snapshot = Snapshot(trackKey = trackKey, isPlaying = true)
                }.onFailure {
                    snapshot = Snapshot(trackKey = trackKey, error = it.message)
                    releaseCurrent(resetSnapshot = false)
                }
            }
            created.setOnCompletionListener { completed ->
                if (player !== completed) return@setOnCompletionListener
                runCatching { completed.seekTo(0) }
                abandonAudioFocus()
                snapshot = Snapshot(trackKey = trackKey)
            }
            created.setOnErrorListener { failed, _, _ ->
                if (player === failed) {
                    releaseCurrent(resetSnapshot = false)
                    snapshot = Snapshot(trackKey = trackKey, error = "Audio playback failed.")
                }
                true
            }
            created.prepareAsync()
        } catch (error: Exception) {
            releaseCurrent(resetSnapshot = false)
            snapshot = Snapshot(trackKey = trackKey, error = error.message ?: "Audio playback failed.")
        }
    }

    fun pause() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Catalogue playback must be controlled on the main thread." }
        runCatching {
            player?.takeIf { it.isPlaying }?.pause()
            resumeAfterTransientFocus = false
            snapshot = snapshot.copy(isPlaying = false)
            abandonAudioFocus(clear = false)
        }
    }

    private fun requestAudioFocus(): Boolean {
        val manager = audioManager ?: return false
        val request = focusRequest ?: return false
        return manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus(clear: Boolean = true) {
        val manager = audioManager
        val request = focusRequest
        if (manager != null && request != null) runCatching { manager.abandonAudioFocusRequest(request) }
        if (clear) {
            audioManager = null
            focusRequest = null
        }
        resumeAfterTransientFocus = false
    }

    private fun releaseCurrent(resetSnapshot: Boolean) {
        runCatching { player?.release() }
        player = null
        abandonAudioFocus()
        if (resetSnapshot) snapshot = Snapshot()
    }
}
