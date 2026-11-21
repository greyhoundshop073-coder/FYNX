package com.fynx.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxConversationIsolationTest {
    @Test
    fun acceptsIncomingMessageFromTheOpenPeer() {
        assertTrue(belongsToDirectConversation("peer-42", "runtimepeer", "owner-7", "owner", "owner-7", "peer-42", "@runtimepeer"))
    }

    @Test
    fun acceptsOutgoingMessageToTheOpenPeer() {
        assertTrue(belongsToDirectConversation("owner-7", "owner", "peer-42", "runtimepeer", "owner-7", "peer-42", "runtimepeer"))
    }

    @Test
    fun rejectsAnotherConversationInvolvingTheSignedInUser() {
        assertFalse(belongsToDirectConversation("owner-7", "owner", "different-peer", "different", "owner-7", "peer-42", "runtimepeer"))
    }

    @Test
    fun usesNormalizedUsernameOnlyWhenPeerIdHasNotResolved() {
        assertTrue(belongsToDirectConversation("peer-42", "@RuntimePeer", "owner-7", "owner", "owner-7", null, "@runtimepeer"))
    }

    @Test
    fun doesNotFallBackToUsernameWhenStablePeerIdIsKnown() {
        assertFalse(belongsToDirectConversation("different-peer", "runtimepeer", "owner-7", "owner", "owner-7", "peer-42", "runtimepeer"))
    }

    @Test
    fun rejectsBlankIdentityAndSelfAddressedMessages() {
        assertFalse(belongsToDirectConversation("owner-7", "owner", "peer-42", "runtimepeer", "", "peer-42", "runtimepeer"))
        assertFalse(belongsToDirectConversation("owner-7", "owner", "owner-7", "owner", "owner-7", "peer-42", "runtimepeer"))
    }
}
