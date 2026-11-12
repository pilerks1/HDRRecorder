package com.pilerks1.hdrrecorder.ui.camera.controls.slider

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import com.pilerks1.hdrrecorder.ui.camera.layout.AxisSpec
import com.pilerks1.hdrrecorder.ui.style.SliderDimensions
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RotatingRangeSlider(
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
    val density = LocalDensity.current
    val resolvedAxisReservation = SliderAxisReservation(
        start = maxOf(axisReservation.start, SliderDimensions.actionEdgePadding),
        end = maxOf(axisReservation.end, SliderDimensions.actionEdgePadding)
    )
    val reservedStartPx = with(density) { resolvedAxisReservation.start.toPx() }
    val reservedEndPx = with(density) { resolvedAxisReservation.end.toPx() }
    val thumbHalfWidthPx = with(density) { SliderDimensions.rangeThumbHalfWidth.toPx() }
    val sliderColors = SliderDefaults.colors(
        thumbColor = mainColor,
        activeTrackColor = mainColor,
        inactiveTrackColor = secondaryColor,
        disabledThumbColor = mainColor,
        disabledActiveTrackColor = mainColor,
        disabledInactiveTrackColor = secondaryColor
    )

    val interaction = RangeSliderInteraction(
        valueRange, reservedStartPx, reservedEndPx, thumbHalfWidthPx, singleThumb
    )

    val sliderContent = @Composable {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Frame rate"
                    stateDescription = if (value.start == value.endInclusive) "${value.endInclusive.roundToInt()} FPS"
                        else "${value.start.roundToInt()}–${value.endInclusive.roundToInt()} FPS"
                    progressBarRangeInfo = ProgressBarRangeInfo(value.endInclusive, valueRange)
                    if (enabled) {
                        // Accessibility adjusts the upper bound, matching the single-thumb slider.
                        setProgress { target ->
                            val startingRange = currentValue
                            val released = interaction.rangeWithValue(startingRange, RangeThumb.END, target.coerceIn(valueRange))
                            currentOnValueChange(released)
                            currentOnFinished?.invoke(released, startingRange, FpsRangeBound.UPPER,
                                released.endInclusive - startingRange.endInclusive)
                            true
                        }
                    } else disabled()
                }
                .pointerInput(
                    enabled,
                    singleThumb,
                    resolvedAxisReservation,
                    valueRange,
                    thumbHalfWidthPx
                ) {
                    if (!enabled) return@pointerInput
                    detectRangeSliderTaps(
                        interaction = interaction,
                        currentValue = { currentValue },
                        onValueChange = { currentOnValueChange(it) },
                        onFinished = { released, starting, bound, direction ->
                            currentOnFinished?.invoke(released, starting, bound, direction)
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
                    detectRangeSliderDrags(
                        interaction = interaction,
                        currentValue = { currentValue },
                        onValueChange = { currentOnValueChange(it) },
                        onFinished = { released, starting, bound, direction ->
                            currentOnFinished?.invoke(released, starting, bound, direction)
                        }
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
                                modifier = Modifier.height(SliderDimensions.rangeTrackThickness),
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
                                modifier = Modifier.height(SliderDimensions.rangeTrackThickness),
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
