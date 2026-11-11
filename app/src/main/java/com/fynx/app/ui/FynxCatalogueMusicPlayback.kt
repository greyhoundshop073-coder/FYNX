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
        val ownerKey: String? = null,
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
    private var requestedVolume = 1f
    private var focusDuckMultiplier = 1f
    private var pendingResumePlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun toggle(context: Context, trackKey: String, file: File, ownerKey: String? = null, initialPositionMs: Long = 0L, loopPlayback: Boolean = false, initialVolume: Float = 1f) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Catalogue playback must be controlled on the main thread." }
        if (snapshot.trackKey == trackKey && snapshot.ownerKey == ownerKey && player != null) {
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
        requestedVolume = initialVolume.coerceIn(0f, 1f)
        focusDuckMultiplier = 1f
        snapshot = Snapshot(trackKey = trackKey, ownerKey = ownerKey, isPreparing = true)
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
                            pendingResumePlayer?.let { runCatching { it.setOnSeekCompleteListener(null) } }
                            pendingResumePlayer = null
                            resumeAfterTransientFocus = false
                            if (snapshot.isPreparing) {
                                releaseCurrent(resetSnapshot = true)
                                return@post
                            }
                            runCatching { if (active.isPlaying) active.pause() }
                            snapshot = snapshot.copy(isPlaying = false)
                            abandonAudioFocus(clear = false)
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> runCatching {
                            pendingResumePlayer?.let { runCatching { it.setOnSeekCompleteListener(null) } }
                            pendingResumePlayer = null
                            // A pending initial seek must never start after focus was lost.
                            if (snapshot.isPreparing) {
                                releaseCurrent(resetSnapshot = true)
                                return@post
                            }
                            resumeAfterTransientFocus = active.isPlaying
                            if (active.isPlaying) active.pause()
                            snapshot = snapshot.copy(isPlaying = false)
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> runCatching {
                            focusDuckMultiplier = 0.2f
                            applyRequestedVolume(active)
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> runCatching {
                            focusDuckMultiplier = 1f
                            applyRequestedVolume(active)
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
                if (player !== prepared || snapshot.ownerKey != ownerKey || !snapshot.isPreparing) return@setOnPreparedListener
                runCatching {
                    prepared.isLooping = loopPlayback
                    val duration = prepared.duration.coerceAtLeast(0)
                    val target = if (duration > 0) (initialPositionMs.coerceAtLeast(0L) % duration.toLong()).toInt() else 0
                    if (target > 0) {
                        prepared.setOnSeekCompleteListener { seeked ->
                            startPreparedPlayback(seeked, trackKey, ownerKey)
                        }
                        prepared.seekTo(target)
                    } else {
                        startPreparedPlayback(prepared, trackKey, ownerKey)
                    }
                }.onFailure {
                    snapshot = Snapshot(trackKey = trackKey, ownerKey = ownerKey, error = it.message)
                    releaseCurrent(resetSnapshot = false)
                }
            }
            created.setOnCompletionListener { completed ->
                if (player !== completed) return@setOnCompletionListener
                runCatching { completed.seekTo(0) }
                abandonAudioFocus(clear = false)
                snapshot = Snapshot(trackKey = trackKey, ownerKey = ownerKey)
            }
            created.setOnErrorListener { failed, _, _ ->
                if (player === failed) {
                    releaseCurrent(resetSnapshot = false)
                    snapshot = Snapshot(trackKey = trackKey, ownerKey = ownerKey, error = "Audio playback failed.")
                }
                true
            }
            created.prepareAsync()
        } catch (error: Exception) {
            releaseCurrent(resetSnapshot = false)
            snapshot = Snapshot(trackKey = trackKey, ownerKey = ownerKey, error = error.message ?: "Audio playback failed.")
        }
    }

    /** Starts a post soundtrack at the paired video's current position. */
    fun playAt(context: Context, trackKey: String, file: File, ownerKey: String, positionMs: Long, initialVolume: Float) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Catalogue playback must be controlled on the main thread." }
        requestedVolume = initialVolume.coerceIn(0f, 1f)
        if (snapshot.trackKey == trackKey && snapshot.ownerKey == ownerKey && player != null) {
            player?.let(::applyRequestedVolume)
            if (snapshot.isPreparing) return
            val current = player ?: return
            runCatching {
                current.isLooping = true
                if (!current.isPlaying) {
                    // Video position updates arrive repeatedly while a seek is pending.
                    // Keep the in-flight seek; the next clock tick corrects any residual drift.
                    if (pendingResumePlayer === current) return
                    if (!requestAudioFocus()) { snapshot = snapshot.copy(isPlaying = false, error = "Audio focus is unavailable."); return }
                    val duration = current.duration.coerceAtLeast(0)
                    val target = if (duration > 0) (positionMs.coerceAtLeast(0L) % duration.toLong()).toInt() else 0
                    if (target > 0) {
                        pendingResumePlayer = current
                        current.setOnSeekCompleteListener { seeked ->
                            if (player !== seeked || pendingResumePlayer !== seeked ||
                                snapshot.trackKey != trackKey || snapshot.ownerKey != ownerKey ||
                                snapshot.isPreparing || snapshot.isPlaying
                            ) return@setOnSeekCompleteListener
                            pendingResumePlayer = null
                            seeked.setOnSeekCompleteListener(null)
                            runCatching {
                                applyRequestedVolume(seeked)
                                if (requestAudioFocus()) {
                                    seeked.start()
                                    snapshot = snapshot.copy(isPlaying = true, error = null)
                                } else {
                                    snapshot = snapshot.copy(isPlaying = false, error = "Audio focus is unavailable.")
                                }
                            }.onFailure { snapshot = snapshot.copy(isPlaying = false, error = it.message) }
                        }
                        current.seekTo(target)
                    } else {
                        applyRequestedVolume(current)
                        current.start()
                        snapshot = snapshot.copy(isPlaying = true, error = null)
                    }
                } else syncTo(ownerKey, positionMs)
            }.onFailure { snapshot = snapshot.copy(isPlaying = false, error = it.message) }
            return
        }
        // Prepare the new player at the video's current clock position. The prepared
        // callback seeks before starting, so the soundtrack never audibly begins at 0.
        toggle(context, trackKey, file, ownerKey, initialPositionMs = positionMs, loopPlayback = true, initialVolume = initialVolume)
    }

    /** Keeps the soundtrack aligned to the video clock and corrects audible drift. */
    fun syncTo(ownerKey: String, positionMs: Long) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Catalogue playback must be controlled on the main thread." }
        if (snapshot.ownerKey != ownerKey || snapshot.isPreparing || !snapshot.isPlaying) return
        val current = player ?: return
        runCatching {
            val duration = current.duration.coerceAtLeast(0)
            if (duration == 0) return@runCatching
            val target = (positionMs.coerceAtLeast(0L) % duration.toLong()).toInt()
            if (kotlin.math.abs(current.currentPosition - target) > 350) current.seekTo(target)
        }
    }

    fun setVolume(ownerKey: String, volume: Float) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Catalogue playback must be controlled on the main thread." }
        if (snapshot.ownerKey != ownerKey) return
        requestedVolume = volume.coerceIn(0f, 1f)
        runCatching { player?.let(::applyRequestedVolume) }
    }

    fun pause() {
        pauseCurrent()
    }

    /** Pause only the soundtrack owned by the feed post that just left the active slot. */
    fun pause(ownerKey: String) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Catalogue playback must be controlled on the main thread." }
        if (snapshot.ownerKey != ownerKey) return
        pauseCurrent()
    }

    private fun pauseCurrent() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Catalogue playback must be controlled on the main thread." }
        // A MediaPlayer that is still preparing can start later from its prepared callback.
        // Releasing it here prevents a soundtrack from starting after its post has left focus.
        pendingResumePlayer?.let { runCatching { it.setOnSeekCompleteListener(null) } }
        pendingResumePlayer = null
        if (snapshot.isPreparing) {
            releaseCurrent(resetSnapshot = true)
            return
        }
        runCatching {
            player?.takeIf { it.isPlaying }?.pause()
            resumeAfterTransientFocus = false
            snapshot = snapshot.copy(isPlaying = false)
            abandonAudioFocus(clear = false)
        }
    }

    private fun startPreparedPlayback(prepared: MediaPlayer, trackKey: String, ownerKey: String?) {
        if (player !== prepared || snapshot.trackKey != trackKey || snapshot.ownerKey != ownerKey || !snapshot.isPreparing) return
        prepared.setOnSeekCompleteListener(null)
        applyRequestedVolume(prepared)
        prepared.start()
        snapshot = Snapshot(trackKey = trackKey, ownerKey = ownerKey, isPlaying = true)
    }

    private fun applyRequestedVolume(active: MediaPlayer) {
        val effective = (requestedVolume * focusDuckMultiplier).coerceIn(0f, 1f)
        active.setVolume(effective, effective)
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
        pendingResumePlayer?.let { runCatching { it.setOnSeekCompleteListener(null) } }
        pendingResumePlayer = null
        runCatching { player?.release() }
        player = null
        abandonAudioFocus()
        if (resetSnapshot) snapshot = Snapshot()
    }
}
