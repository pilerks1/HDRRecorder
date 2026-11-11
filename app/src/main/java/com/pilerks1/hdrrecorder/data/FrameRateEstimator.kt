package com.pilerks1.hdrrecorder.data

import com.pilerks1.hdrrecorder.model.NANOS_PER_SECOND

/** Pure, target-optional frame-rate math used by [StatsManager]. */
internal object FrameRateEstimator {
    data class Sample(val effectiveFps: Int, val droppedFrames: Int?)

    fun sample(frameCount: Int, durationNanos: Long, targetFps: Int?): Sample {
        require(durationNanos > 0L)
        val effectiveFps = ((frameCount * NANOS_PER_SECOND) / durationNanos).toInt()
        val droppedFrames = targetFps?.let { target ->
            val expectedFrames = (target * durationNanos / NANOS_PER_SECOND).toInt()
            (expectedFrames - frameCount).coerceAtLeast(0)
        }
        return Sample(effectiveFps, droppedFrames)
    }
}
