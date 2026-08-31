package com.fynx.app.ui

data class FynxProfile(
    val displayName: String = "Your name",
    val username: String = "@username",
    val bio: String = "Welcome to FYNX"
)

data class FynxSettings(
    val notifications: Boolean = true,
    val privateProfile: Boolean = false,
    val readReceipts: Boolean = true,
    val storyReplies: Boolean = true
)
