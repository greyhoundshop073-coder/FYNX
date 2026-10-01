package com.fynx.app.ui

import org.webrtc.CameraVideoCapturer

/** Bridges camera hardware failures into the existing call error channel. */
class FynxCallCameraEvents(
    private val onError: (String) -> Unit
) : CameraVideoCapturer.CameraEventsHandler {
    override fun onCameraError(errorDescription: String?) {
        onError(errorDescription?.takeIf { it.isNotBlank() } ?: "Camera error")
    }

    override fun onCameraDisconnected() {
        onError("Camera disconnected")
    }

    override fun onCameraFreezed(errorDescription: String?) {
        onError(errorDescription?.takeIf { it.isNotBlank() } ?: "Camera preview froze")
    }

    override fun onCameraOpening(cameraName: String?) = Unit
    override fun onFirstFrameAvailable() = Unit
    override fun onCameraClosed() = Unit
}
