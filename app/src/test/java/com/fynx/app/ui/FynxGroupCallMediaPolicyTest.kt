package com.fynx.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxGroupCallMediaPolicyTest {
    @Test
    fun mutedCameraDoesNotRemoveVideoTrackFromFuturePeerConnections() {
        // Camera mute changes VideoTrack.enabled, not whether the video track exists.
        assertTrue(FynxGroupCallMediaPolicy.shouldAttachVideoTrack(isVideoCall = true, hasLocalVideoTrack = true))
    }

    @Test
    fun voiceCallsNeverAttachVideoTracks() {
        assertFalse(FynxGroupCallMediaPolicy.shouldAttachVideoTrack(isVideoCall = false, hasLocalVideoTrack = true))
    }

    @Test
    fun videoCallWithoutAvailableCameraTrackDoesNotAttachVideo() {
        assertFalse(FynxGroupCallMediaPolicy.shouldAttachVideoTrack(isVideoCall = true, hasLocalVideoTrack = false))
    }
}
