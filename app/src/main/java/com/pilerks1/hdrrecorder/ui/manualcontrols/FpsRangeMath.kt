package com.pilerks1.hdrrecorder.ui.manualcontrols

import android.util.Range
import kotlin.math.abs

/**
 * Specialized FPS range snapping; fixed-rate snapping lives in
 * [com.pilerks1.hdrrecorder.data.camera.core.FixedFrameRates] and standard slider math in [SliderScale].
 */
internal object FpsRangeMath {
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
        // validRanges is nonempty, so every selection below has at least one candidate.
        val supportedEndpoints = validRanges
            .map { range ->
                when (draggedBound) {
                    FpsRangeBound.LOWER -> range.lower
                    FpsRangeBound.UPPER -> range.upper
                }
            }
            .distinct()
            .sorted()
        val minimumDistance = supportedEndpoints.minOf { endpoint -> abs(endpoint - releasedValue) }
        // Endpoints are integers, so equidistant endpoints have exactly equal float distances.
        val nearestEndpoints = supportedEndpoints.filter { endpoint -> abs(endpoint - releasedValue) == minimumDistance }
        val snappedEndpoint = when {
            nearestEndpoints.size == 1 -> nearestEndpoints.first()
            dragDirection > 0f -> nearestEndpoints.last()
            dragDirection < 0f -> nearestEndpoints.first()
            // Sorted ascending, so the first minimum is also the lowest on a tie.
            else -> nearestEndpoints.minBy { endpoint -> abs(endpoint - startingValue) }
        }
        val stationaryValue = when (draggedBound) {
            FpsRangeBound.LOWER -> newRange.endInclusive
            FpsRangeBound.UPPER -> newRange.start
        }
        val bestMatch = validRanges
            .filter { range ->
                when (draggedBound) {
                    FpsRangeBound.LOWER -> range.lower == snappedEndpoint
                    FpsRangeBound.UPPER -> range.upper == snappedEndpoint
                }
            }
            .minWith(
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
            )

        return bestMatch.lower.toFloat()..bestMatch.upper.toFloat()
    }
}

internal enum class FpsRangeBound {
    LOWER,
    UPPER
}
