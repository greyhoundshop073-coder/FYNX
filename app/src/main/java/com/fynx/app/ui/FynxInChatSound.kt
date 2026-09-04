package com.fynx.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/** A short original FYNX in-chat chime for newly received messages. */
object FynxInChatSound {
    fun play(context: Context) {
        Thread {
            val sampleRate = 44_100
            val notes = listOf(880.0 to 105, 1174.66 to 135, 1318.51 to 190)
            val gap = 18
            val totalMs = notes.sumOf { it.second } + gap * (notes.size - 1)
            val samples = (sampleRate * totalMs / 1000.0).toInt()
            val pcm = ShortArray(samples)
            var cursor = 0
            notes.forEachIndexed { index, (frequency, durationMs) ->
                val count = (sampleRate * durationMs / 1000.0).toInt()
                repeat(count) { i ->
                    val t = i.toDouble() / sampleRate
                    val attack = (i / (sampleRate * 0.012)).coerceAtMost(1.0)
                    val release = ((count - i) / (sampleRate * 0.035)).coerceAtMost(1.0)
                    val envelope = (attack * release).coerceIn(0.0, 1.0)
                    val harmonic = 0.18 * sin(2.0 * PI * frequency * 2.0 * t)
                    pcm[cursor + i] = ((sin(2.0 * PI * frequency * t) + harmonic) * 0.22 * envelope * Short.MAX_VALUE).toInt().toShort()
                }
                cursor += count
                if (index < notes.lastIndex) cursor += (sampleRate * gap / 1000.0).toInt()
            }
            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                    .also { it.write(pcm, 0, pcm.size); it.play() }
            }.getOrNull()
            if (track != null) {
                try { Thread.sleep(totalMs.toLong() + 30L) } finally { track.stop(); track.release() }
            }
        }.start()
    }
}
