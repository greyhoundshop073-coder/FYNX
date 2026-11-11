package com.fynx.app.ui

import android.Manifest
import android.media.MediaRecorder
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CameraVideoAudioExtractionInstrumentationTest {
    @Test
    fun cameraCaptionAudioDecoderConvertsAmrNbToPcmWave() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        runCatching {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName, Manifest.permission.RECORD_AUDIO
            )
        }.onFailure { assumeNoException("The test runner could not grant microphone access", it) }

        val source = File(context.cacheDir, "camera-caption-amr-fixture.3gp")
        val wav = File(context.cacheDir, "camera-caption-amr-fixture.wav")
        source.delete()
        wav.delete()
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        var started = false
        try {
            try {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                recorder.setAudioSamplingRate(8_000)
                recorder.setAudioChannels(1)
                recorder.setOutputFile(source.absolutePath)
                recorder.prepare()
                recorder.start()
                started = true
                Thread.sleep(1_200L)
                recorder.stop()
                started = false
            } catch (failure: Throwable) {
                assumeNoException("This emulator does not expose a working AMR-NB microphone recorder", failure)
            } finally {
                runCatching { recorder.release() }
            }

            assumeTrue("AMR-NB test recording was not produced", source.isFile && source.length() > 512L)
            extractFynxVideoAudio(source, wav)
            assertTrue("Caption conversion did not write a WAV file", wav.isFile && wav.length() > 44L)
            val header = ByteArray(44)
            wav.inputStream().use { input ->
                assertEquals("WAV header must be complete", 44, input.read(header))
            }
            assertEquals("RIFF", String(header, 0, 4, Charsets.US_ASCII))
            assertEquals("WAVE", String(header, 8, 4, Charsets.US_ASCII))
            assertEquals("PCM format code", 1, readLittleEndian16(header, 20))
            assertTrue("WAV must have at least one channel", readLittleEndian16(header, 22) in 1..2)
            assertTrue("WAV must have a positive sample rate", readLittleEndian32(header, 24) > 0L)
            assertEquals("16-bit PCM is required by the decoder", 16, readLittleEndian16(header, 34))
            assertTrue("WAV must contain decoded audio samples", readLittleEndian32(header, 40) > 0L)
        } finally {
            if (started) runCatching { recorder.stop() }
            runCatching { recorder.release() }
            source.delete()
            wav.delete()
        }
    }

    private fun readLittleEndian16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun readLittleEndian32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xffL) or
            ((bytes[offset + 1].toLong() and 0xffL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xffL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xffL) shl 24)
}
