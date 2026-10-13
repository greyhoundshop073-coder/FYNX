package com.fynx.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxIncomingCallRoutingTest {
    @Test
    fun routesAnInviteFromAnyNonCallsScreen() {
        assertTrue(FynxIncomingCallRouting.shouldRouteInvite("invite", "Home", false))
        assertTrue(FynxIncomingCallRouting.shouldRouteInvite("INVITE", "Profile", false))
        assertTrue(FynxIncomingCallRouting.shouldRouteInvite("invite", "Chats", false))
    }

    @Test
    fun leavesInvitesToTheActiveCallsScreen() {
        assertFalse(FynxIncomingCallRouting.shouldRouteInvite("invite", "Calls", false))
    }

    @Test
    fun doesNotReplaceAnAlreadyPendingInvite() {
        assertFalse(FynxIncomingCallRouting.shouldRouteInvite("invite", "Home", true))
    }

    @Test
    fun ignoresNonInviteCallSignals() {
        assertFalse(FynxIncomingCallRouting.shouldRouteInvite("offer", "Home", false))
        assertFalse(FynxIncomingCallRouting.shouldRouteInvite("end", "Chats", false))
    }
}
