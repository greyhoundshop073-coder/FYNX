package com.fynx.app.ui

import android.Manifest

/**
 * Keep camera and microphone grants scoped to the capture action that needs them.
 * Opening the camera requires CAMERA; the microphone is requested only when a
 * user starts recording a video with audio.
 */
internal object FynxCameraPermissionPolicy {
    fun entryPermissions(): Array<String> = arrayOf(Manifest.permission.CAMERA)

    fun shouldRequestMicrophoneForVideo(hasAudioPermission: Boolean): Boolean = !hasAudioPermission
}
