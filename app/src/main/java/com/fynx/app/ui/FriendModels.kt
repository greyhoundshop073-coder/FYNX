package com.fynx.app.ui

data class FriendProfile(
    val displayName: String,
    val username: String,
    val bio: String = "",
    val hasProfilePhoto: Boolean = false,
    val isFriend: Boolean = false,
    val requestSent: Boolean = false
)

val samplePeople = listOf(
    FriendProfile("FYNX Assistant", "@fynx", "Your helpful FYNX assistant"),
    FriendProfile("Your first friend", "@username", "Say hello and start chatting")
)
