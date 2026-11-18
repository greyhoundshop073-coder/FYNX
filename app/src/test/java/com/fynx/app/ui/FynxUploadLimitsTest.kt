package com.fynx.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class FynxUploadLimitsTest {
    private val uploadLimitBytes = 12L * 1024L * 1024L

    @Test
    fun recordedVideoStopsBeforeTheUploadLimit() {
        assertTrue(FYNX_UPLOAD_SAFE_VIDEO_BYTES in 1 until uploadLimitBytes)
    }

    @Test
    fun postVideoBitrateAllowsAUsefulClipLength() {
        val audioBitsPerSecond = 128_000L
        val seconds = FYNX_UPLOAD_SAFE_VIDEO_BYTES * 8L / (FYNX_UPLOAD_SAFE_VIDEO_BITRATE + audioBitsPerSecond)
        assertTrue("clip length was $seconds seconds", seconds >= 30L)
    }
}
