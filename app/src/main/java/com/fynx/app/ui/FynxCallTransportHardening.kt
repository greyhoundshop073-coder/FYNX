package com.fynx.app.ui

/** Small call-transport guards shared by realtime/call screens. */
object FynxCallTransportHardening {
    // Accept both the legacy call-123 form and the hardened call_123 form.
    private val callIdPattern = Regex("^call[-_][a-z0-9_-]{1,70}$", RegexOption.IGNORE_CASE)

    fun shouldRetrySocket(closeCode: Int): Boolean = closeCode != 1000 && closeCode != 1008 && closeCode != 1003
    fun isAuthFailure(httpCode: Int?): Boolean = httpCode == 401 || httpCode == 403
    fun isValidCallId(value: String): Boolean = callIdPattern.matches(value)
    fun isValidCallSignal(value: String): Boolean = value in setOf("invite", "accept", "reject", "end", "offer", "answer", "ice", "unavailable", "busy")
    fun isValidCallType(value: String): Boolean = value == "voice" || value == "video"
}
