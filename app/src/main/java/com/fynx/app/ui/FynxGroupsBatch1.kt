package com.fynx.app.ui

/** Group foundation models and validation contracts. */
enum class FynxGroupVisibility { PUBLIC, PRIVATE }
enum class FynxGroupRole { MEMBER, MODERATOR, ADMIN }

data class FynxGroupMember(
    val username: String,
    val role: FynxGroupRole = FynxGroupRole.MEMBER
)

data class FynxGroup(
    val id: String,
    val name: String,
    val description: String,
    val visibility: FynxGroupVisibility,
    val ownerUsername: String,
    val members: List<FynxGroupMember> = emptyList()
)

object FynxGroupsBatch1 {
    fun validate(group: FynxGroup): List<String> = buildList {
        if (group.id.isBlank()) add("Group ID is required")
        if (group.name.trim().length < 2) add("Group name is required")
        if (group.description.trim().length < 2) add("Group description is required")
        if (group.ownerUsername.isBlank()) add("Group owner is required")
        if (group.members.none { it.username == group.ownerUsername }) add("Group owner must be a member")
        if (group.members.groupBy { it.username }.any { it.value.size > 1 }) add("Group members must be unique")
        if (group.members.count { it.role == FynxGroupRole.ADMIN } != 1) add("A group must have exactly one admin")
    }

    fun create(group: FynxGroup): FynxGroup? =
        if (validate(group).isEmpty()) group.copy(members = group.members.distinctBy { it.username }) else null

    fun canManage(role: FynxGroupRole): Boolean =
        role == FynxGroupRole.ADMIN || role == FynxGroupRole.MODERATOR
}
