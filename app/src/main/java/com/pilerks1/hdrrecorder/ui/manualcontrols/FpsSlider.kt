package com.pilerks1.hdrrecorder.ui.manualcontrols

import android.util.Range
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.pilerks1.hdrrecorder.data.camera.CameraCapabilities
import com.pilerks1.hdrrecorder.ui.layout.AxisSpec
import kotlin.math.roundToInt

private data class RangeSliderTickLayout(
    val fraction: Float,
    val label: TextLayoutResult?
)

@Composable
private fun RangeSliderTickAxis(
    ticks: List<SliderTick>,
    color: Color,
    axis: AxisSpec,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textStyle = remember(color) { SliderPaneStyle.rangeTickTextStyle.copy(color = color) }
    val tickLayouts = remember(ticks, color, density, textMeasurer) {
        if (ticks.isEmpty()) {
            (0..10).map { index ->
                RangeSliderTickLayout(
                    fraction = index / 10f,
                    label = null
                )
            }
        } else {
            ticks.map { tick ->
                RangeSliderTickLayout(
                    fraction = tick.position,
                    label = tick.label.takeIf(String::isNotEmpty)?.let {
                        textMeasurer.measure(AnnotatedString(it), style = textStyle)
                    }
                )
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(horizontal = 10.dp)
            .offset(y = SliderPaneStyle.rangeTickOffset)
    ) {
        tickLayouts.forEach { tick ->
            val tickX = size.width * tick.fraction
            val tickHeight = with(density) { SliderPaneStyle.sliderTickHeight.toPx() }
            drawLine(
                color = color,
                start = Offset(tickX, 0f),
                end = Offset(tickX, tickHeight),
                strokeWidth = 1.dp.toPx()
            )
            tick.label?.let { label ->
                val topLeft = Offset(
                    x = tickX - label.size.width / 2f,
                    y = tickHeight + with(density) { SliderPaneStyle.rangeTickTextPadding.toPx() }
                )
                rotate(
                    degrees = axis.contentCounterRotationDegrees,
                    pivot = Offset(tickX, topLeft.y + label.size.height / 2f)
                ) {
                    drawText(label, topLeft = topLeft)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotatingRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    axis: AxisSpec,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    isActive: Boolean = true,
    ticks: List<SliderTick> = emptyList(),
    axisReservation: SliderAxisReservation = SliderAxisReservation()
) {
    val textRotation = axis.contentCounterRotationDegrees

    val mainColor = if (isActive) Color.White else if (enabled) Color.Gray else Color.DarkGray
    val secondaryColor = if (isActive) Color.Gray else if (enabled) Color.Gray else Color.DarkGray

    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    var draggedThumb by remember { mutableStateOf<RangeThumb?>(null) }
    val density = LocalDensity.current
    val reservedStartPx = with(density) { axisReservation.start.toPx() }
    val reservedEndPx = with(density) { axisReservation.end.toPx() }

    fun valueAt(positionX: Float, width: Float): Float {
        val trackLength = (width - reservedStartPx - reservedEndPx).coerceAtLeast(1f)
        val fraction = ((positionX - reservedStartPx) / trackLength).coerceIn(0f, 1f)
        return valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
    }

    val sliderContent = @Composable {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(enabled, axisReservation, valueRange) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val touchedValue = valueAt(offset.x, size.width.toFloat())
                            draggedThumb = if (
                                kotlin.math.abs(touchedValue - currentValue.start) <=
                                kotlin.math.abs(touchedValue - currentValue.endInclusive)
                            ) RangeThumb.START else RangeThumb.END
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val newValue = valueAt(change.position.x, size.width.toFloat())
                            val updated = when (draggedThumb) {
                                RangeThumb.START -> newValue.coerceAtMost(currentValue.endInclusive)..currentValue.endInclusive
                                RangeThumb.END -> currentValue.start..newValue.coerceAtLeast(currentValue.start)
                                null -> currentValue
                            }
                            currentOnValueChange(updated)
                        },
                        onDragEnd = {
                            draggedThumb = null
                            currentOnFinished?.invoke()
                        },
                        onDragCancel = { draggedThumb = null }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = axisReservation.start, end = axisReservation.end),
                contentAlignment = Alignment.Center
            ) {
                RangeSliderTickAxis(
                    ticks = ticks,
                    color = secondaryColor,
                    axis = axis
                )
                RangeSlider(
                value = value,
                onValueChange = {},
                valueRange = valueRange,
                enabled = false,
                colors = SliderDefaults.colors(
                    thumbColor = mainColor,
                    activeTrackColor = mainColor,
                    inactiveTrackColor = secondaryColor,
                    disabledThumbColor = mainColor,
                    disabledActiveTrackColor = mainColor,
                    disabledInactiveTrackColor = secondaryColor
                ),
                startThumb = {
                    Box(
                        modifier = Modifier.size(width = 8.dp, height = 24.dp).background(mainColor, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${value.start.toInt()}",
                            color = if (isActive) Color.White else mainColor,
                            style = SliderPaneStyle.rangeThumbTextStyle,
                            modifier = Modifier
                                .wrapContentSize(unbounded = true)
                                .offset(y = -SliderPaneStyle.rangeThumbLabelOffset)
                                .graphicsLayer { rotationZ = textRotation }
                        )
                    }
                },
                endThumb = {
                    Box(
                        modifier = Modifier.size(width = 8.dp, height = 24.dp).background(mainColor, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${value.endInclusive.toInt()}",
                            color = if (isActive) Color.White else mainColor,
                            style = SliderPaneStyle.rangeThumbTextStyle,
                            modifier = Modifier
                                .wrapContentSize(unbounded = true)
                                .offset(y = -SliderPaneStyle.rangeThumbLabelOffset)
                                .graphicsLayer { rotationZ = textRotation }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
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

@Composable
fun FpsSlider(
    isManualFps: Boolean,
    currentRange: ClosedFloatingPointRange<Float>,
    isRecording: Boolean,
    caps: CameraCapabilities?,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    axis: AxisSpec,
    axisReservation: SliderAxisReservation = SliderAxisReservation(),
    modifier: Modifier = Modifier
) {
    val validRanges = caps?.fpsRanges ?: emptyList<Range<Int>>()
    val minFps = validRanges.minOfOrNull { it.lower }?.toFloat() ?: 15f
    val maxFps = validRanges.maxOfOrNull { it.upper }?.toFloat() ?: 60f
    val ticks = remember(caps?.fpsRanges) {
        validRanges
            .flatMap { listOf(it.lower.toFloat(), it.upper.toFloat()) }
            .distinct()
            .sorted()
            .map { fps ->
                val fraction = if (maxFps == minFps) 0.5f else (fps - minFps) / (maxFps - minFps)
                SliderTick(fraction, fps.roundToInt().toString())
            }
    }
    var localRange by remember(currentRange) { mutableStateOf(currentRange) }

    RotatingRangeSlider(
        value = localRange,
        onValueChange = { newRange ->
            if (!isRecording || isManualFps) localRange = newRange
        },
        onValueChangeFinished = {
            if (!isRecording || isManualFps) {
                val clamped = FpsRangeMath.clampToSupportedRange(localRange, currentRange, validRanges)
                localRange = clamped
                onValueChange(clamped)
            }
        },
        valueRange = minFps..maxFps,
        axis = axis,
        ticks = ticks,
        isActive = isManualFps,
        enabled = !isRecording || isManualFps,
        axisReservation = axisReservation,
        modifier = modifier.fillMaxSize()
    )
}

private enum class RangeThumb {
    START,
    END
}
