package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FynxSystemBarAppearanceTest {
    @Test
    fun appLightAppearanceOverridesDarkAndroidSystemTheme() {
        assertEquals(false, fynxEffectiveDarkMode("Light", systemDarkMode = true, scheduledNightModeActive = false))
        assertEquals(FynxSystemBarIconAppearance(true, true), fynxSystemBarIconAppearance(
            darkMode = fynxEffectiveDarkMode("Light", systemDarkMode = true, scheduledNightModeActive = false)
        ))
    }

    @Test
    fun appDarkAppearanceOverridesLightAndroidSystemTheme() {
        assertEquals(true, fynxEffectiveDarkMode("Dark", systemDarkMode = false, scheduledNightModeActive = false))
        assertEquals(FynxSystemBarIconAppearance(false, false), fynxSystemBarIconAppearance(
            darkMode = fynxEffectiveDarkMode("Dark", systemDarkMode = false, scheduledNightModeActive = false)
        ))
    }

    @Test
    fun systemAppearanceFollowsAndroidSystemTheme() {
        assertEquals(true, fynxEffectiveDarkMode("System", systemDarkMode = true, scheduledNightModeActive = false))
        assertEquals(false, fynxEffectiveDarkMode("System", systemDarkMode = false, scheduledNightModeActive = false))
    }

    @Test
    fun scheduledNightModeCanDarkenSystemAppearance() {
        assertEquals(true, fynxEffectiveDarkMode("System", systemDarkMode = false, scheduledNightModeActive = true))
    }

    @Test
    fun brandedDarkAppearancesAlwaysUseLightSystemBarIcons() {
        listOf("Charcoal Black", "Black AMOLED").forEach { appearance ->
            assertEquals(true, fynxEffectiveDarkMode(appearance, systemDarkMode = false, scheduledNightModeActive = false))
            assertEquals(FynxSystemBarIconAppearance(false, false), fynxSystemBarIconAppearance(
                darkMode = fynxEffectiveDarkMode(appearance, systemDarkMode = false, scheduledNightModeActive = false)
            ))
        }
    }

    @Test
    fun lightAppearanceUsesDarkSystemBarIcons() {
        assertEquals(FynxSystemBarIconAppearance(true, true), fynxSystemBarIconAppearance(darkMode = false))
    }

    @Test
    fun darkAppearanceUsesLightSystemBarIcons() {
        assertEquals(FynxSystemBarIconAppearance(false, false), fynxSystemBarIconAppearance(darkMode = true))
    }
}
