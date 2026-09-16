package com.fynx.app

import com.fynx.app.ui.FynxCallTransportHardening
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for terminal call-signal semantics used by voice/video transport. */
class FynxCallTerminalStateRegressionTest {
    @Test
    fun terminalSignalsAreClosedStates() {
        listOf("reject", "end", "unavailable", "busy").forEach {
            assertTrue("$it must terminate a call", FynxCallTransportHardening.isTerminalSignal(it))
        }
        listOf("invite", "accept", "offer", "answer", "ice").forEach {
            assertFalse("$it must not terminate a call", FynxCallTransportHardening.isTerminalSignal(it))
        }
    }

    @Test
    fun unavailableIsDistinctFromBusy() {
        assertTrue(FynxCallTransportHardening.isUnavailableSignal("unavailable"))
        assertFalse(FynxCallTransportHardening.isUnavailableSignal("busy"))
        assertFalse(FynxCallTransportHardening.isUnavailableSignal("end"))
    }

    @Test
    fun terminalSignalsRemainInsideSupportedCallProtocol() {
        listOf("reject", "end", "unavailable", "busy").forEach {
            assertTrue(FynxCallTransportHardening.isValidCallSignal(it))
            assertTrue(FynxCallTransportHardening.isTerminalSignal(it))
        }
    }
}
