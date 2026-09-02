package com.fynx.app.ui

/**
 * Stage 4 foundation: social relationships and stories.
 *
 * Group and group-invite models live in the existing group batch files so this
 * foundation does not redeclare them. This file intentionally remains
 * dependency-free and does not change navigation, chat, authentication, or UI.
 */

data class FynxSocialUser(
    val username: String,
    val displayName: String,
    val avatarUri: String? = null
)

enum class FynxFriendStatus {
    NONE,
    REQUESTED,
    PENDING,
    OUTGOING_PENDING,
    INCOMING_PENDING,
    FRIENDS,
    DECLINED,
    BLOCKED
}

data class FynxFriendConnection(
    val username: String,
    val status: FynxFriendStatus
)

/** Legacy social-foundation Story model retained for compatibility. */
data class FynxSocialStory(
    val id: String,
    val authorUsername: String,
    val mediaUri: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val caption: String = ""
) {
    fun isExpired(nowMillis: Long): Boolean = nowMillis >= expiresAtMillis
}

object FynxSocialValidation {
    private const val MAX_USERNAME_LENGTH = 30
    private const val MAX_DISPLAY_NAME_LENGTH = 80
    private const val MAX_GROUP_NAME_LENGTH = 60
    private const val MAX_GROUP_DESCRIPTION_LENGTH = 500

    fun username(value: String): String? = value.trim().takeIf {
        it.isNotEmpty() && it.length <= MAX_USERNAME_LENGTH &&
            it.all { character -> character.isLetterOrDigit() || character == '_' || character == '.' }
    }

    fun displayName(value: String): String? = value.trim().takeIf {
        it.isNotEmpty() && it.length <= MAX_DISPLAY_NAME_LENGTH
    }

    fun groupName(value: String): String? = value.trim().takeIf {
        it.isNotEmpty() && it.length <= MAX_GROUP_NAME_LENGTH
    }

    fun groupDescription(value: String): String? = value.trim().takeIf {
        it.length <= MAX_GROUP_DESCRIPTION_LENGTH
    }

    fun canAddMember(group: FynxGroup, username: String): Boolean =
        username.isNotBlank() && group.members.none { it.username == username }
}
