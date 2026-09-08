package com.fynx.app.ui

/** Group management and moderation contracts. */
enum class FynxGroupMemberAction { INVITE, REMOVE, BLOCK, UNBLOCK, PROMOTE_MODERATOR, DEMOTE_MODERATOR }

data class FynxGroupInvite(val groupId: String, val inviterUsername: String, val inviteeUsername: String)

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
    private fun clean(value: String): String = value.trim().removePrefix("@").trim()

    fun createInvite(groupId: String, inviter: String, invitee: String): FynxGroupInvite? {
        val cleanGroup = groupId.trim()
        val cleanInviter = clean(inviter)
        val cleanInvitee = clean(invitee)
        return if (cleanGroup.isBlank() || cleanInviter.isBlank() || cleanInvitee.isBlank() ||
            cleanInviter.equals(cleanInvitee, true)
        ) null else FynxGroupInvite(cleanGroup, cleanInviter, cleanInvitee)
    }

    fun createReport(groupId: String, reporter: String, target: String?, reason: String): FynxGroupReport? {
        val cleanGroup = groupId.trim()
        val cleanReporter = clean(reporter)
        val cleanTarget = target?.let(::clean)?.takeIf { it.isNotBlank() }
        val cleanReason = reason.trim().replace(Regex("\\s+"), " ")
        return if (cleanGroup.isBlank() || cleanReporter.isBlank() || cleanReason.length < 3) null
        else FynxGroupReport(cleanGroup, cleanReporter, cleanTarget, cleanReason)
    }

    fun applyAction(state: FynxGroupManagementState, username: String, action: FynxGroupMemberAction): FynxGroupManagementState? {
        val target = clean(username)
        if (target.isBlank() || target.equals(state.groupId.trim(), true)) return null
        val blocked = state.blockedUsernames.map(::clean).filter { it.isNotBlank() }.toMutableSet()
        val moderators = state.moderatorUsernames.map(::clean).filter { it.isNotBlank() }.toMutableSet()
        when (action) {
            FynxGroupMemberAction.INVITE, FynxGroupMemberAction.REMOVE -> Unit
            FynxGroupMemberAction.BLOCK -> blocked.add(target)
            FynxGroupMemberAction.UNBLOCK -> blocked.remove(target)
            FynxGroupMemberAction.PROMOTE_MODERATOR -> moderators.add(target)
            FynxGroupMemberAction.DEMOTE_MODERATOR -> moderators.remove(target)
        }
        return state.copy(blockedUsernames = blocked, moderatorUsernames = moderators)
    }

    /** True when the actor can open the group-management controls. */
    fun canManage(role: FynxGroupRole): Boolean =
        role == FynxGroupRole.ADMIN || role == FynxGroupRole.MODERATOR

    /** Role-aware guard for member management; admins can manage everyone except themselves. */
    fun canManageTarget(
        actorRole: FynxGroupRole,
        targetRole: FynxGroupRole,
        actorUsername: String,
        targetUsername: String,
        action: FynxGroupMemberAction
    ): Boolean {
        val actor = clean(actorUsername)
        val target = clean(targetUsername)
        if (actor.isBlank() || target.isBlank() || actor.equals(target, true)) return false
        if (actorRole == FynxGroupRole.ADMIN) return targetRole != FynxGroupRole.ADMIN
        if (actorRole != FynxGroupRole.MODERATOR) return false
        return targetRole == FynxGroupRole.MEMBER &&
            action in setOf(
                FynxGroupMemberAction.REMOVE,
                FynxGroupMemberAction.BLOCK,
                FynxGroupMemberAction.UNBLOCK
            )
    }
}
