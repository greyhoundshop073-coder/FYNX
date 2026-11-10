package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxContactsDisclosureTest {
    @Test
    fun disclosureAcknowledgementIsScopedToTheSignedInAccount() {
        assertNotEquals(
            FynxContactsDisclosure.preferenceKey("alice"),
            FynxContactsDisclosure.preferenceKey("bob")
        )
    }

    @Test
    fun accountKeyNormalisationKeepsTheSameAccountPreferenceStable() {
        assertEquals(
            FynxContactsDisclosure.preferenceKey("alice"),
            FynxContactsDisclosure.preferenceKey(" ALICE ")
        )
    }

    @Test
    fun newOrOlderDisclosureVersionsMustBeShownAgain() {
        assertTrue(FynxContactsDisclosure.requiresDisclosure(0))
        assertTrue(FynxContactsDisclosure.requiresDisclosure(FynxContactsDisclosure.CURRENT_VERSION - 1))
    }

    @Test
    fun currentDisclosureVersionDoesNotRepeat() {
        assertFalse(FynxContactsDisclosure.requiresDisclosure(FynxContactsDisclosure.CURRENT_VERSION))
        assertFalse(FynxContactsDisclosure.requiresDisclosure(FynxContactsDisclosure.CURRENT_VERSION + 1))
    }
}
