package com.fynx.app.ui

data class StoryPreview(
    val displayName: String,
    val username: String,
    val timeLabel: String,
    val hasProfilePhoto: Boolean = false,
    val isMine: Boolean = false
)

val sampleStories = listOf(
    StoryPreview("Your story", "@username", "Add a story", isMine = true),
    StoryPreview("FYNX Assistant", "@fynx", "Today"),
    StoryPreview("Your first friend", "@username2", "Today")
)
