package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FynxChatGroupUnreadBadgeStateTest {
    @Test
    fun groupUnreadCount_aggregatesPositiveUnreadMessages() {
        assertEquals(
            7,
            fynxGroupUnreadCount(
                mapOf(
                    "group-a" to 3,
                    "group-b" to 0,
                    "group-c" to 4,
                )
            )
        )
    }

    @Test
    fun groupUnreadCount_ignoresNegativeValues() {
        assertEquals(
            2,
            fynxGroupUnreadCount(
                mapOf(
                    "group-a" to -2,
                    "group-b" to 2,
                )
            )
        )
    }

    @Test
    fun groupUnreadCount_emptyStateIsZero() {
        assertEquals(0, fynxGroupUnreadCount(emptyMap()))
    }
}
