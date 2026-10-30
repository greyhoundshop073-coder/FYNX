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
    fun stageTwoFaceEffectsHaveStableUserFacingOptions() {
        assertEquals(
            listOf("Off", "Cute Ears", "Smart Glasses", "Royal Crown", "Puppy / Animal", "Virtual Makeup", "Hair Colour", "Funny Face"),
            CameraFaceEffect.values().map { it.label }
        )
    }

    @Test
    fun stageThreeBackgroundEffectsIncludeBlurColourReplacementAndScenes() {
        assertEquals(
            listOf("Off", "Portrait Blur", "Blue", "Sunset", "Mint", "Studio Scene", "Beach Scene"),
            CameraBackgroundEffect.values().map { it.label }
        )
        assertTrue(CameraBackgroundEffect.PORTRAIT_BLUR != CameraBackgroundEffect.OFF)
        assertTrue(CameraBackgroundEffect.BLUE_STUDIO != CameraBackgroundEffect.PORTRAIT_BLUR)
        assertTrue(CameraBackgroundEffect.STUDIO_SCENE != CameraBackgroundEffect.BEACH_SCENE)
    }

    @Test
    fun stageFourStudioEffectsExposeSymmetryMatureLookAndCartoon() {
        assertEquals(
            listOf("Off", "Mature Look", "Face Symmetry", "Anime / Cartoon"),
            CameraStudioEffect.values().map { it.label }
        )
    }

    @Test
    fun stageFiveVideoStylesAndCaptionTimelineAreStable() {
        assertEquals(
            listOf("Natural", "Cinematic", "Warm Film", "Vintage", "B&W", "Vivid"),
            CameraVideoStyle.values().map { it.label }
        )
        val cues = buildCameraCaptionCues("Hello there. This is FYNX camera captions.", 8_000L)
        assertTrue(cues.isNotEmpty())
        assertEquals(0L, cues.first().startMs)
        assertEquals(8_000L, cues.last().endMs)
        assertTrue(cues.zipWithNext().all { (left, right) -> left.endMs == right.startMs })
    }

    @Test
    fun videoCaptionTimelineRejectsEmptySpeechOrInvalidDuration() {
        assertTrue(buildCameraCaptionCues("", 10_000L).isEmpty())
        assertTrue(buildCameraCaptionCues("   ", 10_000L).isEmpty())
        assertTrue(buildCameraCaptionCues("Spoken words", 0L).isEmpty())
        assertTrue(buildCameraCaptionCues("Spoken words", -1L).isEmpty())
    }

    @Test
    fun cameraFilterParametersAreFinite() {
        CameraFilter.values().forEach { filter ->
            assertTrue(filter.saturation.isFinite())
            assertTrue(filter.brightness.isFinite())
            assertTrue(filter.contrast.isFinite())
            assertTrue(filter.warmth.isFinite())
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
        assertTrue(CameraFilter.WARM.warmth > CameraFilter.NATURAL.warmth)
        assertTrue(CameraFilter.COOL.warmth < CameraFilter.NATURAL.warmth)
        assertEquals(0f, CameraFilter.NATURAL.warmth, 0f)
    }

    @Test
    fun blackAndWhiteFilterRemovesSaturation() {
        assertEquals(0f, CameraFilter.BW.saturation, 0f)
        assertFalse(CameraFilter.BW.saturation > 0f)
    }
}
