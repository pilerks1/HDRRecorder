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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
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

private data class RibbonTickLayout(
    val fraction: Float,
    val label: TextLayoutResult?
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

@Composable
fun RibbonSlider(
    value: Float, // 0f..1f
    onValueChange: (Float) -> Unit,
    axis: AxisSpec,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    labelString: String = "",
    ticks: List<SliderTick>,
    layoutLabels: List<String>,
    axisReservation: SliderAxisReservation = SliderAxisReservation()
) {
    // ==========================================
    // RIBBON SLIDER INTERNAL LAYOUT CONTROLS
    // ==========================================
    val tickToTextPadding = SliderPaneStyle.standardElementGap
    val tickStartYOffset = SliderPaneStyle.centerIndicatorHalfHeight + SliderPaneStyle.standardElementGap
    // ==========================================

    val textRotation = axis.contentCounterRotationDegrees
    
    val density = LocalDensity.current
    val tickStartYOffsetPx = with(density) { tickStartYOffset.toPx() }
    val tickToTextPaddingPx = with(density) { tickToTextPadding.toPx() }
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
    val tickLayouts = remember(ticks, density, textMeasurer) {
        ticks.map { tick ->
            RibbonTickLayout(
                fraction = tick.position,
                label = tick.label.takeIf(String::isNotEmpty)?.let {
                    textMeasurer.measure(AnnotatedString(it), style = tickTextStyle)
                }
            )
        }
    }
    val maximumLiveLabelSize = remember(layoutLabels, density, textMeasurer) {
        layoutLabels.fold(IntSize.Zero) { maximum, label ->
            val size = textMeasurer.measure(AnnotatedString(label), style = liveTextStyle).size
            IntSize(
                width = maxOf(maximum.width, size.width),
                height = maxOf(maximum.height, size.height)
            )
        }
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
    
    val scope = rememberCoroutineScope()
    val animatable = remember { Animatable(value) }
    val velocityTracker = remember { VelocityTracker() }
    
    var dragAccumulator by remember { mutableFloatStateOf(value) }
    
    val sliderContent = @Composable {
        var sliderSurfaceSize by remember { mutableStateOf(IntSize.Zero) }
        val labelPrimaryOffset = if (sliderSurfaceSize.width > 0) {
            val geometry = ribbonAxisGeometry(
                width = sliderSurfaceSize.width.toFloat(),
                edgePadding = axisEdgePaddingPx,
                reservedStart = axisReservedStartPx,
                reservedEnd = axisReservedEndPx
            )
            with(density) {
                (((geometry.axisStart + geometry.axisEnd) / 2f) - sliderSurfaceSize.width / 2f).toDp()
            }
        } else {
            0.dp
        }
        val sliderCrossCenterOffset = if (sliderSurfaceSize.height > 0) {
            with(density) {
                (standardPaneEdgePaddingPx + sliderTopExtentPx - sliderSurfaceSize.height / 2f).toDp()
            }
        } else {
            0.dp
        }
        val liveLabelCrossOffset = with(density) { liveLabelRelativeCenterPx.toDp() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { sliderSurfaceSize = it }
                .clip(ClipXAxisShape)
                .pointerInput(enabled, axisReservation) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { 
                            velocityTracker.resetTracking()
                            dragAccumulator = currentVal
                            scope.launch { animatable.snapTo(dragAccumulator) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val trackLengthPx = ribbonAxisGeometry(
                                width = size.width.toFloat(),
                                edgePadding = axisEdgePaddingPx,
                                reservedStart = axisReservedStartPx,
                                reservedEnd = axisReservedEndPx
                            ).let { (it.axisEnd - it.axisStart).coerceAtLeast(1f) }
                            dragAccumulator -= (dragAmount / trackLengthPx)
                            val newValue = dragAccumulator.coerceIn(0f, 1f)
                            scope.launch { animatable.snapTo(newValue) }
                            onValueChange(newValue)
                        },
                        onDragEnd = {
                            val velocityPx = velocityTracker.calculateVelocity().x
                            val trackLengthPx = ribbonAxisGeometry(
                                width = size.width.toFloat(),
                                edgePadding = axisEdgePaddingPx,
                                reservedStart = axisReservedStartPx,
                                reservedEnd = axisReservedEndPx
                            ).let { (it.axisEnd - it.axisStart).coerceAtLeast(1f) }
                            val velocityInValue = -(velocityPx / trackLengthPx)
                            
                            scope.launch {
                                val decay = exponentialDecay<Float>(frictionMultiplier = 2f)
                                animatable.animateDecay(
                                    initialVelocity = velocityInValue,
                                    animationSpec = decay
                                ) {
                                    val clamped = this.value.coerceIn(0f, 1f)
                                    onValueChange(clamped)
                                    dragAccumulator = clamped
                                }
                                
                                val finalValue = animatable.value.coerceIn(0f, 1f)
                                val closestTick = ticks.minByOrNull { abs(it.position - finalValue) }
                                if (closestTick != null) {
                                    val distanceToTick = abs(finalValue - closestTick.position)
                                    if (distanceToTick < 0.01f) {
                                        animatable.animateTo(
                                            targetValue = closestTick.position,
                                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                        ) {
                                            val clamped = this.value.coerceIn(0f, 1f)
                                            onValueChange(clamped)
                                            dragAccumulator = clamped
                                        }
                                    }
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(ClipXAxisShape)
            ) {
                val axisGeometry = ribbonAxisGeometry(
                    width = size.width,
                    edgePadding = axisEdgePaddingPx,
                    reservedStart = axisReservedStartPx,
                    reservedEnd = axisReservedEndPx
                )
                val centerX = (axisGeometry.axisStart + axisGeometry.axisEnd) / 2f
                val centerY = standardPaneEdgePaddingPx + sliderTopExtentPx
                val totalPanDistancePx = (axisGeometry.axisEnd - axisGeometry.axisStart).coerceAtLeast(1f)

                clipRect(left = axisGeometry.axisStart, right = axisGeometry.axisEnd) {
                    tickLayouts.forEach { tick ->
                        val tickX = centerX + (tick.fraction - value) * totalPanDistancePx
                        val tickTop = centerY + tickStartYOffsetPx
                        drawLine(
                            color = Color.White,
                            start = Offset(tickX, tickTop),
                            end = Offset(tickX, tickTop + tickHeightPx),
                            strokeWidth = 1.dp.toPx()
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
                            val labelPrimaryHalfExtent = if (textRotation == 0f) {
                                label.size.width / 2f
                            } else {
                                label.size.height / 2f
                            }
                            if (
                                tickX - labelPrimaryHalfExtent >= axisGeometry.axisStart &&
                                tickX + labelPrimaryHalfExtent <= axisGeometry.axisEnd
                            ) {
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
                        .width(maximumLiveLabelWidth)
                        .offset(
                            x = labelPrimaryOffset,
                            y = sliderCrossCenterOffset + liveLabelCrossOffset
                        )
                        .graphicsLayer { rotationZ = textRotation }
                )
            }
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.rotateForAxis(axis)) {
            sliderContent()
        }
    }
}
