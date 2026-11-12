package com.pilerks1.hdrrecorder.ui.camera.controls.slider

import androidx.compose.foundation.layout.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import com.pilerks1.hdrrecorder.ui.style.SliderDimensions

internal data class RibbonTickLayout(
    val fraction: Float,
    val label: TextLayoutResult?,
    val isMarker: Boolean,
    val auxiliaryLabel: Boolean
)

internal data class RibbonAxisGeometry(
    val axisStart: Float,
    val axisEnd: Float
)

internal fun ribbonAxisGeometry(
    width: Float,
    edgePadding: Float,
    reservedStart: Float,
    reservedEnd: Float
): RibbonAxisGeometry {
    val axisStart = maxOf(edgePadding, reservedStart).coerceAtMost(width / 2f)
    val axisEnd = minOf(width - edgePadding, width - reservedEnd).coerceAtLeast(axisStart)
    return RibbonAxisGeometry(axisStart, axisEnd)
}

private fun ribbonTickLabelTop(
    tickBottom: Float,
    labelPadding: Float,
    labelWidth: Float,
    labelHeight: Float,
    textRotation: Float
): Float = if (textRotation == 0f) {
    tickBottom + labelPadding
} else {
    tickBottom + labelPadding + labelWidth / 2f - labelHeight / 2f
}

internal data class RibbonDrawingMetrics(
    val axisEdgePaddingPx: Float,
    val axisReservedStartPx: Float,
    val axisReservedEndPx: Float,
    val standardPaneEdgePaddingPx: Float,
    val sliderTopExtentPx: Float,
    val virtualLengthPx: Float,
    val labelGapPx: Float,
    val tickStartYOffsetPx: Float,
    val tickHeightPx: Float,
    val tickToTextPaddingPx: Float,
    val centerIndicatorHalfHeightPx: Float
)

internal fun DrawScope.drawRibbonSlider(
    value: Float,
    textRotation: Float,
    tickLayouts: List<RibbonTickLayout>,
    drawnAuxiliaryLabels: ArrayList<ClosedFloatingPointRange<Float>>,
    metrics: RibbonDrawingMetrics
) {
    val axisGeometry = ribbonAxisGeometry(
        width = size.width,
        edgePadding = metrics.axisEdgePaddingPx,
        reservedStart = metrics.axisReservedStartPx,
        reservedEnd = metrics.axisReservedEndPx
    )
    val centerX = (axisGeometry.axisStart + axisGeometry.axisEnd) / 2f
    val centerY = metrics.standardPaneEdgePaddingPx + metrics.sliderTopExtentPx
    val totalPanDistancePx = metrics.virtualLengthPx

    clipRect(left = axisGeometry.axisStart, right = axisGeometry.axisEnd) {
        fun labelHalfExtent(label: TextLayoutResult): Float =
            (if (textRotation == 0f) label.size.width else label.size.height) / 2f
        fun overlaps(start: Float, end: Float, other: ClosedFloatingPointRange<Float>): Boolean =
            start < other.endInclusive + metrics.labelGapPx && end + metrics.labelGapPx > other.start
        // Auxiliary labels yield to regular labels and to auxiliary labels already drawn.
        fun collides(start: Float, end: Float): Boolean =
            tickLayouts.any { tick ->
                val label = tick.label
                if (tick.auxiliaryLabel || label == null) return@any false
                val x = centerX + (tick.fraction - value) * totalPanDistancePx
                val half = labelHalfExtent(label)
                overlaps(start, end, (x - half)..(x + half))
            } || drawnAuxiliaryLabels.any { overlaps(start, end, it) }
        drawnAuxiliaryLabels.clear()
        tickLayouts.forEach { tick ->
            val tickX = centerX + (tick.fraction - value) * totalPanDistancePx
            if (tickX < axisGeometry.axisStart || tickX > axisGeometry.axisEnd) return@forEach
            val tickTop = centerY + metrics.tickStartYOffsetPx
            drawLine(
                color = if (tick.isMarker) Color.Yellow else Color.White,
                start = Offset(tickX, tickTop),
                end = Offset(tickX, tickTop + metrics.tickHeightPx),
                strokeWidth = (if (tick.isMarker) SliderDimensions.markerStrokeWidth else SliderDimensions.tickStrokeWidth).toPx()
            )

            tick.label?.let { label ->
                val labelTop = ribbonTickLabelTop(
                    tickBottom = tickTop + metrics.tickHeightPx,
                    labelPadding = metrics.tickToTextPaddingPx,
                    labelWidth = label.size.width.toFloat(),
                    labelHeight = label.size.height.toFloat(),
                    textRotation = textRotation
                )
                val topLeft = Offset(
                    x = tickX - label.size.width / 2f,
                    y = labelTop
                )
                val half = labelHalfExtent(label)
                val start = tickX - half
                val end = tickX + half
                if (
                    !(tick.auxiliaryLabel && collides(start, end)) &&
                    start >= axisGeometry.axisStart && end <= axisGeometry.axisEnd
                ) {
                    if (tick.auxiliaryLabel) drawnAuxiliaryLabels.add(start..end)
                    rotate(
                        degrees = textRotation,
                        pivot = Offset(tickX, topLeft.y + label.size.height / 2f)
                    ) {
                        drawText(label, topLeft = topLeft)
                    }
                }
            }
        }
    }

    drawLine(
        color = Color.White,
        start = Offset(centerX, centerY - metrics.centerIndicatorHalfHeightPx),
        end = Offset(centerX, centerY + metrics.centerIndicatorHalfHeightPx),
        strokeWidth = SliderDimensions.centerIndicatorStrokeWidth.toPx()
    )
}
