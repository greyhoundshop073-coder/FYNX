package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FynxBottomNavLabelTest {
    @Test
    fun marketplaceLabelStaysFullWhenItFits() {
        assertEquals("Marketplace", fynxBottomNavLabel("Marketplace", 411, 1f))
        assertEquals("Marketplace", fynxBottomNavLabel("Marketplace", 360, 1f))
        assertEquals("Marketplace", fynxBottomNavLabel("Marketplace", 800, 1f))
    }

    @Test
    fun marketplaceLabelShortensOnNarrowScreensAndLargeFonts() {
        assertEquals("Market", fynxBottomNavLabel("Marketplace", 320, 1f))
        assertEquals("Market", fynxBottomNavLabel("Marketplace", 411, 1.5f))
    }

    @Test
    fun otherLabelsNeverChange() {
        assertEquals("Friends", fynxBottomNavLabel("Friends", 320, 2f))
    }
}
