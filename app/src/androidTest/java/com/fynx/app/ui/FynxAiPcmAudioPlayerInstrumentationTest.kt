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
        val foregroundDeadline = SystemClock.uptimeMillis() + 5_000L
        while (device.currentPackageName != context.packageName && SystemClock.uptimeMillis() < foregroundDeadline) {
            SystemClock.sleep(50L)
        }
        assertEquals("FYNX must be the foreground app before requesting Android 15 audio focus.", context.packageName, device.currentPackageName)

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val player = FynxAiPcmAudioPlayer(audioManager)
        try {
            val prepared = player.prepare()
            assertTrue("Android PCM playback should initialize: ${prepared.exceptionOrNull()?.message}", prepared.isSuccess)

            // Four seconds of 24 kHz mono PCM16 silence: no audible output, with ample
            // queue remaining even if the emulator test thread is briefly delayed.
            val silentPcm = ByteArray(96_000 * 2)
            val encoded = Base64.encodeToString(silentPcm, Base64.NO_WRAP)
            assertTrue("The PCM chunk should be accepted by the player.", player.enqueueBase64Delta(encoded))
            assertEquals("The duration should be derived from queued PCM frames.", 4_000L, player.durationMillis())

            player.play()
            assertTrue(
                "Playback should acquire audio focus while FYNX is foregrounded: ${player.errorMessage()}",
                player.errorMessage() == null
            )
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
            val resumeDeadline = SystemClock.uptimeMillis() + 1_500L
            while (player.elapsedMillis() <= frozenPosition + 120L && SystemClock.uptimeMillis() < resumeDeadline) {
                SystemClock.sleep(25L)
            }
            val resumedPosition = player.elapsedMillis()
            assertTrue(
                "The same PCM playback head must advance after resume (paused=$frozenPosition, resumed=$resumedPosition, error=${player.errorMessage()}).",
                resumedPosition > frozenPosition + 100L
            )
            assertTrue("PCM output must remain healthy after resume: ${player.errorMessage()}", player.errorMessage() == null)

            player.markStreamCompleted()
            val completionDeadline = SystemClock.uptimeMillis() + 5_000L
            while (!player.isPlaybackComplete() && SystemClock.uptimeMillis() < completionDeadline) {
                SystemClock.sleep(25L)
            }
            assertTrue("The same queued PCM should finish after resuming.", player.isPlaybackComplete())
            assertEquals(4_000L, player.durationMillis())
        } finally {
            player.close()
            device.pressHome()
        }
    }
}
