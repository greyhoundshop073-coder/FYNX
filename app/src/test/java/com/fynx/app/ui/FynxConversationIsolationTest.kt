package com.fynx.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxConversationIsolationTest {
    @Test
    fun acceptsIncomingMessageFromTheOpenPeer() {
        assertTrue(
            belongsToDirectConversation(
                remoteSenderId = "peer-42",
                remoteSenderUsername = "runtimepeer",
                remoteRecipientId = "owner-7",
                remoteRecipientUsername = "owner",
                currentUserId = "owner-7",
                peerUserId = "peer-42",
                peerUsername = "@runtimepeer"
            )
        )
    }

    @Test
    fun acceptsOutgoingMessageToTheOpenPeer() {
        assertTrue(
            belongsToDirectConversation(
                remoteSenderId = "owner-7",
                remoteSenderUsername = "owner",
                remoteRecipientId = "peer-42",
                remoteRecipientUsername = "runtimepeer",
                currentUserId = "owner-7",
                peerUserId = "peer-42",
                peerUsername = "runtimepeer"
            )
        )
    }

    @Test
    fun rejectsAnotherConversationInvolvingTheSignedInUser() {
        assertFalse(
            belongsToDirectConversation(
                remoteSenderId = "owner-7",
                remoteSenderUsername = "owner",
                remoteRecipientId = "different-peer",
                remoteRecipientUsername = "different",
                currentUserId = "owner-7",
                peerUserId = "peer-42",
                peerUsername = "runtimepeer"
            )
        )
    }

    @Test
    fun usesNormalizedUsernameOnlyWhenPeerIdHasNotResolved() {
        assertTrue(
            belongsToDirectConversation(
                remoteSenderId = "peer-42",
                remoteSenderUsername = "@RuntimePeer",
                remoteRecipientId = "owner-7",
                remoteRecipientUsername = "owner",
                currentUserId = "owner-7",
                peerUserId = null,
                peerUsername = "@runtimepeer"
            )
        )
    }

    @Test
    fun doesNotFallBackToUsernameWhenStablePeerIdIsKnown() {
        assertFalse(
            belongsToDirectConversation(
                remoteSenderId = "different-peer",
                remoteSenderUsername = "runtimepeer",
                remoteRecipientId = "owner-7",
                remoteRecipientUsername = "owner",
                currentUserId = "owner-7",
                peerUserId = "peer-42",
                peerUsername = "runtimepeer"
            )
        )
    }

    @Test
    fun rejectsBlankIdentityAndSelfAddressedMessages() {
        assertFalse(
            belongsToDirectConversation(
                remoteSenderId = "owner-7",
                remoteSenderUsername = "owner",
                remoteRecipientId = "peer-42",
                remoteRecipientUsername = "runtimepeer",
                currentUserId = "",
                peerUserId = "peer-42",
                peerUsername = "runtimepeer"
            )
        )
        assertFalse(
            belongsToDirectConversation(
                remoteSenderId = "owner-7",
                remoteSenderUsername = "owner",
                remoteRecipientId = "owner-7",
                remoteRecipientUsername = "owner",
                currentUserId = "owner-7",
                peerUserId = "peer-42",
                peerUsername = "runtimepeer"
            )
        )
    }
}
