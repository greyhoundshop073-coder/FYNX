package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FynxNotificationBadgeTest {
    @Test
    fun zeroAndNegativeCounts_hideBadgeLabel() {
        assertEquals("", fynxUnreadBadgeLabel(0))
        assertEquals("", fynxUnreadBadgeLabel(-1))
    }

    @Test
    fun counts_areDisplayedUpToNinetyNine() {
        assertEquals("1", fynxUnreadBadgeLabel(1))
        assertEquals("9", fynxUnreadBadgeLabel(9))
        assertEquals("99", fynxUnreadBadgeLabel(99))
    }

    @Test
    fun counts_aboveNinetyNine_areCapped() {
        assertEquals("99+", fynxUnreadBadgeLabel(100))
        assertEquals("99+", fynxUnreadBadgeLabel(1000))
    }
}
