package com.pilerks1.hdrrecorder.ui.camera.controls.slider

import kotlin.math.abs

internal data class RangeSliderGeometry(
    val axisStart: Float,
    val axisEnd: Float
) {
    private val axisLength: Float
        get() = (axisEnd - axisStart).coerceAtLeast(0f)

    fun positionAt(fraction: Float): Float =
        axisStart + axisLength * fraction.coerceIn(0f, 1f)

    fun fractionAt(positionX: Float): Float =
        ((positionX - axisStart) / axisLength.coerceAtLeast(1f)).coerceIn(0f, 1f)
}

internal fun rangeSliderGeometry(
    width: Float,
    reservedStart: Float,
    reservedEnd: Float,
    thumbHalfWidth: Float
): RangeSliderGeometry {
    val axisStart = (reservedStart + thumbHalfWidth).coerceAtMost(width / 2f)
    val axisEnd = (width - reservedEnd - thumbHalfWidth).coerceAtLeast(axisStart)
    return RangeSliderGeometry(axisStart, axisEnd)
}

internal fun valueFraction(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>
): Float {
    val length = valueRange.endInclusive - valueRange.start
    return if (length == 0f) 0.5f else (value - valueRange.start) / length
}

/** Immutable coordinate context shared by pointer input and accessibility. */
internal class RangeSliderInteraction(
    private val valueRange: ClosedFloatingPointRange<Float>,
    val reservedStart: Float,
    val reservedEnd: Float,
    val thumbHalfWidth: Float,
    private val singleThumb: Boolean
) {
    fun valueAt(positionX: Float, width: Float): Float {
        val geometry = rangeSliderGeometry(
            width = width,
            reservedStart = reservedStart,
            reservedEnd = reservedEnd,
            thumbHalfWidth = thumbHalfWidth
        )
        val fraction = geometry.fractionAt(positionX)
        return valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
    }

    fun thumbAt(
        positionX: Float,
        width: Float,
        range: ClosedFloatingPointRange<Float>,
        deferOverlappingThumbs: Boolean
    ): RangeThumb? {
        if (singleThumb) return RangeThumb.END
        if (range.start != range.endInclusive) {
            val touchedValue = valueAt(positionX, width)
            return if (
                abs(touchedValue - range.start) <=
                abs(touchedValue - range.endInclusive)
            ) RangeThumb.START else RangeThumb.END
        }

        val geometry = rangeSliderGeometry(
            width = width,
            reservedStart = reservedStart,
            reservedEnd = reservedEnd,
            thumbHalfWidth = thumbHalfWidth
        )
        val sharedPosition = geometry.positionAt(valueFraction(range.start, valueRange))
        return when {
            positionX < sharedPosition - thumbHalfWidth -> RangeThumb.START
            positionX > sharedPosition + thumbHalfWidth -> RangeThumb.END
            deferOverlappingThumbs -> null
            positionX < sharedPosition -> RangeThumb.START
            else -> RangeThumb.END
        }
    }

    fun rangeWithValue(
        range: ClosedFloatingPointRange<Float>,
        thumb: RangeThumb,
        newValue: Float
    ): ClosedFloatingPointRange<Float> {
        if (singleThumb) return newValue..newValue
        return when (thumb) {
            RangeThumb.START -> newValue.coerceAtMost(range.endInclusive)..range.endInclusive
            RangeThumb.END -> range.start..newValue.coerceAtLeast(range.start)
        }
    }

}

internal enum class RangeThumb {
    START,
    END
}

internal fun RangeThumb.toFpsRangeBound(): FpsRangeBound = when (this) {
    RangeThumb.START -> FpsRangeBound.LOWER
    RangeThumb.END -> FpsRangeBound.UPPER
}
