package com.pilerks1.hdrrecorder.ui.manualcontrols

import android.util.Range
import kotlin.math.abs
import com.pilerks1.hdrrecorder.data.camera.core.FixedFrameRates

/** Specialized FPS range and fixed-rate snapping; standard slider math lives in [SliderScale]. */
internal object FpsRangeMath {
    fun fixedFrameRates(validRanges: List<Range<Int>>): List<Int> = FixedFrameRates.fixedFrameRates(validRanges)

    fun nearestFixedFrameRate(requestedFps: Int, validRanges: List<Range<Int>>): Int? =
        FixedFrameRates.nearestFixedFrameRate(requestedFps, validRanges)

    fun nearestFixedFrameRateForShutter(shutterNanos: Long, validRanges: List<Range<Int>>): Int? =
        FixedFrameRates.nearestFixedFrameRateForShutter(shutterNanos, validRanges)

    fun clampToSupportedRange(
        newRange: ClosedFloatingPointRange<Float>,
        oldRange: ClosedFloatingPointRange<Float>,
        draggedBound: FpsRangeBound,
        dragDirection: Float,
        validRanges: List<Range<Int>>
    ): ClosedFloatingPointRange<Float> {
        if (validRanges.isEmpty()) return newRange

        val releasedValue = when (draggedBound) {
            FpsRangeBound.LOWER -> newRange.start
            FpsRangeBound.UPPER -> newRange.endInclusive
        }
        val startingValue = when (draggedBound) {
            FpsRangeBound.LOWER -> oldRange.start
            FpsRangeBound.UPPER -> oldRange.endInclusive
        }
        val supportedEndpoints = validRanges
            .map { range ->
                when (draggedBound) {
                    FpsRangeBound.LOWER -> range.lower
                    FpsRangeBound.UPPER -> range.upper
                }
            }
            .distinct()
            .sorted()
        val minimumDistance = supportedEndpoints.minOfOrNull { endpoint ->
            abs(endpoint - releasedValue)
        } ?: return newRange
        val nearestEndpoints = supportedEndpoints.filter { endpoint ->
            abs(abs(endpoint - releasedValue) - minimumDistance) <= SNAP_TIE_EPSILON
        }
        val snappedEndpoint = when {
            nearestEndpoints.size == 1 -> nearestEndpoints.first()
            dragDirection > 0f -> nearestEndpoints.last()
            dragDirection < 0f -> nearestEndpoints.first()
            else -> nearestEndpoints.minWithOrNull(
                compareBy<Int> { endpoint -> abs(endpoint - startingValue) }
                    .thenBy { endpoint -> endpoint }
            ) ?: return newRange
        }
        val candidates = validRanges.filter { range ->
            when (draggedBound) {
                FpsRangeBound.LOWER -> range.lower == snappedEndpoint
                FpsRangeBound.UPPER -> range.upper == snappedEndpoint
            }
        }
        val stationaryValue = when (draggedBound) {
            FpsRangeBound.LOWER -> newRange.endInclusive
            FpsRangeBound.UPPER -> newRange.start
        }
        val bestMatch = candidates.minWithOrNull(
            compareBy<Range<Int>> { range ->
                val otherEndpoint = when (draggedBound) {
                    FpsRangeBound.LOWER -> range.upper
                    FpsRangeBound.UPPER -> range.lower
                }
                abs(otherEndpoint - stationaryValue)
            }
                .thenBy { range ->
                    if (
                        range.lower.toFloat() == oldRange.start &&
                        range.upper.toFloat() == oldRange.endInclusive
                    ) 0 else 1
                }
                .thenBy { range -> range.lower }
                .thenBy { range -> range.upper }
        ) ?: return newRange

        return bestMatch.lower.toFloat()..bestMatch.upper.toFloat()
    }

    private const val SNAP_TIE_EPSILON = 0.0001f
}

internal enum class FpsRangeBound {
    LOWER,
    UPPER
}
