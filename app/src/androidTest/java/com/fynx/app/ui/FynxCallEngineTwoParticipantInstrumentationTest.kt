package com.fynx.app.ui

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import java.util.concurrent.ConcurrentLinkedQueue
import androidx.test.uiautomator.UiDevice
import java.io.File

/**
 * End-to-end test of the real FYNX one-to-one WebRTC engine with two local
 * participants. The test replaces only the network signaling transport with
 * in-memory callbacks; SDP, ICE, audio capture, PeerConnections and remote-track
 * delivery are the production FynxWebRtcCallEngine implementation.
 *
 * This can run while Neon/Railway is unavailable. It does not certify server
 * authorization, Cloudflare TURN/SFU, push wake-up or real-network traversal.
 */
class FynxCallEngineTwoParticipantInstrumentationTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun buyerAndSellerCompleteARealVoiceCallThroughFynxMediaEngines() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.RECORD_AUDIO
        )
        check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            "The emulator must grant microphone permission for the FYNX call-engine test."
        }

        val buyerConnected = CompletableDeferred<Unit>()
        val sellerConnected = CompletableDeferred<Unit>()
        val buyerReceivedSellerAudio = CompletableDeferred<Unit>()
        val sellerReceivedBuyerAudio = CompletableDeferred<Unit>()
        val errors = ConcurrentLinkedQueue<String>()
        val mediaEvents = ConcurrentLinkedQueue<String>()
        lateinit var buyer: FynxWebRtcCallEngine
        lateinit var seller: FynxWebRtcCallEngine

        fun connected(target: CompletableDeferred<Unit>, state: PeerConnection.IceConnectionState) {
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> target.complete(Unit)
                PeerConnection.IceConnectionState.FAILED ->
                    target.completeExceptionally(AssertionError("FYNX local peer ICE connection failed"))
                else -> Unit
            }
        }

        buyer = FynxWebRtcCallEngine(
            context = context,
            iceServers = emptyList(),
            callbacks = FynxWebRtcCallEngine.CallCallbacks(
                onOffer = { seller.acceptOfferAndCreateAnswer(it) },
                onAnswer = { buyer.applyAnswer(it) },
                onIceCandidate = { seller.addRemoteIceCandidate(it) },
                onRemoteAudioTrack = { track ->
                    track.setEnabled(true)
                    mediaEvents.add("buyer received seller audio track")
                    buyerReceivedSellerAudio.complete(Unit)
                },
                onConnectionState = {
                    mediaEvents.add("buyer ICE=$it")
                    connected(buyerConnected, it)
                },
                onError = { errors.add("buyer: $it") }
            )
        )
        seller = FynxWebRtcCallEngine(
            context = context,
            iceServers = emptyList(),
            callbacks = FynxWebRtcCallEngine.CallCallbacks(
                onOffer = { buyer.acceptOfferAndCreateAnswer(it) },
                onAnswer = { seller.applyAnswer(it) },
                onIceCandidate = { buyer.addRemoteIceCandidate(it) },
                onRemoteAudioTrack = { track ->
                    track.setEnabled(true)
                    mediaEvents.add("seller received buyer audio track")
                    sellerReceivedBuyerAudio.complete(Unit)
                },
                onConnectionState = {
                    mediaEvents.add("seller ICE=$it")
                    connected(sellerConnected, it)
                },
                onError = { errors.add("seller: $it") }
            )
        )

        val callId = "call-local-buyer-seller-01"
        try {
            buyer.connect(
                FynxCallSession(
                    id = callId,
                    callerUsername = "@fynx_test_buyer",
                    participantUsernames = listOf("@fynx_test_buyer", "@fynx_test_seller"),
                    type = FynxCallType.VOICE,
                    state = FynxCallState.CONNECTING
                )
            )
            seller.connect(
                FynxCallSession(
                    id = callId,
                    callerUsername = "@fynx_test_seller",
                    participantUsernames = listOf("@fynx_test_buyer", "@fynx_test_seller"),
                    type = FynxCallType.VOICE,
                    state = FynxCallState.CONNECTING
                )
            )

            // Same offer/answer and ICE callback route used by the app's realtime layer,
            // but in memory so no hosted backend is needed for this transport test.
            buyer.createOffer()

            try {
                withTimeout(45_000) {
                    buyerConnected.await()
                    sellerConnected.await()
                }
            } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                throw AssertionError(
                    "FYNX call ICE did not connect within 45s. errors=${errors.joinToString()}, " +
                        "events=${mediaEvents.joinToString()}",
                    error
                )
            }
            try {
                withTimeout(15_000) {
                    buyerReceivedSellerAudio.await()
                    sellerReceivedBuyerAudio.await()
                }
            } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                throw AssertionError(
                    "FYNX ICE connected but remote audio tracks did not arrive. " +
                        "errors=${errors.joinToString()}, events=${mediaEvents.joinToString()}",
                    error
                )
            }

            buyer.setMicrophoneEnabled(false)
            buyer.setMicrophoneEnabled(true)
            buyer.setSpeakerEnabled(true)
            seller.setMicrophoneEnabled(false)
            seller.setMicrophoneEnabled(true)
            seller.setSpeakerEnabled(true)

            assertTrue("Both participants must have exchanged real remote audio tracks", buyerReceivedSellerAudio.isCompleted && sellerReceivedBuyerAudio.isCompleted)
            assertTrue("The local call engine must not report media/signaling errors: ${errors.joinToString()}", errors.isEmpty())

            // Render the real FYNX connected-call surface while both production
            // media engines are still connected, then save the screenshot as evidence.
            compose.setContent {
                MaterialTheme {
                    FynxActiveCallPanel(
                        name = "FYNX Test Seller",
                        session = FynxCallSession(
                            id = callId,
                            callerUsername = "@fynx_test_buyer",
                            participantUsernames = listOf("@fynx_test_buyer", "@fynx_test_seller"),
                            type = FynxCallType.VOICE,
                            state = FynxCallState.CONNECTED,
                            speakerEnabled = true
                        ),
                        onAnswer = {},
                        onRetry = {},
                        onToggleMicrophone = { buyer.setMicrophoneEnabled(false) },
                        onToggleCamera = {},
                        onSwitchCamera = {},
                        onToggleSpeaker = { buyer.setSpeakerEnabled(true) },
                        onEnd = {}
                    )
                }
            }
            compose.onNodeWithText("FYNX Test Seller").assertIsDisplayed()
            compose.onNodeWithText("Voice call").assertIsDisplayed()
            compose.waitForIdle()
            val screenshotDirectory = File(requireNotNull(compose.activity.getExternalFilesDir(null)), "marketplace-test-fixtures")
            check(screenshotDirectory.mkdirs() || screenshotDirectory.isDirectory)
            val screenshot = File(screenshotDirectory, "calls-two-participant-live-engine.png")
            check(UiDevice.getInstance(instrumentation).takeScreenshot(screenshot))
            check(screenshot.isFile && screenshot.length() > 0L) { "Live FYNX call screenshot was not saved." }
            android.util.Log.i("FYNX_CALL_SCREENSHOT", "Captured live two-participant FYNX call: ${screenshot.absolutePath}")

            android.util.Log.i(
                "FYNX_CALL_ENGINE_E2E",
                "PASS: buyer and seller connected through two production FYNX WebRTC engines; SDP/ICE callbacks completed; each side received the other participant's audio track; mute/speaker controls executed"
            )
        } finally {
            buyer.disconnect()
            seller.disconnect()
        }
        Unit
    }
}
