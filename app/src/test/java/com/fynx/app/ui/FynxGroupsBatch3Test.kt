package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxGroupsBatch3Test {
    private fun group(): FynxGroup = FynxGroup(
        id = "group-1",
        name = "FYNX Developers",
        description = "Test group",
        visibility = FynxGroupVisibility.PRIVATE,
        ownerUsername = "owner",
        members = listOf(
            FynxGroupMember("owner", FynxGroupRole.ADMIN),
            FynxGroupMember("moderator", FynxGroupRole.MODERATOR),
            FynxGroupMember("member", FynxGroupRole.MEMBER)
        )
    )

    @Test
    fun report_requires_reason_and_normalizes_usernames() {
        val report = FynxGroupsBatch3.createReport("group-1", "@reporter", "@member", "  spam  ")
        assertNotNull(report)
        assertEquals("reporter", report!!.reporterUsername)
        assertEquals("member", report.targetUsername)
        assertEquals("spam", report.reason)
        assertNull(FynxGroupsBatch3.createReport("group-1", "reporter", "member", "  "))
    }

    @Test
    fun management_requires_admin_or_moderator_and_protects_owner() {
        assertTrue(FynxGroupsBatch3.canManage(FynxGroupRole.ADMIN))
        assertTrue(FynxGroupsBatch3.canManage(FynxGroupRole.MODERATOR))
        assertFalse(FynxGroupsBatch3.canManage(FynxGroupRole.MEMBER))

        val owner = group().members.first { it.username == "owner" }
        val moderator = group().members.first { it.username == "moderator" }
        val member = group().members.first { it.username == "member" }

        assertFalse(FynxGroupsBatch3.canManageMember(owner, owner, "owner"))
        assertFalse(FynxGroupsBatch3.canManageMember(moderator, owner, "owner"))
        assertTrue(FynxGroupsBatch3.canManageMember(moderator, member, "owner"))
        assertFalse(FynxGroupsBatch3.canManageMember(member, moderator, "owner"))
    }

    @Test
    fun management_state_actions_are_reversible() {
        val initial = FynxGroupManagementState("group-1")
        val blocked = FynxGroupsBatch3.applyAction(initial, "@member", FynxGroupMemberAction.BLOCK)
        assertNotNull(blocked)
        assertTrue(blocked!!.blockedUsernames.contains("member"))

        val unblocked = FynxGroupsBatch3.applyAction(blocked, "member", FynxGroupMemberAction.UNBLOCK)
        assertNotNull(unblocked)
        assertFalse(unblocked!!.blockedUsernames.contains("member"))

        val promoted = FynxGroupsBatch3.applyAction(unblocked, "member", FynxGroupMemberAction.PROMOTE_MODERATOR)
        assertNotNull(promoted)
        assertTrue(promoted!!.moderatorUsernames.contains("member"))

        val demoted = FynxGroupsBatch3.applyAction(promoted, "member", FynxGroupMemberAction.DEMOTE_MODERATOR)
        assertNotNull(demoted)
        assertFalse(demoted!!.moderatorUsernames.contains("member"))
    }

    @Test
    fun invite_link_requires_group_management_role() {
        val group = group()
        assertNotNull(FynxGroupsBatch3.createInviteLink(group, "owner"))
        assertNotNull(FynxGroupsBatch3.createInviteLink(group, "moderator"))
        assertNull(FynxGroupsBatch3.createInviteLink(group, "member"))
    }
}
