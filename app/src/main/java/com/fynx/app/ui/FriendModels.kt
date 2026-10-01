package com.fynx.app.ui

data class FriendProfile(
    val displayName: String,
    val username: String,
    val bio: String = "",
    val hasProfilePhoto: Boolean = false,
    val status: FynxFriendStatus = FynxFriendStatus.NONE
) {
    val isFriend: Boolean get() = status == FynxFriendStatus.FRIENDS
    val requestSent: Boolean get() = status == FynxFriendStatus.OUTGOING_PENDING
}
