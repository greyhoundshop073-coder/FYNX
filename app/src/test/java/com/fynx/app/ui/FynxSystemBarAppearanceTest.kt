package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FynxSystemBarAppearanceTest {
    @Test
    fun lightAppearanceUsesDarkSystemBarIcons() {
        assertEquals(FynxSystemBarIconAppearance(true, true), fynxSystemBarIconAppearance(darkMode = false))
    }

    @Test
    fun darkAppearanceUsesLightSystemBarIcons() {
        assertEquals(FynxSystemBarIconAppearance(false, false), fynxSystemBarIconAppearance(darkMode = true))
    }
}
