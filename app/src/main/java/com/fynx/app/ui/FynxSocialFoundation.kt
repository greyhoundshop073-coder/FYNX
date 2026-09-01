package com.fynx.app.ui

/**
 * Stage 4 foundation: groups and social relationships.
 *
 * This file intentionally contains only dependency-free domain models and
 * validation helpers. It does not change navigation, chat, authentication,
 * or existing UI until those integration points are verified separately.
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
    FRIENDS,
    BLOCKED
}

data class FynxFriendConnection(
    val username: String,
    val status: FynxFriendStatus
)

data class FynxGroup(
    val id: String,
    val name: String,
    val description: String = "",
    val ownerUsername: String,
    val memberUsernames: List<String> = emptyList(),
    val avatarUri: String? = null
)

data class FynxGroupInvite(
    val groupId: String,
    val invitedUsername: String,
    val invitedByUsername: String
)

data class FynxStory(
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
        username.isNotBlank() && username !in group.memberUsernames
}
