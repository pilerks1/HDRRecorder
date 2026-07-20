package com.pilerks1.hdrrecorder.ui.manualcontrols

import android.util.Range
import kotlin.math.abs
import kotlin.math.roundToInt

/** Specialized two-thumb FPS snapping; standard slider math lives in [SliderScale]. */
object FpsRangeMath {
    fun clampToSupportedRange(
        newRange: ClosedFloatingPointRange<Float>,
        oldRange: ClosedFloatingPointRange<Float>,
        validRanges: List<Range<Int>>
    ): ClosedFloatingPointRange<Float> {
        if (validRanges.isEmpty()) return newRange

        val newStart = newRange.start.roundToInt()
        val newEnd = newRange.endInclusive.roundToInt()
        val draggedStart = newStart != oldRange.start.roundToInt()
        val anchoredMatches = validRanges.filter { range ->
            if (draggedStart) range.lower == newStart else range.upper == newEnd
        }
        val candidates = anchoredMatches.ifEmpty { validRanges }
        val bestMatch = candidates.minByOrNull { range ->
            if (draggedStart && anchoredMatches.isNotEmpty()) {
                abs(range.upper - newEnd)
            } else if (!draggedStart && anchoredMatches.isNotEmpty()) {
                abs(range.lower - newStart)
            } else {
                abs(range.lower - newStart) + abs(range.upper - newEnd)
            }
        } ?: return newRange

        return bestMatch.lower.toFloat()..bestMatch.upper.toFloat()
    }
}
