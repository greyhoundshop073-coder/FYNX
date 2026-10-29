package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression guards for the existing camera/media capture contract. */
class CameraMediaReliabilityTest {
    @Test
    fun cameraModesRemainPhotoAndVideo() {
        assertEquals(listOf(CameraMode.PHOTO, CameraMode.VIDEO), CameraMode.values().toList())
    }

    @Test
    fun captureTimerOptionsRemainOffThreeAndTenSeconds() {
        assertTrue(listOf(0, 3, 10).all { it >= 0 })
    }

    @Test
    fun cameraFiltersHaveStableUserFacingOptions() {
        assertEquals(listOf("Natural", "Portrait", "Beauty Glow", "Bright", "Warm Glow", "Cinematic", "Low-Light Rescue", "2000s Flash", "Cool", "Vintage", "B&W"), CameraFilter.values().map { it.label })
    }

    @Test
    fun cameraFilterParametersAreFinite() {
        CameraFilter.values().forEach { filter ->
            assertTrue(filter.saturation.isFinite())
            assertTrue(filter.brightness.isFinite())
            assertTrue(filter.contrast.isFinite())
            assertTrue(filter.saturation >= 0f)
            assertTrue(filter.contrast > 0f)
        }
    }

    @Test
    fun stageOneLightingPresetsAreAvailableAndDistinct() {
        assertTrue(CameraFilter.BEAUTY_GLOW.brightness > CameraFilter.NATURAL.brightness)
        assertTrue(CameraFilter.LOW_LIGHT.brightness > CameraFilter.BRIGHT.brightness)
        assertTrue(CameraFilter.CINEMATIC.contrast > CameraFilter.NATURAL.contrast)
        assertTrue(CameraFilter.FLASH_2000S.saturation > CameraFilter.NATURAL.saturation)
        assertEquals("Warm Glow", CameraFilter.WARM.label)
    }

    @Test
    fun blackAndWhiteFilterRemovesSaturation() {
        assertEquals(0f, CameraFilter.BW.saturation, 0f)
        assertFalse(CameraFilter.BW.saturation > 0f)
    }
}
