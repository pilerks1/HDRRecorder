package com.pilerks1.hdrrecorder.ui.camera.controls.slider

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerInputScope

internal typealias RangeSliderFinished = (
    ClosedFloatingPointRange<Float>, ClosedFloatingPointRange<Float>, FpsRangeBound, Float
) -> Unit

internal suspend fun PointerInputScope.detectRangeSliderTaps(
    interaction: RangeSliderInteraction,
    currentValue: () -> ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onFinished: RangeSliderFinished?
) {
    detectTapGestures(
        onTap = { offset ->
            val width = size.width.toFloat()
            val geometry = rangeSliderGeometry(
                width = width,
                reservedStart = interaction.reservedStart,
                reservedEnd = interaction.reservedEnd,
                thumbHalfWidth = interaction.thumbHalfWidth
            )
            if (offset.x !in geometry.axisStart..geometry.axisEnd) {
                return@detectTapGestures
            }

            val startingRange = currentValue()
            val thumb = interaction.thumbAt(
                positionX = offset.x,
                width = width,
                range = startingRange,
                deferOverlappingThumbs = false
            ) ?: return@detectTapGestures
            val releasedRange = interaction.rangeWithValue(
                range = startingRange,
                thumb = thumb,
                newValue = interaction.valueAt(offset.x, width)
            )
            val startingValue = when (thumb) {
                RangeThumb.START -> startingRange.start
                RangeThumb.END -> startingRange.endInclusive
            }
            val releasedValue = when (thumb) {
                RangeThumb.START -> releasedRange.start
                RangeThumb.END -> releasedRange.endInclusive
            }
            onValueChange(releasedRange)
            onFinished?.invoke(
                releasedRange,
                startingRange,
                thumb.toFpsRangeBound(),
                releasedValue - startingValue
            )
        }
    )

}

internal suspend fun PointerInputScope.detectRangeSliderDrags(
    interaction: RangeSliderInteraction,
    currentValue: () -> ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onFinished: RangeSliderFinished?
) {
    // Gesture-local; composition never reads these.
    var draggedThumb: RangeThumb? = null
    var gestureStartRange = currentValue()
    var latestGestureRange = currentValue()
    detectHorizontalDragGestures(
        onDragStart = { offset ->
            gestureStartRange = currentValue()
            latestGestureRange = currentValue()
            draggedThumb = interaction.thumbAt(
                positionX = offset.x,
                width = size.width.toFloat(),
                range = currentValue(),
                deferOverlappingThumbs = true
            )
        },
        onHorizontalDrag = { change, dragAmount ->
            change.consume()
            if (draggedThumb == null) {
                draggedThumb = when {
                    dragAmount < 0f -> RangeThumb.START
                    dragAmount > 0f -> RangeThumb.END
                    else -> null
                }
            }
            val baseRange = latestGestureRange
            val newValue = interaction.valueAt(change.position.x, size.width.toFloat())
            val updated = draggedThumb?.let { thumb ->
                interaction.rangeWithValue(baseRange, thumb, newValue)
            } ?: baseRange
            latestGestureRange = updated
            onValueChange(updated)
        },
        onDragEnd = {
            draggedThumb?.let { thumb ->
                val releasedRange = latestGestureRange
                val startingValue = when (thumb) {
                    RangeThumb.START -> gestureStartRange.start
                    RangeThumb.END -> gestureStartRange.endInclusive
                }
                val releasedValue = when (thumb) {
                    RangeThumb.START -> releasedRange.start
                    RangeThumb.END -> releasedRange.endInclusive
                }
                onFinished?.invoke(
                    releasedRange,
                    gestureStartRange,
                    thumb.toFpsRangeBound(),
                    releasedValue - startingValue
                )
            }
            draggedThumb = null
        },
        onDragCancel = { draggedThumb = null }
    )
}
