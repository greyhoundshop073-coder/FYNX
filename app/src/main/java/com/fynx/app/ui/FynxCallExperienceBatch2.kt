package com.fynx.app.ui

/** Incoming-call and in-call experience state layered on top of FynxCallSession. */
enum class FynxCallAction { ACCEPT, DECLINE, END, TOGGLE_MIC, TOGGLE_CAMERA, SWITCH_CAMERA, TOGGLE_SPEAKER, RECONNECT }

data class FynxCallExperience(
    val session: FynxCallSession,
    val speakerEnabled: Boolean = false,
    val durationSeconds: Long = 0L,
    val reconnecting: Boolean = false,
    val lastError: String? = null
)

object FynxCallExperienceBatch2 {
    fun accept(call: FynxCallExperience): FynxCallExperience =
        call.copy(session = FynxCallsFoundation.answer(call.session), reconnecting = false, lastError = null)

    fun decline(call: FynxCallExperience): FynxCallExperience =
        call.copy(session = FynxCallsFoundation.end(call.session), reconnecting = false)

    fun end(call: FynxCallExperience): FynxCallExperience =
        call.copy(session = FynxCallsFoundation.end(call.session), reconnecting = false)

    fun toggleMicrophone(call: FynxCallExperience): FynxCallExperience =
        call.copy(session = FynxCallsFoundation.toggleMicrophone(call.session), lastError = null)

    fun toggleCamera(call: FynxCallExperience): FynxCallExperience =
        if (call.session.type == FynxCallType.VIDEO) call.copy(session = FynxCallsFoundation.toggleCamera(call.session), lastError = null) else call

    fun switchCamera(call: FynxCallExperience): FynxCallExperience =
        if (call.session.type == FynxCallType.VIDEO) call.copy(session = FynxCallsFoundation.switchCamera(call.session), lastError = null) else call

    fun toggleSpeaker(call: FynxCallExperience): FynxCallExperience =
        call.copy(speakerEnabled = !call.speakerEnabled, lastError = null)

    fun updateDuration(call: FynxCallExperience, seconds: Long): FynxCallExperience =
        call.copy(durationSeconds = seconds.coerceAtLeast(0L))

    fun reconnect(call: FynxCallExperience): FynxCallExperience =
        if (FynxCallsFoundation.canRetry(call.session)) call.copy(reconnecting = true, session = call.session.copy(state = FynxCallState.CONNECTING), lastError = null) else call

    fun reconnectFailed(call: FynxCallExperience, reason: String): FynxCallExperience =
        call.copy(reconnecting = false, lastError = reason.trim().take(240).ifBlank { "Call connection failed. Please try again." })

    fun clearError(call: FynxCallExperience): FynxCallExperience = call.copy(lastError = null)

    fun formatDuration(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        val secs = safe % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%02d:%02d".format(minutes, secs)
    }

    fun statusLabel(call: FynxCallExperience): String = when {
        call.lastError != null -> call.lastError
        call.reconnecting -> "Reconnecting…"
        call.session.state == FynxCallState.RINGING -> "Incoming ${if (call.session.type == FynxCallType.VIDEO) "video" else "voice"} call"
        call.session.state == FynxCallState.CONNECTING -> "Connecting…"
        call.session.state == FynxCallState.CONNECTED -> formatDuration(call.durationSeconds)
        call.session.state == FynxCallState.ENDED -> "Call ended"
        else -> "Ready"
    }
}
