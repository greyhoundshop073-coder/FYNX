package com.fynx.app.ui

/**
 * Runtime-neutral media/signaling contract for the FYNX call UI.
 * Platform/WebRTC transport can implement this without changing call state models.
 */
interface FynxCallMediaEngine {
    fun connect(session: FynxCallSession)
    fun setMicrophoneEnabled(enabled: Boolean)
    fun setCameraEnabled(enabled: Boolean)
    fun switchCamera()
    fun setSpeakerEnabled(enabled: Boolean)
    fun disconnect()
}

data class FynxCallPermissions(
    val microphoneGranted: Boolean,
    val cameraGranted: Boolean
) {
    fun canStartVoiceCall(): Boolean = microphoneGranted
    fun canStartVideoCall(): Boolean = microphoneGranted && cameraGranted
}

object FynxCallMediaRequirements {
    fun requiredPermissions(type: FynxCallType): Set<String> = when (type) {
        FynxCallType.VOICE -> setOf("android.permission.RECORD_AUDIO")
        FynxCallType.VIDEO -> setOf("android.permission.RECORD_AUDIO", "android.permission.CAMERA")
    }
}
