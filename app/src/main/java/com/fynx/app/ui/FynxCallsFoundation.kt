package com.fynx.app.ui

/** Call foundation for FYNX voice and video calling. Networking/WebRTC transport plugs into these contracts. */
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
    val speakerEnabled: Boolean = false,
    val usingFrontCamera: Boolean = true
)

object FynxCallsFoundation {
    private const val MIN_CALL_ID_LENGTH = 6
    private const val MAX_CALL_ID_LENGTH = 80

    fun start(session: FynxCallSession): FynxCallSession =
        transition(session, FynxCallState.CONNECTING)

    fun answer(session: FynxCallSession): FynxCallSession =
        transition(session, FynxCallState.CONNECTED)

    fun ring(session: FynxCallSession): FynxCallSession =
        transition(session, FynxCallState.RINGING)

    fun end(session: FynxCallSession): FynxCallSession =
        transition(session, FynxCallState.ENDED)

    /**
     * Keeps terminal calls terminal and rejects invalid backwards transitions.
     * This is intentionally pure so the realtime/WebRTC layer remains authoritative
     * while the UI cannot accidentally revive an ended call.
     */
    fun transition(session: FynxCallSession, next: FynxCallState): FynxCallSession {
        if (!isValidSessionId(session.id) || session.state == FynxCallState.ENDED) return session
        val allowed = when (session.state) {
            FynxCallState.IDLE -> setOf(FynxCallState.RINGING, FynxCallState.CONNECTING, FynxCallState.ENDED)
            FynxCallState.RINGING -> setOf(FynxCallState.CONNECTING, FynxCallState.CONNECTED, FynxCallState.ENDED)
            FynxCallState.CONNECTING -> setOf(FynxCallState.CONNECTED, FynxCallState.ENDED)
            FynxCallState.CONNECTED -> setOf(FynxCallState.ENDED)
            FynxCallState.ENDED -> emptySet()
        }
        return if (next in allowed || next == session.state) session.copy(state = next) else session
    }

    fun toggleMicrophone(session: FynxCallSession): FynxCallSession =
        if (isActive(session)) session.copy(microphoneEnabled = !session.microphoneEnabled) else session

    fun toggleCamera(session: FynxCallSession): FynxCallSession =
        if (isActive(session) && session.type == FynxCallType.VIDEO) session.copy(cameraEnabled = !session.cameraEnabled) else session

    fun toggleSpeaker(session: FynxCallSession): FynxCallSession =
        if (isActive(session)) session.copy(speakerEnabled = !session.speakerEnabled) else session

    fun switchCamera(session: FynxCallSession): FynxCallSession =
        if (isActive(session) && session.type == FynxCallType.VIDEO) session.copy(usingFrontCamera = !session.usingFrontCamera) else session

    fun canUseCamera(session: FynxCallSession): Boolean = session.type == FynxCallType.VIDEO && isActive(session)

    fun canRetry(session: FynxCallSession): Boolean = session.state == FynxCallState.ENDED || session.state == FynxCallState.CONNECTING

    fun isActive(session: FynxCallSession): Boolean =
        session.state == FynxCallState.RINGING ||
            session.state == FynxCallState.CONNECTING ||
            session.state == FynxCallState.CONNECTED

    fun isValidSessionId(id: String): Boolean =
        id.length in MIN_CALL_ID_LENGTH..MAX_CALL_ID_LENGTH &&
            id.startsWith("call-") &&
            id.drop(5).all { it.isLetterOrDigit() || it == '-' || it == '_' }
}
