package com.fynx.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Offline UI certification for the actual shared private/Marketplace call surface. */
class FynxCallScreensComposeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun connectedVoiceCallShowsAndRunsTheCorrectControls() {
        var muteActions = 0
        var speakerActions = 0
        var endActions = 0
        compose.setContent {
            MaterialTheme {
                FynxActiveCallPanel(
                    name = "FYNX Test Seller",
                    session = FynxCallSession(
                        id = "call-voice-test-001",
                        callerUsername = "@fynx_test_buyer",
                        participantUsernames = listOf("@fynx_test_seller"),
                        type = FynxCallType.VOICE,
                        state = FynxCallState.CONNECTED
                    ),
                    onAnswer = {},
                    onRetry = {},
                    onToggleMicrophone = { muteActions++ },
                    onToggleCamera = {},
                    onSwitchCamera = {},
                    onToggleSpeaker = { speakerActions++ },
                    onEnd = { endActions++ }
                )
            }
        }

        compose.onNodeWithText("FYNX Test Seller").assertIsDisplayed()
        compose.onNodeWithText("Voice call").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mute").performClick()
        compose.onNodeWithContentDescription("Speaker").performClick()
        compose.onNodeWithContentDescription("End").performClick()
        compose.runOnIdle {
            assertEquals(1, muteActions)
            assertEquals(1, speakerActions)
            assertEquals(1, endActions)
        }
        saveScreenshot("calls-voice-active.png")
    }

    @Test
    fun connectedVideoCallKeepsCameraAndAudioControlsReachable() {
        var muteActions = 0
        var cameraActions = 0
        var flipActions = 0
        var speakerActions = 0
        var endActions = 0
        compose.setContent {
            MaterialTheme {
                FynxActiveCallPanel(
                    name = "FYNX Test Seller",
                    session = FynxCallSession(
                        id = "call-video-test-001",
                        callerUsername = "@fynx_test_buyer",
                        participantUsernames = listOf("@fynx_test_seller"),
                        type = FynxCallType.VIDEO,
                        state = FynxCallState.CONNECTED
                    ),
                    onAnswer = {},
                    onRetry = {},
                    onToggleMicrophone = { muteActions++ },
                    onToggleCamera = { cameraActions++ },
                    onSwitchCamera = { flipActions++ },
                    onToggleSpeaker = { speakerActions++ },
                    onEnd = { endActions++ }
                )
            }
        }

        compose.onNodeWithText("Video call").assertIsDisplayed()
        compose.onAllNodesWithText("Waiting for video…")[0].assertIsDisplayed()
        compose.onNodeWithContentDescription("Mute").performClick()
        compose.onNodeWithContentDescription("Camera").performClick()
        compose.onNodeWithContentDescription("Flip").performClick()
        compose.onNodeWithContentDescription("Speaker").performClick()
        compose.onNodeWithContentDescription("End").performClick()
        compose.runOnIdle {
            assertEquals(1, muteActions)
            assertEquals(1, cameraActions)
            assertEquals(1, flipActions)
            assertEquals(1, speakerActions)
            assertEquals(1, endActions)
        }
        saveScreenshot("calls-video-active.png")
    }

    @Test
    fun incomingVideoCallShowsAnswerAndDeclineActions() {
        var answerActions = 0
        var declineActions = 0
        compose.setContent {
            MaterialTheme {
                FynxActiveCallPanel(
                    name = "FYNX Test Seller",
                    session = FynxCallSession(
                        id = "call-video-incoming-001",
                        callerUsername = "@fynx_test_seller",
                        participantUsernames = listOf("@fynx_test_seller"),
                        type = FynxCallType.VIDEO,
                        state = FynxCallState.RINGING
                    ),
                    onAnswer = { answerActions++ },
                    onRetry = {},
                    onToggleMicrophone = {},
                    onToggleCamera = {},
                    onSwitchCamera = {},
                    onToggleSpeaker = {},
                    onEnd = { declineActions++ }
                )
            }
        }

        compose.onNodeWithText("Incoming video call").assertIsDisplayed()
        compose.onNodeWithText("Video call from FYNX Test Seller").assertIsDisplayed()
        compose.onNodeWithContentDescription("Answer").performClick()
        compose.onNodeWithContentDescription("Decline").performClick()
        compose.runOnIdle {
            assertEquals(1, answerActions)
            assertEquals(1, declineActions)
        }
        saveScreenshot("calls-video-incoming.png")
    }

    @Test
    fun incomingGroupVideoCallShowsGroupInvitationAndActions() {
        var dismissed = 0
        compose.setContent {
            MaterialTheme {
                FynxGroupCallPanel(
                    groupId = "group-call-ui-test",
                    groupName = "FYNX Test Group",
                    currentUsername = "fynx_test_member",
                    initialInvite = FynxRealtimeClient.Event.GroupCall(
                        groupId = "group-call-ui-test",
                        callId = "group-video-incoming-001",
                        callType = "video",
                        signalType = "group_invite",
                        creatorId = "seller-user-001",
                        fromUserId = "seller-user-001",
                        participants = listOf(
                            FynxRealtimeClient.Event.GroupCallParticipant("seller-user-001", "fynx_test_seller")
                        )
                    ),
                    onDismiss = { dismissed++ }
                )
            }
        }

        compose.onNodeWithText("FYNX Test Group").assertIsDisplayed()
        compose.onNodeWithText("Incoming group video call").assertIsDisplayed()
        compose.onNodeWithText("A member has started a group video call.").assertIsDisplayed()
        compose.onNodeWithText("Decline").assertIsDisplayed()
        compose.onNodeWithText("Join call").assertIsDisplayed()
        saveScreenshot("calls-group-video-incoming.png")
        compose.onNodeWithText("Decline").performClick()
        compose.runOnIdle { assertEquals(1, dismissed) }
    }

    private fun saveScreenshot(name: String) {
        // Screenshots are optional evidence; a capture failure must not mask UI assertions.
        runCatching {
            val externalFiles = requireNotNull(compose.activity.getExternalFilesDir(null))
            val directory = File(externalFiles, "marketplace-test-fixtures")
            check(directory.mkdirs() || directory.isDirectory)
            val file = File(directory, name)
            compose.waitForIdle()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            check(device.takeScreenshot(file))
            check(file.isFile && file.length() > 0L)
            android.util.Log.i("FYNX_CALL_SCREENSHOT", "Captured ${file.absolutePath}")
        }.onFailure { error ->
            android.util.Log.w("FynxCallScreensComposeTest", "Optional screenshot '$name' was not captured", error)
        }
    }
}
