package com.fynx.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxGroupCallOfferPolicyTest {
    @Test
    fun exactlyOneEndpointOwnsTheInitialOfferForEachPair() {
        val first = "member-001"
        val second = "member-002"

        assertTrue(FynxGroupCallOfferPolicy.shouldCreateOffer(first, second))
        assertFalse(FynxGroupCallOfferPolicy.shouldCreateOffer(second, first))
    }

    @Test
    fun serverIdsAreComparedDeterministicallyAsOpaqueStrings() {
        assertTrue(FynxGroupCallOfferPolicy.shouldCreateOffer("10", "2"))
        assertFalse(FynxGroupCallOfferPolicy.shouldCreateOffer("2", "10"))
    }

    @Test
    fun blankOrIdenticalIdsNeverStartAnOffer() {
        assertFalse(FynxGroupCallOfferPolicy.shouldCreateOffer(null, "member-001"))
        assertFalse(FynxGroupCallOfferPolicy.shouldCreateOffer("", "member-001"))
        assertFalse(FynxGroupCallOfferPolicy.shouldCreateOffer("member-001", " "))
        assertFalse(FynxGroupCallOfferPolicy.shouldCreateOffer("member-001", "member-001"))
    }
}
