package com.fynx.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/** Short original FYNX chime shared by private and group chat surfaces. */
object FynxInChatSound {
    private const val PREFS = "fynx_chat_sound_preferences"
    private const val ENABLED = "enabled"
    @Volatile private var lastPlayedAt = 0L

    fun play(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(ENABLED, true)) return
        val now = System.currentTimeMillis()
        if (now - lastPlayedAt < 120L) return
        lastPlayedAt = now
        Thread {
            val sampleRate = 44_100
            val notes = listOf(880.0 to 90, 1174.66 to 120, 1318.51 to 150)
            val gap = 16
            val totalMs = notes.sumOf { it.second } + gap * (notes.size - 1)
            val pcm = ShortArray((sampleRate * totalMs / 1000.0).toInt())
            var cursor = 0
            notes.forEachIndexed { index, (frequency, durationMs) ->
                val count = (sampleRate * durationMs / 1000.0).toInt()
                repeat(count) { i ->
                    val t = i.toDouble() / sampleRate
                    val attack = (i / (sampleRate * 0.010)).coerceAtMost(1.0)
                    val release = ((count - i) / (sampleRate * 0.035)).coerceAtMost(1.0)
                    val envelope = (attack * release).coerceIn(0.0, 1.0)
                    val harmonic = 0.14 * sin(2.0 * PI * frequency * 2.0 * t)
                    pcm[cursor + i] = ((sin(2.0 * PI * frequency * t) + harmonic) * 0.20 * envelope * Short.MAX_VALUE).toInt().toShort()
                }
                cursor += count
                if (index < notes.lastIndex) cursor += (sampleRate * gap / 1000.0).toInt()
            }
            if ((context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.getStreamVolume(AudioManager.STREAM_NOTIFICATION) == 0) return@Thread
            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build().also { it.write(pcm, 0, pcm.size); it.play() }
            }.getOrNull() ?: return@Thread
            try { Thread.sleep(totalMs.toLong() + 30L) } finally { runCatching { track.stop() }; runCatching { track.release() } }
        }.start()
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, enabled).apply()
    }

    fun isEnabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, true)
}
