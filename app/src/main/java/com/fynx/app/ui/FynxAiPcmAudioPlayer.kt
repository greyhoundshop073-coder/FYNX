package com.fynx.app.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Plays PCM chunks returned by the existing FYNX Realtime voice session.
 *
 * The Realtime model generates the voice. This class only provides a controllable
 * Android audio queue so pause/resume continues the same PCM stream exactly.
 */
internal class FynxAiPcmAudioPlayer(
    private val audioManager: AudioManager,
    private val sampleRate: Int = FynxAiReadAloud.OUTPUT_SAMPLE_RATE
) : AutoCloseable {
    companion object {
        private const val BYTES_PER_SAMPLE = 2
        private const val MAX_RESPONSE_AUDIO_BYTES = 48L * 1024L * 1024L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val chunks = Channel<ByteArray>(Channel.UNLIMITED)
    // PCM is not written until play() has acquired focus and started the AudioTrack.
    private val playbackStarted = CompletableDeferred<Unit>()
    private val closed = AtomicBoolean(false)
    private val streamCompleted = AtomicBoolean(false)
    private val writerCompleted = AtomicBoolean(false)
    private val enqueuedBytes = AtomicLong(0L)
    private val writtenBytes = AtomicLong(0L)
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var failureMessage: String? = null
    @Volatile private var userPaused = false
    @Volatile private var hasAudioFocus = false
    private var focusRequest: android.media.AudioFocusRequest? = null
    private var focusRequested = false
    private var audioAttributes: AudioAttributes? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                startTrackIfAllowed()
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = false
                runCatching { audioTrack?.pause() }
            }
        }
    }

    fun prepare(): Result<Unit> = runCatching {
        check(!closed.get()) { "FYNX audio player is already closed." }
        val minBufferBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        require(minBufferBytes > 0) { "FYNX could not prepare audio playback on this device." }
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        audioAttributes = attributes
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBufferBytes * 2, sampleRate * BYTES_PER_SAMPLE / 2))
            .build()
        check(track.state == AudioTrack.STATE_INITIALIZED) {
            track.release()
            "FYNX audio playback could not be initialized."
        }
        audioTrack = track
        // Audio focus is requested in play(), immediately before the first PCM is
        // emitted. Preparing a remote Realtime session can take several seconds,
        // and should not reserve the user's audio focus while it connects.
        scope.launch {
            try {
                playbackStarted.await()
                for (chunk in chunks) {
                    var offset = 0
                    var consecutiveZeroWrites = 0
                    while (offset < chunk.size && isActive && !closed.get()) {
                        val written = track.write(
                            chunk,
                            offset,
                            chunk.size - offset,
                            AudioTrack.WRITE_BLOCKING
                        )
                        if (written < 0) {
                            throw IllegalStateException("FYNX audio output stopped before the response finished (AudioTrack.write returned $written).")
                        }
                        if (written == 0) {
                            // A paused track can temporarily accept no frames. Keep the
                            // same queue and retry after resume instead of failing it.
                            if (userPaused || !hasAudioFocus) {
                                consecutiveZeroWrites = 0
                                delay(25L)
                                continue
                            }
                            consecutiveZeroWrites += 1
                            if (consecutiveZeroWrites >= 100) {
                                throw IllegalStateException("FYNX audio output is not accepting PCM frames.")
                            }
                            delay(10L)
                            continue
                        }
                        consecutiveZeroWrites = 0
                        offset += written
                        writtenBytes.addAndGet(written.toLong())
                    }
                }
            } catch (error: Throwable) {
                if (!closed.get()) {
                    failureMessage = error.message ?: "FYNX audio playback was interrupted."
                    streamCompleted.set(true)
                }
            } finally {
                writerCompleted.set(true)
            }
        }
    }

    /** Queue a base64 PCM16/24 kHz mono chunk without blocking the UI or WebRTC callback. */
    fun enqueueBase64Delta(encoded: String): Boolean {
        if (encoded.isBlank() || closed.get() || streamCompleted.get()) return false
        val bytes = runCatching { Base64.decode(encoded, Base64.NO_WRAP) }.getOrNull() ?: return false
        if (bytes.isEmpty() || bytes.size % BYTES_PER_SAMPLE != 0) return false
        val total = enqueuedBytes.addAndGet(bytes.size.toLong())
        if (total > MAX_RESPONSE_AUDIO_BYTES) {
            enqueuedBytes.addAndGet(-bytes.size.toLong())
            failureMessage = "This response is too long to play in one session. Stop and start it again."
            return false
        }
        val result = chunks.trySend(bytes)
        if (result.isFailure) {
            enqueuedBytes.addAndGet(-bytes.size.toLong())
            return false
        }
        return true
    }

    fun play() {
        if (closed.get() || failureMessage != null) return
        userPaused = false
        if (!focusRequested && !hasAudioFocus) {
            // On Android 15+ this must happen while FYNX is topmost or while an
            // eligible media foreground service is active. UI read-aloud is started
            // from a visible assistant screen, so acquire focus at the user action.
            audioAttributes?.let(::requestAudioFocus)
        }
        startTrackIfAllowed()
    }

    fun pause() {
        if (closed.get()) return
        userPaused = true
        runCatching { audioTrack?.pause() }
            .onFailure { failureMessage = it.message ?: "FYNX could not pause audio playback." }
    }

    /** No more PCM chunks are expected; queued audio still plays to completion. */
    fun markStreamCompleted() {
        if (streamCompleted.compareAndSet(false, true)) chunks.close()
    }

    fun elapsedMillis(): Long {
        val frames = audioTrack?.playbackHeadPosition?.toLong()?.and(0xffffffffL) ?: 0L
        return frames * 1000L / sampleRate
    }

    fun durationMillis(): Long =
        enqueuedBytes.get() * 1000L / (sampleRate * BYTES_PER_SAMPLE)

    fun isPlaybackComplete(): Boolean {
        if (failureMessage != null) return true
        if (!streamCompleted.get() || !writerCompleted.get()) return false
        val playedFrames = audioTrack?.playbackHeadPosition?.toLong()?.and(0xffffffffL) ?: 0L
        val writtenFrames = writtenBytes.get() / BYTES_PER_SAMPLE
        return playedFrames >= writtenFrames
    }

    fun errorMessage(): String? = failureMessage

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        chunks.close()
        scope.cancel()
        releaseAudioFocus()
        val track = audioTrack
        audioTrack = null
        runCatching {
            if (track?.state == AudioTrack.STATE_INITIALIZED) track.stop()
        }
        runCatching { track?.release() }
    }

    private fun startTrackIfAllowed() {
        val track = audioTrack ?: return
        if (!hasAudioFocus || userPaused || closed.get()) return
        runCatching { track.play() }
            .onSuccess { playbackStarted.complete(Unit) }
            .onFailure { failureMessage = it.message ?: "FYNX could not start audio playback." }
    }

    private fun requestAudioFocus(attributes: AudioAttributes) {
        val request = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusListener)
            .setAcceptsDelayedFocusGain(true)
            .build()
        focusRequest = request
        focusRequested = true
        val result = audioManager.requestAudioFocus(request)
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
            focusRequested = false
            failureMessage = "FYNX could not get audio focus. Pause other audio and try again."
        }
    }

    private fun releaseAudioFocus() {
        focusRequest?.let { request ->
            runCatching { audioManager.abandonAudioFocusRequest(request) }
        }
        focusRequest = null
        focusRequested = false
        hasAudioFocus = false
    }
}
