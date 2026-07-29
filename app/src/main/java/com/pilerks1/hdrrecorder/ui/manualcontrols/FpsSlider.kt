package com.pilerks1.hdrrecorder.ui.manualcontrols

import android.util.Range
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import kotlin.math.abs
import kotlin.math.roundToInt

private data class RangeSliderTickLayout(
    val fraction: Float,
    val label: TextLayoutResult?
)

private data class RangeSliderGeometry(
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

private fun rangeSliderGeometry(
    width: Float,
    reservedStart: Float,
    reservedEnd: Float,
    thumbHalfWidth: Float
): RangeSliderGeometry {
    val axisStart = (reservedStart + thumbHalfWidth).coerceAtMost(width / 2f)
    val axisEnd = (width - reservedEnd - thumbHalfWidth).coerceAtLeast(axisStart)
    return RangeSliderGeometry(axisStart, axisEnd)
}

private fun valueFraction(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>
): Float {
    val length = valueRange.endInclusive - valueRange.start
    return if (length == 0f) 0.5f else (value - valueRange.start) / length
}

@Composable
private fun FpsSliderThumb(
    value: Float,
    color: Color,
    textColor: Color,
    textRotation: Float
) {
    Box(
        modifier = Modifier
            .size(
                width = SliderPaneStyle.rangeThumbWidth,
                height = SliderPaneStyle.rangeThumbHeight
            )
            .background(color, androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${value.toInt()}",
            color = textColor,
            style = SliderPaneStyle.rangeThumbTextStyle,
            modifier = Modifier
                .wrapContentSize(unbounded = true)
                .offset(y = -SliderPaneStyle.rangeThumbLabelOffset)
                .graphicsLayer { rotationZ = textRotation }
        )
    }
}

@Composable
private fun RangeSliderTickAxis(
    ticks: List<SliderTick>,
    color: Color,
    axis: AxisSpec,
    reservedStartPx: Float,
    reservedEndPx: Float,
    thumbHalfWidthPx: Float,
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
            .offset(y = SliderPaneStyle.rangeTickOffset)
    ) {
        val geometry = rangeSliderGeometry(
            width = size.width,
            reservedStart = reservedStartPx,
            reservedEnd = reservedEndPx,
            thumbHalfWidth = thumbHalfWidthPx
        )
        tickLayouts.forEach { tick ->
            val tickX = geometry.positionAt(tick.fraction)
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
private fun RotatingRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    axis: AxisSpec,
    modifier: Modifier = Modifier,
    onValueChangeFinished: ((
        releasedRange: ClosedFloatingPointRange<Float>,
        startingRange: ClosedFloatingPointRange<Float>,
        draggedBound: FpsRangeBound,
        dragDirection: Float
    ) -> Unit)? = null,
    enabled: Boolean = true,
    isActive: Boolean = true,
    singleThumb: Boolean = false,
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
    var gestureStartRange by remember { mutableStateOf(value) }
    var latestGestureRange by remember { mutableStateOf(value) }
    val density = LocalDensity.current
    val resolvedAxisReservation = SliderAxisReservation(
        start = maxOf(axisReservation.start, SliderPaneStyle.actionEdgePadding),
        end = maxOf(axisReservation.end, SliderPaneStyle.actionEdgePadding)
    )
    val reservedStartPx = with(density) { resolvedAxisReservation.start.toPx() }
    val reservedEndPx = with(density) { resolvedAxisReservation.end.toPx() }
    val thumbHalfWidthPx = with(density) { SliderPaneStyle.rangeThumbHalfWidth.toPx() }
    val sliderColors = SliderDefaults.colors(
        thumbColor = mainColor,
        activeTrackColor = mainColor,
        inactiveTrackColor = secondaryColor,
        disabledThumbColor = mainColor,
        disabledActiveTrackColor = mainColor,
        disabledInactiveTrackColor = secondaryColor
    )

    fun valueAt(positionX: Float, width: Float): Float {
        val geometry = rangeSliderGeometry(
            width = width,
            reservedStart = reservedStartPx,
            reservedEnd = reservedEndPx,
            thumbHalfWidth = thumbHalfWidthPx
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
            reservedStart = reservedStartPx,
            reservedEnd = reservedEndPx,
            thumbHalfWidth = thumbHalfWidthPx
        )
        val sharedPosition = geometry.positionAt(valueFraction(range.start, valueRange))
        return when {
            positionX < sharedPosition - thumbHalfWidthPx -> RangeThumb.START
            positionX > sharedPosition + thumbHalfWidthPx -> RangeThumb.END
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

    val sliderContent = @Composable {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(
                    enabled,
                    singleThumb,
                    resolvedAxisReservation,
                    valueRange,
                    thumbHalfWidthPx
                ) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onTap = { offset ->
                            val width = size.width.toFloat()
                            val geometry = rangeSliderGeometry(
                                width = width,
                                reservedStart = reservedStartPx,
                                reservedEnd = reservedEndPx,
                                thumbHalfWidth = thumbHalfWidthPx
                            )
                            if (offset.x !in geometry.axisStart..geometry.axisEnd) {
                                return@detectTapGestures
                            }

                            val startingRange = currentValue
                            val thumb = thumbAt(
                                positionX = offset.x,
                                width = width,
                                range = startingRange,
                                deferOverlappingThumbs = false
                            ) ?: return@detectTapGestures
                            val releasedRange = rangeWithValue(
                                range = startingRange,
                                thumb = thumb,
                                newValue = valueAt(offset.x, width)
                            )
                            val startingValue = when (thumb) {
                                RangeThumb.START -> startingRange.start
                                RangeThumb.END -> startingRange.endInclusive
                            }
                            val releasedValue = when (thumb) {
                                RangeThumb.START -> releasedRange.start
                                RangeThumb.END -> releasedRange.endInclusive
                            }
                            currentOnValueChange(releasedRange)
                            currentOnFinished?.invoke(
                                releasedRange,
                                startingRange,
                                thumb.toFpsRangeBound(),
                                releasedValue - startingValue
                            )
                        }
                    )
                }
                .pointerInput(
                    enabled,
                    singleThumb,
                    resolvedAxisReservation,
                    valueRange,
                    thumbHalfWidthPx
                ) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            gestureStartRange = currentValue
                            latestGestureRange = currentValue
                            draggedThumb = thumbAt(
                                positionX = offset.x,
                                width = size.width.toFloat(),
                                range = currentValue,
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
                            val newValue = valueAt(change.position.x, size.width.toFloat())
                            val updated = draggedThumb?.let { thumb ->
                                rangeWithValue(baseRange, thumb, newValue)
                            } ?: baseRange
                            latestGestureRange = updated
                            currentOnValueChange(updated)
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
                                currentOnFinished?.invoke(
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
            },
            contentAlignment = Alignment.Center
        ) {
            RangeSliderTickAxis(
                ticks = ticks,
                color = secondaryColor,
                axis = axis,
                reservedStartPx = reservedStartPx,
                reservedEndPx = reservedEndPx,
                thumbHalfWidthPx = thumbHalfWidthPx
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = resolvedAxisReservation.start,
                        end = resolvedAxisReservation.end
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (singleThumb) {
                    Slider(
                        value = value.endInclusive,
                        onValueChange = {},
                        valueRange = valueRange,
                        enabled = false,
                        colors = sliderColors,
                        thumb = {
                            FpsSliderThumb(
                                value = value.endInclusive,
                                color = mainColor,
                                textColor = if (isActive) Color.White else mainColor,
                                textRotation = textRotation
                            )
                        },
                        track = { sliderState ->
                            SliderDefaults.Track(
                                sliderState = sliderState,
                                modifier = Modifier.height(SliderPaneStyle.rangeTrackThickness),
                                colors = sliderColors,
                                enabled = false
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    RangeSlider(
                        value = value,
                        onValueChange = {},
                        valueRange = valueRange,
                        enabled = false,
                        colors = sliderColors,
                        startThumb = {
                            FpsSliderThumb(
                                value = value.start,
                                color = mainColor,
                                textColor = if (isActive) Color.White else mainColor,
                                textRotation = textRotation
                            )
                        },
                        endThumb = {
                            FpsSliderThumb(
                                value = value.endInclusive,
                                color = mainColor,
                                textColor = if (isActive) Color.White else mainColor,
                                textRotation = textRotation
                            )
                        },
                        track = { rangeSliderState ->
                            SliderDefaults.Track(
                                rangeSliderState = rangeSliderState,
                                modifier = Modifier.height(SliderPaneStyle.rangeTrackThickness),
                                colors = sliderColors,
                                enabled = false
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
    isExposureFullyManual: Boolean,
    caps: CameraCapabilities?,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    axis: AxisSpec,
    axisReservation: SliderAxisReservation = SliderAxisReservation(),
    enabled: Boolean = true,
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

    if (isExposureFullyManual) {
        val legalFrameRates = remember(caps?.fpsRanges) {
            FpsRangeMath.fixedFrameRates(validRanges)
        }
        val requestedFps = currentRange.endInclusive.roundToInt()
        val initialFps = FpsRangeMath.nearestFixedFrameRate(
            requestedFps = requestedFps,
            validRanges = validRanges
        ) ?: requestedFps
        val manualTicks = legalFrameRates.map { fps ->
            SliderTick(
                position = valueFraction(fps.toFloat(), minFps..maxFps),
                label = fps.toString()
            )
        }
        var manualRange by remember(initialFps, legalFrameRates) {
            mutableStateOf(initialFps.toFloat()..initialFps.toFloat())
        }

        RotatingRangeSlider(
            value = manualRange,
            onValueChange = { draggedRange ->
                if ((!isRecording || isManualFps) && enabled) {
                    val snappedFps = FpsRangeMath.nearestFixedFrameRate(
                        requestedFps = draggedRange.endInclusive.roundToInt(),
                        validRanges = validRanges
                    )
                    if (snappedFps != null) {
                        val snapped = snappedFps.toFloat()
                        manualRange = snapped..snapped
                    }
                }
            },
            onValueChangeFinished = { releasedRange, _, _, _ ->
                if ((!isRecording || isManualFps) && enabled) {
                    val snappedFps = FpsRangeMath.nearestFixedFrameRate(
                        requestedFps = releasedRange.endInclusive.roundToInt(),
                        validRanges = validRanges
                    )
                    if (snappedFps != null) {
                        val snapped = snappedFps.toFloat()
                        manualRange = snapped..snapped
                        localRange = manualRange
                        onValueChange(manualRange)
                    }
                }
            },
            valueRange = minFps..maxFps,
            axis = axis,
            ticks = manualTicks,
            isActive = isManualFps,
            singleThumb = true,
            enabled = enabled &&
                legalFrameRates.isNotEmpty() &&
                (!isRecording || isManualFps),
            axisReservation = axisReservation,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    RotatingRangeSlider(
        value = localRange,
        onValueChange = { newRange ->
            if ((!isRecording || isManualFps) && enabled) {
                localRange = newRange
            }
        },
        onValueChangeFinished = { releasedRange, startingRange, draggedBound, dragDirection ->
            if ((!isRecording || isManualFps) && enabled) {
                val clamped = FpsRangeMath.clampToSupportedRange(
                    newRange = releasedRange,
                    oldRange = startingRange,
                    draggedBound = draggedBound,
                    dragDirection = dragDirection,
                    validRanges = validRanges
                )
                localRange = clamped
                onValueChange(clamped)
            }
        },
        valueRange = minFps..maxFps,
        axis = axis,
        ticks = ticks,
        isActive = isManualFps,
        enabled = enabled && (!isRecording || isManualFps),
        axisReservation = axisReservation,
        modifier = modifier.fillMaxSize()
    )
}

private enum class RangeThumb {
    START,
    END
}

private fun RangeThumb.toFpsRangeBound(): FpsRangeBound = when (this) {
    RangeThumb.START -> FpsRangeBound.LOWER
    RangeThumb.END -> FpsRangeBound.UPPER
}
