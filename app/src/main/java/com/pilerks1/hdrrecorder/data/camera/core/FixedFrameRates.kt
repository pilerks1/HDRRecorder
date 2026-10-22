package com.pilerks1.hdrrecorder.data.camera.core

import android.util.Range
import kotlin.math.abs
import kotlin.math.floor

/** Fixed-rate exposure constraints shared by capture policies and UI snapping. */
object FixedFrameRates {
    fun fixedFrameRates(validRanges: List<Range<Int>>): List<Int> =
        validRanges
            .filter { it.lower == it.upper }
            .map { it.lower }
            .distinct()
            .sorted()

    fun nearestFixedFrameRate(
        requestedFps: Int,
        validRanges: List<Range<Int>>
    ): Int? = fixedFrameRates(validRanges)
        .minWithOrNull(
            compareBy<Int> { fps -> abs(fps - requestedFps) }
                .thenBy { fps -> fps }
        )

    fun nearestFixedFrameRateForShutter(
        shutterNanos: Long,
        validRanges: List<Range<Int>>
    ): Int? {
        if (shutterNanos <= 0L) return null
        val fixedRates = fixedFrameRates(validRanges)
        if (fixedRates.isEmpty()) return null

        val shutterLimitedFps = NANOS_PER_SECOND.toDouble() / shutterNanos.toDouble()
        val maximumWholeFps = floor(shutterLimitedFps).toInt().coerceAtLeast(1)
        val achievableRates = fixedRates.filter { it <= maximumWholeFps }
        val candidates = achievableRates.ifEmpty { fixedRates }
        return candidates.minWithOrNull(
            compareBy<Int> { fps -> abs(fps - shutterLimitedFps) }
                .thenByDescending { fps -> fps }
        )
    }

    private const val NANOS_PER_SECOND = 1_000_000_000L
}
