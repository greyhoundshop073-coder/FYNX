package com.fynx.app.ui

import android.media.MediaCodec
import android.media.MediaExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression guards for the existing camera/media capture contract. */
class CameraMediaReliabilityTest {
    @Test
    fun regularVideoDurationOptionsAre15SecondsOneMinuteThreeMinutesAndTenMinutes() {
        assertEquals(listOf(15_000L, 60_000L, 180_000L, 600_000L), FynxCameraRecordingDuration.videoOptions.map { it.first })
        assertEquals(listOf("15s", "60s", "3m", "10m"), FynxCameraRecordingDuration.videoOptions.map { it.second })
    }

    @Test
    fun regularVideoDurationDefaultsSafelyForUnsupportedSelections() {
        assertEquals(FynxCameraRecordingDuration.SIXTY_SECONDS_MS,
            FynxCameraRecordingDuration.maximumDurationMs(false, false, CameraMode.VIDEO, 7_000L))
    }

    @Test
    fun statusCaptureKeepsExistingNinetySecondMaximum() {
        assertEquals(FYNX_STATUS_MAX_VIDEO_DURATION_MS,
            FynxCameraRecordingDuration.maximumDurationMs(false, true, CameraMode.VIDEO, FynxCameraRecordingDuration.TEN_MINUTES_MS))
    }

    @Test
    fun videoNotesRemainLimitedToSixtySeconds() {
        assertEquals(FynxCameraRecordingDuration.SIXTY_SECONDS_MS,
            FynxCameraRecordingDuration.maximumDurationMs(true, false, CameraMode.VIDEO, FynxCameraRecordingDuration.TEN_MINUTES_MS))
    }

    @Test
    fun photoModeHasNoVideoDurationLimit() {
        assertEquals(null, FynxCameraRecordingDuration.maximumDurationMs(false, false, CameraMode.PHOTO, FynxCameraRecordingDuration.SIXTY_SECONDS_MS))
    }

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
        assertEquals(listOf("Natural", "Portrait", "Bright", "Warm", "Cool", "Vintage", "Cinematic", "Neon", "Fade", "Dream", "Negative", "B&W"), CameraFilter.values().map { it.label })
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
    fun negativeFilterIsMarkedForChannelInversion() {
        assertTrue(CameraFilter.NEGATIVE.invert)
        assertFalse(CameraFilter.NATURAL.invert)
    }

    @Test
    fun blackAndWhiteFilterRemovesSaturation() {
        assertEquals(0f, CameraFilter.BW.saturation, 0f)
        assertFalse(CameraFilter.BW.saturation > 0f)
    }

    @Test
    fun extractorSampleFlagsAreMappedToMuxerBufferFlags() {
        assertEquals(0, toMuxerBufferFlags(0))
        assertEquals(MediaCodec.BUFFER_FLAG_KEY_FRAME, toMuxerBufferFlags(MediaExtractor.SAMPLE_FLAG_SYNC))
        assertEquals(MediaCodec.BUFFER_FLAG_PARTIAL_FRAME, toMuxerBufferFlags(MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME))
    }

    @Test(expected = IllegalArgumentException::class)
    fun videoEditorRejectsEncryptedExtractorSamples() {
        toMuxerBufferFlags(MediaExtractor.SAMPLE_FLAG_ENCRYPTED)
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
    fun squareCropRectProducesOneToOneAspect() {
        val rect = calculateFynxCameraCropRect(4000, 3000, 1f)!!
        assertEquals(500, rect.left)
        assertEquals(0, rect.top)
        assertEquals(3000, rect.cropWidth)
        assertEquals(3000, rect.cropHeight)
    }

    @Test
    fun portraitCropRectProducesFourByFiveAspect() {
        val rect = calculateFynxCameraCropRect(4000, 3000, 4f / 5f)!!
        assertEquals(800, rect.left)
        assertEquals(0, rect.top)
        assertEquals(2400, rect.cropWidth)
        assertEquals(3000, rect.cropHeight)
    }

    @Test
    fun landscapeCropRectProducesSixteenByNineAspect() {
        val rect = calculateFynxCameraCropRect(4000, 3000, 16f / 9f)!!
        assertEquals(0, rect.left)
        assertEquals(375, rect.top)
        assertEquals(4000, rect.cropWidth)
        assertEquals(2250, rect.cropHeight)
    }

    @Test
    fun cropRectRejectsInvalidDimensionsAndAspectRatio() {
        assertEquals(null, calculateFynxCameraCropRect(0, 3000, 1f))
        assertEquals(null, calculateFynxCameraCropRect(4000, -1, 1f))
        assertEquals(null, calculateFynxCameraCropRect(4000, 3000, Float.NaN))
        assertEquals(null, calculateFynxCameraCropRect(4000, 3000, 0f))
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
