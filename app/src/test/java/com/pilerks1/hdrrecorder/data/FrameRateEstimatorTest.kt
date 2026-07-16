package com.pilerks1.hdrrecorder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameRateEstimatorTest {
    @Test
    fun manualFpsReportsActualRateWithoutDroppedFrames() {
        val sample = FrameRateEstimator.sample(frameCount = 24, durationNanos = 1_000_000_000L, targetFps = null)

        assertEquals(24, sample.effectiveFps)
        assertNull(sample.droppedFrames)
    }

    @Test
    fun requestedFpsReportsFramesBelowTheRequestedRate() {
        val sample = FrameRateEstimator.sample(frameCount = 24, durationNanos = 1_000_000_000L, targetFps = 30)

        assertEquals(24, sample.effectiveFps)
        assertEquals(6, sample.droppedFrames)
    }
}
