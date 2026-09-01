package com.fynx.app.ui

/** Call foundation for FYNX voice and video calling. Networking/WebRTC transport can plug into these contracts. */
enum class FynxCallType { VOICE, VIDEO }
enum class FynxCallState { IDLE, RINGING, CONNECTING, CONNECTED, ENDED }

data class FynxCallSession(
    val id: String,
    val callerUsername: String,
    val participantUsernames: List<String>,
    val type: FynxCallType,
    val state: FynxCallState = FynxCallState.IDLE,
    val microphoneEnabled: Boolean = true,
    val cameraEnabled: Boolean = true,
    val usingFrontCamera: Boolean = true
)

object FynxCallsFoundation {
    fun start(session: FynxCallSession): FynxCallSession = session.copy(state = FynxCallState.CONNECTING)
    fun answer(session: FynxCallSession): FynxCallSession = session.copy(state = FynxCallState.CONNECTED)
    fun end(session: FynxCallSession): FynxCallSession = session.copy(state = FynxCallState.ENDED)
    fun toggleMicrophone(session: FynxCallSession): FynxCallSession = session.copy(microphoneEnabled = !session.microphoneEnabled)
    fun toggleCamera(session: FynxCallSession): FynxCallSession = session.copy(cameraEnabled = !session.cameraEnabled)
    fun switchCamera(session: FynxCallSession): FynxCallSession = session.copy(usingFrontCamera = !session.usingFrontCamera)
    fun canUseCamera(session: FynxCallSession): Boolean = session.type == FynxCallType.VIDEO
}
