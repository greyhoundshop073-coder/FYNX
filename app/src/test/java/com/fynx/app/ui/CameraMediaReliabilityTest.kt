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
    fun cameraFiltersHaveStableUserFacingOptions() {
        assertEquals(listOf("Natural", "Vivid", "Warm", "Cool", "B&W"), CameraFilter.values().map { it.label })
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
    fun blackAndWhiteFilterRemovesSaturation() {
        assertEquals(0f, CameraFilter.BW.saturation, 0f)
        assertFalse(CameraFilter.BW.saturation > 0f)
    }
}
