package com.fynx.app.ui

data class GroupChat(
    val id: String,
    val name: String,
    val description: String = "",
    val memberUsernames: List<String> = emptyList(),
    val adminUsernames: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

fun GroupChat.isAdmin(username: String): Boolean = username in adminUsernames

fun GroupChat.addMember(username: String): GroupChat =
    if (username in memberUsernames) this else copy(memberUsernames = memberUsernames + username)

fun GroupChat.removeMember(username: String): GroupChat =
    copy(
        memberUsernames = memberUsernames.filterNot { it == username },
        adminUsernames = adminUsernames.filterNot { it == username }
    )

fun GroupChat.promoteToAdmin(username: String): GroupChat =
    if (username !in memberUsernames || username in adminUsernames) this
    else copy(adminUsernames = adminUsernames + username)
