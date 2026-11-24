package com.fynx.app.ui

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxCameraPermissionPolicyTest {
    @Test
    fun openingCameraRequestsOnlyCameraPermission() {
        assertEquals(
            listOf(Manifest.permission.CAMERA),
            FynxCameraPermissionPolicy.entryPermissions().toList()
        )
    }

    @Test
    fun microphoneIsRequestedOnlyWhenVideoRecordingNeedsIt() {
        assertTrue(FynxCameraPermissionPolicy.shouldRequestMicrophoneForVideo(hasAudioPermission = false))
        assertFalse(FynxCameraPermissionPolicy.shouldRequestMicrophoneForVideo(hasAudioPermission = true))
    }
}
