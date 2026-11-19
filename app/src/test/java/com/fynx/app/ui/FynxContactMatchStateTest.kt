package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FynxContactMatchStateTest {
    @Test
    fun successfulMatchIsShownAsMatched() {
        assertEquals(FynxContactMatchState.MATCHED, fynxContactMatchState(matched = true, lookupFailed = false))
    }

    @Test
    fun successfulEmptyResultIsShownAsNotOnFynx() {
        assertEquals(FynxContactMatchState.NOT_ON_FYNX, fynxContactMatchState(matched = false, lookupFailed = false))
    }

    @Test
    fun failedLookupIsNeverTreatedAsAnUnmatchedContact() {
        assertEquals(FynxContactMatchState.UNAVAILABLE, fynxContactMatchState(matched = false, lookupFailed = true))
    }
}
