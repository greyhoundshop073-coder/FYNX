package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxProfileActionRulesTest {
    @Test
    fun recognizesOwnProfileAcrossAtPrefixAndCaseDifferences() {
        assertTrue(isOwnFynxProfile("@Miracle", "miracle"))
        assertTrue(isOwnFynxProfile(" MIRACLE ", "@miracle"))
    }

    @Test
    fun doesNotTreatAnotherOrMissingAccountAsOwnProfile() {
        assertFalse(isOwnFynxProfile("seller", "buyer"))
        assertFalse(isOwnFynxProfile("seller", null))
        assertFalse(isOwnFynxProfile("seller", "   "))
        assertFalse(isOwnFynxProfile("   ", "seller"))
    }

    @Test
    fun rejectsEmptyAndWhitespaceOnlyReportReasons() {
        assertEquals(
            "Enter a reason before submitting this report.",
            validateProfileReportReason("")
        )
        assertEquals(
            "Enter a reason before submitting this report.",
            validateProfileReportReason("   \t\n")
        )
    }

    @Test
    fun acceptsAReasonContainingVisibleText() {
        assertNull(validateProfileReportReason("Spam"))
        assertNull(validateProfileReportReason("  Harassment  "))
    }
}
