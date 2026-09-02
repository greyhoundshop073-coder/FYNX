package com.fynx.app.ui

data class StoryPreview(
    val displayName: String,
    val username: String,
    val timeLabel: String,
    val hasProfilePhoto: Boolean = false,
    val isMine: Boolean = false
)

enum class FynxStoryType { PHOTO, VIDEO, TEXT }

data class FynxStory(
    val id: String,
    val ownerName: String,
    val ownerUsername: String,
    val type: FynxStoryType,
    val contentUri: String? = null,
    val text: String? = null,
    val createdAtMillis: Long,
    val privateStory: Boolean = false,
    val reaction: String? = null,
    val reply: String? = null
) {
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean =
        now - createdAtMillis >= 24L * 60L * 60L * 1000L
}

/** Kept for compatibility with existing UI foundations; real friend Stories are not fabricated. */
val sampleStories = emptyList<StoryPreview>()
