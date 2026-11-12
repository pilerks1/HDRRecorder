package com.pilerks1.hdrrecorder.ui.camera.controls.slider

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pilerks1.hdrrecorder.camera.camera2.FixedFrameRates
import com.pilerks1.hdrrecorder.camera.model.CameraCapabilities
import com.pilerks1.hdrrecorder.ui.camera.layout.AxisSpec
import kotlin.math.roundToInt

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
    val validRanges = caps?.fpsRanges.orEmpty()
    // Without reported ranges the slider is shown for layout only and cannot be edited.
    val minFps = validRanges.minOfOrNull { it.lower }?.toFloat() ?: 15f
    val maxFps = validRanges.maxOfOrNull { it.upper }?.toFloat() ?: 60f
    val canEdit = enabled && validRanges.isNotEmpty() && (!isRecording || isManualFps)
    val ticks = remember(caps?.fpsRanges) {
        validRanges
            .flatMap { listOf(it.lower.toFloat(), it.upper.toFloat()) }
            .distinct()
            .sorted()
            .map { fps -> SliderTick(valueFraction(fps, minFps..maxFps), fps.roundToInt().toString()) }
    }
    var localRange by remember(currentRange) { mutableStateOf(currentRange) }

    if (isExposureFullyManual) {
        val legalFrameRates = remember(caps?.fpsRanges) {
            FixedFrameRates.fixedFrameRates(validRanges)
        }
        val requestedFps = currentRange.endInclusive.roundToInt()
        val initialFps = FixedFrameRates.nearestFixedFrameRate(
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
        fun snapped(range: ClosedFloatingPointRange<Float>): ClosedFloatingPointRange<Float>? =
            FixedFrameRates.nearestFixedFrameRate(range.endInclusive.roundToInt(), validRanges)
                ?.toFloat()?.let { it..it }

        RotatingRangeSlider(
            value = manualRange,
            onValueChange = { draggedRange ->
                if (canEdit) snapped(draggedRange)?.let { manualRange = it }
            },
            onValueChangeFinished = { releasedRange, _, _, _ ->
                if (canEdit) snapped(releasedRange)?.let {
                    manualRange = it
                    onValueChange(it)
                }
            },
            valueRange = minFps..maxFps,
            axis = axis,
            ticks = manualTicks,
            isActive = isManualFps,
            singleThumb = true,
            enabled = canEdit && legalFrameRates.isNotEmpty(),
            axisReservation = axisReservation,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    RotatingRangeSlider(
        value = localRange,
        onValueChange = { newRange ->
            if (canEdit) localRange = newRange
        },
        onValueChangeFinished = { releasedRange, startingRange, draggedBound, dragDirection ->
            if (canEdit) {
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
        enabled = canEdit,
        axisReservation = axisReservation,
        modifier = modifier.fillMaxSize()
    )
}
