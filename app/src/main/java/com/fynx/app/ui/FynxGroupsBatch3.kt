package com.fynx.app.ui

import java.util.UUID

/** Group management and moderation contracts. */
enum class FynxGroupMemberAction { INVITE, REMOVE, BLOCK, UNBLOCK, PROMOTE_MODERATOR, DEMOTE_MODERATOR }

data class FynxGroupInvite(val groupId: String, val inviterUsername: String, val inviteeUsername: String)

data class FynxGroupInviteLink(
    val groupId: String,
    val token: String,
    val createdByUsername: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun asShareText(): String = "Join my FYNX group: fynx://group/$groupId/invite/$token"
}

data class FynxGroupReport(val groupId: String, val reporterUsername: String, val targetUsername: String?, val reason: String)

data class FynxGroupSettings(
    val groupId: String,
    val allowMemberPosts: Boolean = true,
    val allowMemberInvites: Boolean = true,
    val allowMarketplaceShares: Boolean = true,
    val notificationsEnabled: Boolean = true
)

data class FynxGroupManagementState(
    val groupId: String,
    val blockedUsernames: Set<String> = emptySet(),
    val moderatorUsernames: Set<String> = emptySet()
)

object FynxGroupsBatch3 {
    fun createInvite(groupId: String, inviter: String, invitee: String): FynxGroupInvite? =
        if (groupId.isBlank() || inviter.cleanUsername().isBlank() || invitee.cleanUsername().isBlank() ||
            inviter.cleanUsername().equals(invitee.cleanUsername(), true)) null
        else FynxGroupInvite(groupId, inviter.cleanUsername(), invitee.cleanUsername())

    fun createInviteLink(group: FynxGroup, creator: String): FynxGroupInviteLink? {
        val cleanCreator = creator.cleanUsername()
        if (group.id.isBlank() || cleanCreator.isBlank()) return null
        val member = group.members.firstOrNull { it.username.cleanUsername().equals(cleanCreator, true) } ?: return null
        if (member.role != FynxGroupRole.ADMIN && member.role != FynxGroupRole.MODERATOR) return null
        return FynxGroupInviteLink(group.id, UUID.randomUUID().toString().replace("-", ""), cleanCreator)
    }

    fun createReport(groupId: String, reporter: String, target: String?, reason: String): FynxGroupReport? =
        if (groupId.isBlank() || reporter.cleanUsername().isBlank() || reason.trim().isEmpty()) null
        else FynxGroupReport(groupId, reporter.cleanUsername(), target?.cleanUsername()?.takeIf { it.isNotBlank() }, reason.trim())

    fun canManage(role: FynxGroupRole): Boolean =
        role == FynxGroupRole.ADMIN || role == FynxGroupRole.MODERATOR

    fun canManageMember(actor: FynxGroupMember, target: FynxGroupMember, ownerUsername: String): Boolean {
        if (!canManage(actor.role)) return false
        if (actor.username.cleanUsername().equals(target.username.cleanUsername(), true)) return false
        if (target.username.cleanUsername().equals(ownerUsername.cleanUsername(), true)) return false
        return actor.role == FynxGroupRole.ADMIN || target.role == FynxGroupRole.MEMBER
    }

    fun canChangeAdmin(actor: FynxGroupMember, group: FynxGroup): Boolean =
        actor.role == FynxGroupRole.ADMIN &&
            actor.username.cleanUsername().equals(group.ownerUsername.cleanUsername(), true)

    fun removeMember(group: FynxGroup, actor: FynxGroupMember, username: String): FynxGroup? {
        val target = group.members.firstOrNull { it.username.cleanUsername().equals(username.cleanUsername(), true) } ?: return null
        if (!canManageMember(actor, target, group.ownerUsername)) return null
        val next = group.copy(members = group.members.filterNot { it.username.cleanUsername().equals(target.username.cleanUsername(), true) })
        return next.takeIf { FynxGroupsBatch1.validate(it).isEmpty() }
    }

    fun leaveGroup(group: FynxGroup, username: String): FynxGroup? {
        val clean = username.cleanUsername()
        if (clean.isBlank() || clean.equals(group.ownerUsername.cleanUsername(), true)) return null
        if (group.members.none { it.username.cleanUsername().equals(clean, true) }) return null
        val next = group.copy(members = group.members.filterNot { it.username.cleanUsername().equals(clean, true) })
        return next.takeIf { FynxGroupsBatch1.validate(it).isEmpty() }
    }

    fun canDeleteGroup(group: FynxGroup, username: String): Boolean =
        username.cleanUsername().equals(group.ownerUsername.cleanUsername(), true)

    fun applyAction(
        state: FynxGroupManagementState,
        username: String,
        action: FynxGroupMemberAction
    ): FynxGroupManagementState? {
        val clean = username.cleanUsername()
        if (clean.isBlank() || clean.equals(state.groupId, true)) return null
        val blocked = state.blockedUsernames.toMutableSet()
        val moderators = state.moderatorUsernames.toMutableSet()
        when (action) {
            FynxGroupMemberAction.INVITE,
            FynxGroupMemberAction.REMOVE -> Unit
            FynxGroupMemberAction.BLOCK -> blocked.add(clean)
            FynxGroupMemberAction.UNBLOCK -> blocked.remove(clean)
            FynxGroupMemberAction.PROMOTE_MODERATOR -> moderators.add(clean)
            FynxGroupMemberAction.DEMOTE_MODERATOR -> moderators.remove(clean)
        }
        return state.copy(blockedUsernames = blocked, moderatorUsernames = moderators)
    }

    private fun String.cleanUsername(): String = trim().removePrefix("@").trim()
}
