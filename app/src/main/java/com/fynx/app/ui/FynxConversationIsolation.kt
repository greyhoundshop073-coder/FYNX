package com.fynx.app.ui

import java.util.Locale

/**
 * Accept a realtime private message only when it belongs to the peer currently open.
 *
 * The realtime socket is account-scoped, not conversation-scoped. Checking only that
 * the signed-in user is one endpoint can leak another private conversation into the
 * currently displayed thread. Prefer the peer's stable user ID when available; use
 * the normalized username only while the peer ID has not yet resolved.
 */
internal fun belongsToDirectConversation(
    remoteSenderId: String,
    remoteSenderUsername: String?,
    remoteRecipientId: String,
    remoteRecipientUsername: String?,
    currentUserId: String,
    peerUserId: String?,
    peerUsername: String
): Boolean {
    val currentId = currentUserId.trim()
    if (currentId.isBlank()) return false

    val expectedPeerId = peerUserId?.trim()?.takeIf { it.isNotBlank() }
    val expectedPeerUsername = peerUsername.trim()
        .removePrefix("@")
        .lowercase(Locale.ROOT)
    if (expectedPeerId == null && expectedPeerUsername.isBlank()) return false

    fun matchesPeer(userId: String, username: String?): Boolean {
        if (expectedPeerId != null) return userId.trim() == expectedPeerId
        val actualUsername = username?.trim()
            ?.removePrefix("@")
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        return actualUsername.isNotBlank() && actualUsername == expectedPeerUsername
    }

    val senderIsCurrentUser = remoteSenderId.trim() == currentId
    val recipientIsCurrentUser = remoteRecipientId.trim() == currentId

    // A valid direct message has exactly one endpoint equal to the signed-in account.
    if (senderIsCurrentUser == recipientIsCurrentUser) return false

    return if (senderIsCurrentUser) {
        matchesPeer(remoteRecipientId, remoteRecipientUsername)
    } else {
        matchesPeer(remoteSenderId, remoteSenderUsername)
    }
}
