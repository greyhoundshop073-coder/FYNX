package com.fynx.app

import com.fynx.app.ui.FynxCallTransportHardening
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxCallTransportHardeningTest {
    @Test
    fun accepts_supported_call_signals() {
        listOf("invite", "accept", "reject", "end", "offer", "answer", "ice", "unavailable", "busy")
            .forEach { signal -> assertTrue("expected supported signal: $signal", FynxCallTransportHardening.isValidCallSignal(signal)) }
    }

    @Test
    fun rejects_invalid_call_signals() {
        listOf("", "error", "CALL", "invite-now", "x".repeat(17))
            .forEach { signal -> assertFalse("expected invalid signal: $signal", FynxCallTransportHardening.isValidCallSignal(signal)) }
    }

    @Test
    fun accepts_both_call_id_forms() {
        assertTrue(FynxCallTransportHardening.isValidCallId("call-123"))
        assertTrue(FynxCallTransportHardening.isValidCallId("call_123"))
    }

    @Test
    fun retries_only_connection_level_failures() {
        assertTrue(FynxCallTransportHardening.shouldRetrySocket(1006))
        assertTrue(FynxCallTransportHardening.shouldRetrySocket(1011))
        assertFalse(FynxCallTransportHardening.shouldRetrySocket(1008))
        assertFalse(FynxCallTransportHardening.shouldRetrySocket(1009))
    }
}
