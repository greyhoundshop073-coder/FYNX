package com.fynx.app.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real Android PCM output queue with silence so CI never emits audible test audio. */
@RunWith(AndroidJUnit4::class)
class FynxAiPcmAudioPlayerInstrumentationTest {
    @Test
    fun pauseAndResumeContinueTheSamePcmQueueAndPlaybackHead() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        // Android 15+ grants audio focus only to an eligible foreground app (or a
        // foreground service). Launch the app's debug-only visual host so this test
        // exercises the same foreground playback condition as a user tapping Speak.
        context.startActivity(
            Intent().setClassName(context.packageName, "com.fynx.app.ui.FynxAiVisualAuditActivity")
                .putExtra("screen", "voice")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        device.waitForIdle()
        SystemClock.sleep(250L)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val player = FynxAiPcmAudioPlayer(audioManager)
        try {
            val prepared = player.prepare()
            assertTrue("Android PCM playback should initialize: ${prepared.exceptionOrNull()?.message}", prepared.isSuccess)

            // One second of 24 kHz mono PCM16 silence: no audible output during device CI.
            val silentPcm = ByteArray(24_000 * 2)
            val encoded = Base64.encodeToString(silentPcm, Base64.NO_WRAP)
            assertTrue("The PCM chunk should be accepted by the player.", player.enqueueBase64Delta(encoded))
            assertEquals("The duration should be derived from queued PCM frames.", 1_000L, player.durationMillis())

            player.play()
            val firstPlaybackDeadline = SystemClock.uptimeMillis() + 2_000L
            while (player.elapsedMillis() < 160L && SystemClock.uptimeMillis() < firstPlaybackDeadline) {
                SystemClock.sleep(20L)
            }
            val pausedPosition = player.elapsedMillis()
            assertTrue("The audio playback head should advance before pausing (elapsed=$pausedPosition).", pausedPosition >= 100L)

            player.pause()
            val frozenPosition = player.elapsedMillis()
            SystemClock.sleep(180L)
            val afterPause = player.elapsedMillis()
            assertTrue(
                "Pausing must hold the exact PCM playback head (before=$frozenPosition, after=$afterPause).",
                afterPause <= frozenPosition + 40L
            )

            player.play()
            player.markStreamCompleted()
            val completionDeadline = SystemClock.uptimeMillis() + 3_000L
            while (!player.isPlaybackComplete() && SystemClock.uptimeMillis() < completionDeadline) {
                SystemClock.sleep(25L)
            }
            assertTrue("The same queued PCM should finish after resuming.", player.isPlaybackComplete())
            assertTrue("Playback must continue beyond the paused position.", player.elapsedMillis() > pausedPosition + 100L)
            assertEquals(1_000L, player.durationMillis())
        } finally {
            player.close()
            device.pressHome()
        }
    }
}
