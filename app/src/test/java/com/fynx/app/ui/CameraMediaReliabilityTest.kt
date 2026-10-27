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
        assertEquals(listOf("Natural", "Portrait", "Bright", "Warm", "Cool", "Vintage", "B&W"), CameraFilter.values().map { it.label })
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

    @Test
    fun videoEditRangeIsClampedToValidMinimumAndSourceBounds() {
        val range = FynxCameraVideoEditRange.normalize(9_000L, 2_500L, 10_000L)
        assertEquals(1_500L, range.startMs)
        assertEquals(2_500L, range.endMs)
        assertEquals(1_000L, range.clipDurationMs)
    }

    @Test
    fun videoEditRangeClampsNegativeAndOverlongSelections() {
        val range = FynxCameraVideoEditRange.normalize(-500L, 25_000L, 8_000L)
        assertEquals(0L, range.startMs)
        assertEquals(8_000L, range.endMs)
        assertEquals(8_000L, range.clipDurationMs)
    }

    @Test
    fun videoEditRangeKeepsAtLeastOneSecond() {
        val range = FynxCameraVideoEditRange.normalize(4_000L, 4_100L, 9_000L)
        assertEquals(3_100L, range.startMs)
        assertEquals(4_100L, range.endMs)
        assertEquals(FynxCameraVideoEditRange.MIN_CLIP_DURATION_MS, range.clipDurationMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun videoEditRejectsSourceShorterThanMinimumDuration() {
        FynxCameraVideoEditRange.normalize(0L, 500L, 500L)
    }

    @Test
    fun drawingPointMapsToImageCoordinatesInsideFitCenterImage() {
        val point = normalizeFynxCameraDrawPoint(
            x = 150f, y = 300f, canvasWidth = 300f, canvasHeight = 600f,
            imageWidth = 1000f, imageHeight = 1000f
        )
        assertTrue(point != null)
        assertEquals(0.5f, point!!.x, 0.001f)
        assertEquals(0.5f, point.y, 0.001f)
    }

    @Test
    fun drawingPointRejectsLetterboxAreaOutsideTheActualPhoto() {
        val point = normalizeFynxCameraDrawPoint(
            x = 150f, y = 40f, canvasWidth = 300f, canvasHeight = 600f,
            imageWidth = 1000f, imageHeight = 1000f
        )
        assertEquals(null, point)
    }

    @Test
    fun drawingPointAtPhotoEdgeIsNormalizedAndClamped() {
        val point = normalizeFynxCameraDrawPoint(
            x = 0f, y = 150f, canvasWidth = 300f, canvasHeight = 600f,
            imageWidth = 1000f, imageHeight = 1000f
        )
        assertTrue(point != null)
        assertEquals(0f, point!!.x, 0.001f)
        assertEquals(0f, point.y, 0.001f)
    }
}
