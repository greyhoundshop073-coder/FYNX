package com.fynx.app.ui

/** Small call-transport guards shared by realtime/call screens. */
object FynxCallTransportHardening {
    private const val MAX_CALL_ID_LENGTH = 80
    private const val MAX_SIGNAL_LENGTH = 16
    private val callIdPattern = Regex("^call[-_][a-z0-9_-]{1,70}$", RegexOption.IGNORE_CASE)
    private val validSignals = setOf("invite", "accept", "reject", "end", "offer", "answer", "ice", "unavailable", "busy")

    // Accept both the legacy call-123 form and the hardened call_123 form.
    fun shouldRetrySocket(closeCode: Int): Boolean = closeCode != 1000 && closeCode != 1008 && closeCode != 1003
    fun isAuthFailure(httpCode: Int?): Boolean = httpCode == 401 || httpCode == 403
    fun isValidCallId(value: String): Boolean = value.length <= MAX_CALL_ID_LENGTH && callIdPattern.matches(value)
    fun isValidCallSignal(value: String): Boolean = value.length <= MAX_SIGNAL_LENGTH && value in validSignals
    fun isValidCallType(value: String): Boolean = value == "voice" || value == "video"
}
