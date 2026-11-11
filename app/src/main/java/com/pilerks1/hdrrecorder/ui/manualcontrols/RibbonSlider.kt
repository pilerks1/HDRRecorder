package com.pilerks1.hdrrecorder.ui.manualcontrols

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import com.pilerks1.hdrrecorder.ui.layout.AxisSpec

private val ClipXAxisShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Rectangle(
            Rect(
                left = 0f,
                top = -1000f, // Don't clip Y (which is the preview/black bar direction)
                right = size.width,
                bottom = size.height + 1000f
            )
        )
    }
}

/** Gesture-owned fling handle; composition never reads it. */
private class FlingHandle {
    var job: Job? = null
}

private data class RibbonTickLayout(
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

internal fun ribbonTickLabelTop(
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

/**
 * @param modeKey changing this (for example, Auto to manual) cancels a fling in progress.
 */
@Composable
fun RibbonSlider(
    value: Float, // 0f..1f
    onValueChange: (Float) -> Unit,
    axis: AxisSpec,
    ruler: SliderRuler,
    layoutLabels: List<String>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onValueChangeFinished: () -> Unit = {},
    enabled: Boolean = true,
    labelString: String = "",
    modeKey: Any? = null,
    axisReservation: SliderAxisReservation = SliderAxisReservation()
) {
    // ==========================================
    // RIBBON SLIDER INTERNAL LAYOUT CONTROLS
    // ==========================================
    val tickToTextPadding = SliderPaneStyle.standardElementGap
    val tickStartYOffset = SliderPaneStyle.centerIndicatorHalfHeight + SliderPaneStyle.standardElementGap
    // ==========================================

    val ticks = ruler.ticks
    val textRotation = axis.contentCounterRotationDegrees

    val density = LocalDensity.current
    val tickStartYOffsetPx = with(density) { tickStartYOffset.toPx() }
    val tickToTextPaddingPx = with(density) { tickToTextPadding.toPx() }
    val virtualLengthPx = with(density) { SliderPaneStyle.rulerTickSpacing.toPx() } * ruler.intervalCount
    val labelGapPx = with(density) { SliderPaneStyle.rulerLabelGap.toPx() }
    val tickHeightPx = with(density) { SliderPaneStyle.sliderTickHeight.toPx() }
    val axisEdgePaddingPx = with(density) { SliderPaneStyle.sliderAxisEdgePadding.toPx() }
    val axisReservedStartPx = with(density) { axisReservation.start.toPx() }
    val axisReservedEndPx = with(density) { axisReservation.end.toPx() }
    val centerIndicatorHalfHeightPx = with(density) { SliderPaneStyle.centerIndicatorHalfHeight.toPx() }
    val standardElementGapPx = with(density) { SliderPaneStyle.standardElementGap.toPx() }
    val standardPaneEdgePaddingPx = with(density) { SliderPaneStyle.standardPaneEdgePadding.toPx() }
    val tickTextStyle = remember { SliderPaneStyle.tickTextStyle.copy(color = Color.Gray) }
    val liveTextStyle = remember { SliderPaneStyle.liveReadoutTextStyle.copy(color = Color.White) }
    val textMeasurer = rememberTextMeasurer()
    val tickLayouts = remember(ruler, density, textMeasurer) {
        fun layout(tick: SliderTick, isMarker: Boolean, auxiliaryLabel: Boolean) = RibbonTickLayout(
            fraction = tick.position,
            label = tick.label.takeIf(String::isNotEmpty)?.let {
                textMeasurer.measure(AnnotatedString(it), style = tickTextStyle)
            },
            isMarker = isMarker,
            auxiliaryLabel = auxiliaryLabel
        )
        fun coincides(first: SliderTick, second: SliderTick): Boolean =
            abs(first.position - second.position) * ruler.intervalCount < 0.00001f
        val regular = ticks.map { tick ->
            val marker = ruler.markers.firstOrNull { coincides(tick, it) }
            layout(
                tick = if (tick.label.isEmpty() && marker != null) marker else tick,
                isMarker = marker != null,
                auxiliaryLabel = tick.label.isEmpty() && marker != null
            )
        }
        val markers = ruler.markers.filter { marker -> ticks.none { coincides(it, marker) } }
            .map { layout(it, isMarker = true, auxiliaryLabel = true) }
        (regular + markers).sortedBy(RibbonTickLayout::fraction)
    }
    // Draw-time scratch storage for auxiliary label bounds; reused instead of allocated per frame.
    val drawnAuxiliaryLabels = remember { ArrayList<ClosedFloatingPointRange<Float>>() }
    val maximumLiveLabelSize = remember(layoutLabels, density, textMeasurer) {
        layoutLabels.maxMeasuredSize(textMeasurer, liveTextStyle)
    }
    val sliderTopExtentPx = standardSliderTopExtentPx(
        axis = axis,
        liveLabelWidthPx = maximumLiveLabelSize.width.toFloat(),
        liveLabelHeightPx = maximumLiveLabelSize.height.toFloat(),
        dpToPx = { with(density) { it.toPx() } }
    )
    val liveLabelCrossExtentPx = if (axis.usesVerticalTrack) {
        maximumLiveLabelSize.width.toFloat()
    } else {
        maximumLiveLabelSize.height.toFloat()
    }
    val liveLabelRelativeCenterPx = -(
        centerIndicatorHalfHeightPx + standardElementGapPx + liveLabelCrossExtentPx / 2f
        )
    val maximumLiveLabelWidth = with(density) { maximumLiveLabelSize.width.toDp() }

    val currentVal by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    val scope = rememberCoroutineScope()
    // The animatable only drives flings; bounds keep decay inside the slider range.
    val animatable = remember { Animatable(value).apply { updateBounds(0f, 1f) } }
    val fling = remember { FlingHandle() }
    DisposableEffect(modeKey, enabled, ruler) {
        onDispose { fling.job?.cancel() }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .rotateForAxis(axis)
                .fillMaxSize()
                .clip(ClipXAxisShape)
                .semantics {
                    this.contentDescription = contentDescription
                    stateDescription = labelString
                    progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 1f), 0f..1f)
                    if (enabled) {
                        setProgress { target ->
                            currentOnValueChange(target.coerceIn(0f, 1f))
                            currentOnValueChangeFinished()
                            true
                        }
                    } else disabled()
                }
                .pointerInput(enabled, axisReservation, ruler, virtualLengthPx) {
                    if (!enabled || ruler.intervalCount <= 0f) return@pointerInput
                    val velocityTracker = VelocityTracker()
                    val trackLengthPx = virtualLengthPx.coerceAtLeast(1f)
                    var dragAccumulator = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            fling.job?.cancel()
                            velocityTracker.resetTracking()
                            dragAccumulator = currentVal
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            // Clamped so reversing at an endpoint responds immediately.
                            dragAccumulator = (dragAccumulator - dragAmount / trackLengthPx).coerceIn(0f, 1f)
                            currentOnValueChange(dragAccumulator)
                        },
                        onDragEnd = {
                            val velocityInValue = -(velocityTracker.calculateVelocity().x / trackLengthPx)
                            fling.job = scope.launch {
                                animatable.snapTo(dragAccumulator)
                                animatable.animateDecay(
                                    initialVelocity = velocityInValue,
                                    animationSpec = exponentialDecay(frictionMultiplier = 2f)
                                ) { currentOnValueChange(this.value) }

                                val finalValue = animatable.value
                                val closestTick = ticks.minByOrNull { abs(it.position - finalValue) }
                                if (closestTick != null && abs(finalValue - closestTick.position) < 0.01f) {
                                    animatable.animateTo(
                                        targetValue = closestTick.position,
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                    ) { currentOnValueChange(this.value) }
                                }
                                currentOnValueChangeFinished()
                            }
                        },
                        onDragCancel = { currentOnValueChangeFinished() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val axisGeometry = ribbonAxisGeometry(
                    width = size.width,
                    edgePadding = axisEdgePaddingPx,
                    reservedStart = axisReservedStartPx,
                    reservedEnd = axisReservedEndPx
                )
                val centerX = (axisGeometry.axisStart + axisGeometry.axisEnd) / 2f
                val centerY = standardPaneEdgePaddingPx + sliderTopExtentPx
                val totalPanDistancePx = virtualLengthPx

                clipRect(left = axisGeometry.axisStart, right = axisGeometry.axisEnd) {
                    fun labelHalfExtent(label: TextLayoutResult): Float =
                        (if (textRotation == 0f) label.size.width else label.size.height) / 2f
                    fun overlaps(start: Float, end: Float, other: ClosedFloatingPointRange<Float>): Boolean =
                        start < other.endInclusive + labelGapPx && end + labelGapPx > other.start
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
                        val tickTop = centerY + tickStartYOffsetPx
                        drawLine(
                            color = if (tick.isMarker) Color.Yellow else Color.White,
                            start = Offset(tickX, tickTop),
                            end = Offset(tickX, tickTop + tickHeightPx),
                            strokeWidth = (if (tick.isMarker) 2.dp else 1.dp).toPx()
                        )

                        tick.label?.let { label ->
                            val labelTop = ribbonTickLabelTop(
                                tickBottom = tickTop + tickHeightPx,
                                labelPadding = tickToTextPaddingPx,
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
                    start = Offset(centerX, centerY - centerIndicatorHalfHeightPx),
                    end = Offset(centerX, centerY + centerIndicatorHalfHeightPx),
                    strokeWidth = 2.dp.toPx()
                )
            }

            if (labelString.isNotEmpty()) {
                Text(
                    text = labelString,
                    style = liveTextStyle,
                    maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        // Offsets depend on the slider surface size (the incoming constraints), so
                        // they are resolved during layout instead of feeding a size back into composition.
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val surfaceWidth = constraints.maxWidth.toFloat()
                            val surfaceHeight = constraints.maxHeight.toFloat()
                            val geometry = ribbonAxisGeometry(
                                width = surfaceWidth,
                                edgePadding = axisEdgePaddingPx,
                                reservedStart = axisReservedStartPx,
                                reservedEnd = axisReservedEndPx
                            )
                            val x = (geometry.axisStart + geometry.axisEnd) / 2f - surfaceWidth / 2f
                            val y = standardPaneEdgePaddingPx + sliderTopExtentPx - surfaceHeight / 2f +
                                liveLabelRelativeCenterPx
                            layout(placeable.width, placeable.height) {
                                placeable.placeRelative(x.roundToInt(), y.roundToInt())
                            }
                        }
                        .width(maximumLiveLabelWidth)
                        .graphicsLayer { rotationZ = textRotation }
                )
            }
        }
    }
}
