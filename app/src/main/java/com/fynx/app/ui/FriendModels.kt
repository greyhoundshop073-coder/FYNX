package com.fynx.app.ui

enum class FynxFriendStatus {
    NONE,
    OUTGOING_PENDING,
    INCOMING_PENDING,
    FRIENDS,
    DECLINED,
    BLOCKED
}

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
