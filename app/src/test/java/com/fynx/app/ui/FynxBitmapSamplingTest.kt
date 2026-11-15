package com.fynx.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FynxBitmapSamplingTest {
    @Test
    fun computeInSampleSizeKeepsDecodedAxesWithinConfiguredBound() {
        val sample = FynxBitmapSampling.computeInSampleSize(6000, 4000, 1600)
        assertEquals(4, sample)
        assertTrue(FynxBitmapSampling.sampledDimension(6000, sample) <= 1600)
        assertTrue(FynxBitmapSampling.sampledDimension(4000, sample) <= 1600)
    }

    @Test
    fun sampledDimensionUsesCeilingDivisionAndHandlesInvalidBounds() {
        assertEquals(0, FynxBitmapSampling.sampledDimension(0, 2))
        assertEquals(0, FynxBitmapSampling.sampledDimension(-10, 2))
        assertEquals(5, FynxBitmapSampling.sampledDimension(9, 2))
        assertEquals(9, FynxBitmapSampling.sampledDimension(9, 0))
        assertTrue(FynxBitmapSampling.sampledDimension(Int.MAX_VALUE, 2) > 0)
    }

    @Test
    fun fitWithinBoundsScalesProportionallyAndPreservesSmallerImages() {
        assertEquals(800 to 600, FynxBitmapSampling.fitWithinBounds(800, 600, 1600))
        assertEquals(1600 to 800, FynxBitmapSampling.fitWithinBounds(3200, 1600, 1600))
        assertEquals(1 to 1, FynxBitmapSampling.fitWithinBounds(0, 500, 1600))
    }
}
