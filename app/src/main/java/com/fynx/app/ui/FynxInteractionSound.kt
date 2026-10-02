package com.fynx.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.AudioManager.RINGER_MODE_SILENT
import android.media.AudioManager.RINGER_MODE_VIBRATE
import kotlin.math.PI
import kotlin.math.sin

/**
 * Single interaction-sound controller for FYNX.
 *
 * The controller owns all in-app interaction tones, category switches, device
 * audio-state checks and rapid-event throttling. FynxInChatSound remains as the
 * compatibility facade for existing callers.
 */
object FynxInteractionSound {
    enum class Category { CHAT, GROUP, SOCIAL }
    enum class Event(val category: Category, val throttleMs: Long, val notes: List<Pair<Double, Int>>, val volume: Double = 0.18) {
        MESSAGE_SENT(Category.CHAT, 120L, listOf(880.0 to 70, 1174.66 to 90)),
        MESSAGE_RECEIVED(Category.CHAT, 180L, listOf(659.25 to 80, 987.77 to 110)),
        MEDIA_SENT(Category.CHAT, 140L, listOf(784.0 to 70, 1046.5 to 100)),
        REPLY(Category.CHAT, 180L, listOf(698.46 to 70, 1046.5 to 100)),
        REACTION(Category.CHAT, 220L, listOf(987.77 to 60, 1318.51 to 80)),
        TYPING(Category.CHAT, 900L, listOf(880.0 to 45), 0.10),

        GROUP_MESSAGE_SENT(Category.GROUP, 120L, listOf(880.0 to 70, 1174.66 to 90)),
        GROUP_MESSAGE_RECEIVED(Category.GROUP, 180L, listOf(659.25 to 80, 987.77 to 110)),
        GROUP_MEDIA_SENT(Category.GROUP, 140L, listOf(784.0 to 70, 1046.5 to 100)),
        GROUP_REPLY(Category.GROUP, 180L, listOf(698.46 to 70, 1046.5 to 100)),
        GROUP_REACTION(Category.GROUP, 220L, listOf(987.77 to 60, 1318.51 to 80)),
        GROUP_TYPING(Category.GROUP, 900L, listOf(880.0 to 45), 0.10),

        SOCIAL_REACTION(Category.SOCIAL, 220L, listOf(987.77 to 60, 1318.51 to 80)),
        SOCIAL_COMMENT(Category.SOCIAL, 180L, listOf(698.46 to 70, 1046.5 to 100)),
        SOCIAL_INTERACTION(Category.SOCIAL, 220L, listOf(784.0 to 70, 1174.66 to 90)),
        SOCIAL_POST_SUCCESS(Category.SOCIAL, 220L, listOf(784.0 to 65, 1174.66 to 85)),
        SOCIAL_LIKE(Category.SOCIAL, 220L, listOf(987.77 to 55, 1318.51 to 75))
    }

    private const val PREFS = "fynx_interaction_sound_preferences"
    private const val MASTER = "master_enabled"
    private const val CHAT = "chat_enabled"
    private const val GROUP = "group_enabled"
    private const val SOCIAL = "social_enabled"
    private val lastPlayed = mutableMapOf<Event, Long>()
    private val recentEventIds = mutableMapOf<String, Long>()

    @Synchronized
    fun play(context: Context, event: Event, eventId: String? = null) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(MASTER, true)) return
        val categoryEnabled = when (event.category) {
            Category.CHAT -> prefs.getBoolean(CHAT, true)
            Category.GROUP -> prefs.getBoolean(GROUP, true)
            Category.SOCIAL -> prefs.getBoolean(SOCIAL, true)
        }
        if (!categoryEnabled) return

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (audio.ringerMode == RINGER_MODE_SILENT) return
        if (audio.getStreamVolume(AudioManager.STREAM_NOTIFICATION) <= 0) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val notifications = context.getSystemService(android.app.NotificationManager::class.java)
            if (notifications?.currentInterruptionFilter == android.app.NotificationManager.INTERRUPTION_FILTER_NONE) return
        }

        val now = System.currentTimeMillis()
        val previous = lastPlayed[event] ?: 0L
        if (now - previous < event.throttleMs) return
        if (eventId != null) {
            val old = recentEventIds[eventId]
            if (old != null && now - old < 30_000L) return
            recentEventIds[eventId] = now
            recentEventIds.entries.removeIf { now - it.value > 30_000L }
        }
        lastPlayed[event] = now

        Thread {
            val sampleRate = 44_100
            val gap = 14
            val totalMs = event.notes.sumOf { it.second } + gap * (event.notes.size - 1)
            val pcm = ShortArray((sampleRate * totalMs / 1000.0).toInt())
            var cursor = 0
            event.notes.forEachIndexed { index, (frequency, durationMs) ->
                val count = (sampleRate * durationMs / 1000.0).toInt()
                repeat(count) { i ->
                    val t = i.toDouble() / sampleRate
                    val attack = (i / (sampleRate * 0.010)).coerceAtMost(1.0)
                    val release = ((count - i) / (sampleRate * 0.035)).coerceAtMost(1.0)
                    val envelope = (attack * release).coerceIn(0.0, 1.0)
                    val harmonic = 0.12 * sin(2.0 * PI * frequency * 2.0 * t)
                    pcm[cursor + i] = ((sin(2.0 * PI * frequency * t) + harmonic) * event.volume * envelope * Short.MAX_VALUE).toInt().toShort()
                }
                cursor += count
                if (index < event.notes.lastIndex) cursor += (sampleRate * gap / 1000.0).toInt()
            }

            // Re-check device state immediately before playback so a silent-mode
            // change made while the short synthesis was running is respected.
            val currentAudio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return@Thread
            if (currentAudio.ringerMode == RINGER_MODE_SILENT || currentAudio.getStreamVolume(AudioManager.STREAM_NOTIFICATION) <= 0) return@Thread

            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build().also { it.write(pcm, 0, pcm.size); it.play() }
            }.getOrNull() ?: return@Thread

            try {
                Thread.sleep(totalMs.toLong() + 30L)
            } finally {
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }.start()
    }

    fun setMasterEnabled(context: Context, enabled: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(MASTER, enabled).apply()

    fun setCategoryEnabled(context: Context, category: Category, enabled: Boolean) {
        val key = when (category) {
            Category.CHAT -> CHAT
            Category.GROUP -> GROUP
            Category.SOCIAL -> SOCIAL
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, enabled).apply()
    }

    fun isMasterEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(MASTER, true)

    fun isCategoryEnabled(context: Context, category: Category): Boolean {
        val key = when (category) {
            Category.CHAT -> CHAT
            Category.GROUP -> GROUP
            Category.SOCIAL -> SOCIAL
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, true)
    }
}
