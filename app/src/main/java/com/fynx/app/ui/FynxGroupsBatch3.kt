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
    fun createInvite(groupId: String, inviter: String, invitee: String): FynxGroupInvite? =
        if (groupId.isBlank() || inviter.isBlank() || invitee.isBlank() || inviter == invitee) null
        else FynxGroupInvite(groupId, inviter, invitee)

    fun createReport(groupId: String, reporter: String, target: String?, reason: String): FynxGroupReport? =
        if (groupId.isBlank() || reporter.isBlank() || reason.trim().isEmpty()) null
        else FynxGroupReport(groupId, reporter, target?.takeIf { it.isNotBlank() }, reason.trim())

    fun applyAction(state: FynxGroupManagementState, username: String, action: FynxGroupMemberAction): FynxGroupManagementState? {
        if (username.isBlank() || username == state.groupId) return null
        val blocked = state.blockedUsernames.toMutableSet()
        val moderators = state.moderatorUsernames.toMutableSet()
        when (action) {
            FynxGroupMemberAction.INVITE, FynxGroupMemberAction.REMOVE -> Unit
            FynxGroupMemberAction.BLOCK -> blocked.add(username)
            FynxGroupMemberAction.UNBLOCK -> blocked.remove(username)
            FynxGroupMemberAction.PROMOTE_MODERATOR -> moderators.add(username)
            FynxGroupMemberAction.DEMOTE_MODERATOR -> moderators.remove(username)
        }
        return state.copy(blockedUsernames = blocked, moderatorUsernames = moderators)
    }

    fun canManage(role: FynxGroupRole): Boolean = role == FynxGroupRole.ADMIN || role == FynxGroupRole.MODERATOR
}
