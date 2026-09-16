package com.fynx.app

import com.fynx.app.ui.FynxCallTransportHardening
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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

    @Test
    fun terminalStatusMappingCannotDrift() {
        assertEquals("Declined", FynxCallTransportHardening.terminalStatus("reject"))
        assertEquals("Ended", FynxCallTransportHardening.terminalStatus("end"))
        assertEquals("Unavailable", FynxCallTransportHardening.terminalStatus("unavailable"))
        assertEquals("Busy", FynxCallTransportHardening.terminalStatus("busy"))
        listOf("invite", "accept", "offer", "answer", "ice", "unknown").forEach {
            assertNull("$it must not map to a terminal history status", FynxCallTransportHardening.terminalStatus(it))
        }
    }

    @Test
    fun terminalStatusMappingPreservesDistinctUserOutcomes() {
        val outcomes = listOf("reject", "end", "unavailable", "busy")
            .mapNotNull(FynxCallTransportHardening::terminalStatus)
        assertEquals(4, outcomes.size)
        assertEquals(4, outcomes.toSet().size)
    }
}
