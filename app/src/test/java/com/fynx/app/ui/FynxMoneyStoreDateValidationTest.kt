package com.fynx.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression tests for date input accepted by local Money Center trackers. */
class FynxMoneyStoreDateValidationTest {
    @Test
    fun acceptsValidIsoCalendarDates() {
        assertTrue(FynxMoneyStore.isValidLocalDate("2026-10-13"))
        assertTrue(FynxMoneyStore.isValidLocalDate(" 2024-02-29 "))
    }

    @Test
    fun rejectsImpossibleCalendarDates() {
        assertFalse(FynxMoneyStore.isValidLocalDate("2025-02-29"))
        assertFalse(FynxMoneyStore.isValidLocalDate("2026-04-31"))
        assertFalse(FynxMoneyStore.isValidLocalDate("2026-13-01"))
    }

    @Test
    fun rejectsBlankAndMalformedDates() {
        assertFalse(FynxMoneyStore.isValidLocalDate(""))
        assertFalse(FynxMoneyStore.isValidLocalDate("   "))
        assertFalse(FynxMoneyStore.isValidLocalDate("13/10/2026"))
        assertFalse(FynxMoneyStore.isValidLocalDate("not-a-date"))
    }
}
