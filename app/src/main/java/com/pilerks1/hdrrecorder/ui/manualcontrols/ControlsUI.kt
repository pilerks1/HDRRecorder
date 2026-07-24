package com.pilerks1.hdrrecorder.ui.manualcontrols

import android.util.Range
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import com.pilerks1.hdrrecorder.data.camera.CameraCapabilities
import com.pilerks1.hdrrecorder.model.CameraTelemetry
import com.pilerks1.hdrrecorder.model.RecordingAspectRatio
import com.pilerks1.hdrrecorder.model.Resolution
import com.pilerks1.hdrrecorder.ui.CameraControlPanel
import com.pilerks1.hdrrecorder.ui.ManualControl
import com.pilerks1.hdrrecorder.ui.ManualControlsState
import com.pilerks1.hdrrecorder.ui.layout.AxisSpec
import com.pilerks1.hdrrecorder.ui.layout.ActionRailSpec
import com.pilerks1.hdrrecorder.ui.layout.EdgeInsets
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

private data class EffectiveWhiteBalance(val temperatureKelvin: Int, val tint: Int)

fun availableControlPanels(
    caps: CameraCapabilities?
): List<CameraControlPanel> = orderedControlPanels(
    hasShutter = caps?.hasManualShutterControl == true,
    hasIso = caps?.hasManualIsoControl == true,
    hasExposureCompensation = caps?.hasExposureCompensationControl == true,
    hasFocus = caps?.hasManualFocusControl == true,
    hasWhiteBalance = caps?.supportsCCT == true
)

internal fun orderedControlPanels(
    hasShutter: Boolean,
    hasIso: Boolean,
    hasExposureCompensation: Boolean,
    hasFocus: Boolean,
    hasWhiteBalance: Boolean
): List<CameraControlPanel> = buildList {
    add(CameraControlPanel.RESOLUTION)
    if (hasShutter) add(CameraControlPanel.SHUTTER)
    if (hasIso) add(CameraControlPanel.ISO)
    if (hasExposureCompensation) add(CameraControlPanel.EXPOSURE_COMPENSATION)
    if (hasFocus) add(CameraControlPanel.FOCUS)
    if (hasWhiteBalance) add(CameraControlPanel.WHITE_BALANCE)
    if (hasWhiteBalance) add(CameraControlPanel.TINT)
}

@Composable
fun SecondaryControlsRail(
    panels: List<CameraControlPanel>,
    activePanel: CameraControlPanel?,
    axis: AxisSpec,
    railSpec: ActionRailSpec,
    onTogglePanel: (CameraControlPanel) -> Unit,
    modifier: Modifier = Modifier
) {
    Layout(
        modifier = modifier,
        content = {
            panels.forEach { panel ->
                GridButton(
                    label = panel.gridLabel,
                    isActive = activePanel == panel,
                    onClick = { onTogglePanel(panel) }
                )
            }
        }
    ) { measurables, constraints ->
        val buttonSize = railSpec.buttonSize.roundToInt().coerceAtLeast(1)
        val placeables = measurables.map { measurable ->
            measurable.measure(androidx.compose.ui.unit.Constraints.fixed(buttonSize, buttonSize))
        }
        val vertical = axis.usesVerticalTrack
        val availablePrimary = if (vertical) constraints.maxHeight else constraints.maxWidth
        val primaryOrigins = evenlySpacedOrigins(
            availableLength = availablePrimary,
            itemSize = buttonSize,
            itemCount = placeables.size
        )
        val crossOrigin = if (vertical) {
            ((constraints.maxWidth - buttonSize) / 2).coerceAtLeast(0)
        } else {
            ((constraints.maxHeight - buttonSize) / 2).coerceAtLeast(0)
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                val x = if (vertical) {
                    crossOrigin
                } else {
                    primaryOrigins[index]
                }
                val y = if (vertical) {
                    primaryOrigins[index]
                } else {
                    crossOrigin
                }
                placeable.place(x, y)
            }
        }
    }
}

internal fun evenlySpacedOrigins(
    availableLength: Int,
    itemSize: Int,
    itemCount: Int
): List<Int> {
    if (itemCount <= 0) return emptyList()

    val freeSpace = (availableLength - itemSize * itemCount).coerceAtLeast(0)
    val gap = freeSpace.toFloat() / (itemCount + 1)
    return List(itemCount) { index ->
        (gap + index * (itemSize + gap)).roundToInt()
    }
}

@Composable
private fun GridButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Text(
            text = label,
            color = if (isActive) Color.Yellow else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ActiveControlPanel(
    state: ManualControlsState,
    caps: CameraCapabilities?,
    cameraTelemetry: StateFlow<CameraTelemetry>,
    isRecording: Boolean,
    selectedResolution: Resolution,
    selectedAspectRatio: RecordingAspectRatio,
    axis: AxisSpec,
    contentInsets: EdgeInsets,
    onCycleResolution: () -> Unit,
    onCycleAspectRatio: () -> Unit,
    onSetIso: (Boolean, Int?) -> Unit,
    onSetSs: (Boolean, Long?) -> Unit,
    onSetEv: (Boolean, Int?) -> Unit,
    onSetNightMode: (Boolean) -> Unit,
    onSetFocus: (Boolean, Float?) -> Unit,
    onSetWb: (Boolean, Int?, Int?) -> Unit,
    onSetFps: (Boolean, Range<Int>?) -> Unit,
    autoFps: Int,
    onCycleFps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activePanel = state.activePanel ?: return
    val telemetry = cameraTelemetry.collectAsState().value
    val density = LocalDensity.current
    val safeContentPadding = with(density) {
        if (axis.usesVerticalTrack) {
            PaddingValues(
                top = contentInsets.top.toDp(),
                bottom = contentInsets.bottom.toDp()
            )
        } else {
            PaddingValues(
                start = contentInsets.left.toDp(),
                end = contentInsets.right.toDp()
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(safeContentPadding),
            contentAlignment = Alignment.Center
        ) panelContent@{
            if (activePanel == CameraControlPanel.RESOLUTION) {
                ResolutionPanel(
                    selectedResolution = selectedResolution,
                    selectedAspectRatio = selectedAspectRatio,
                    state = state,
                    caps = caps,
                    autoFps = autoFps,
                    isRecording = isRecording,
                    axis = axis,
                    onCycleResolution = onCycleResolution,
                    onCycleAspectRatio = onCycleAspectRatio,
                    onCycleFps = onCycleFps,
                    onSetFps = onSetFps
                )
                return@panelContent
            }

            when (activePanel.manualControl) {
            ManualControl.ISO -> {
                val scale = remember(caps?.isoRange) { SliderScales.iso(caps) }
                val effective = telemetry.iso ?: state.isoValue ?: scale.fallbackValue
                StandardSlider(
                    scale = scale,
                    binding = StandardSliderBinding(
                        isManual = state.isManualIso,
                        effectiveValue = if (state.isManualIso) state.isoValue ?: effective else effective,
                        onToggleAuto = {
                            if (state.isManualIso) onSetIso(false, null) else onSetIso(true, effective)
                        },
                        onManualValue = { onSetIso(true, it) }
                    ),
                    axis = axis
                )
            }
            ManualControl.SHUTTER -> {
                val scale = remember(caps?.ssRangeNanos) { SliderScales.shutter(caps) }
                val effective = telemetry.shutterNanos ?: state.ssValueNanos ?: scale.fallbackValue
                StandardSlider(
                    scale = scale,
                    binding = StandardSliderBinding(
                        isManual = state.isManualSs,
                        effectiveValue = if (state.isManualSs) state.ssValueNanos ?: effective else effective,
                        onToggleAuto = {
                            if (state.isManualSs) onSetSs(false, null) else onSetSs(true, effective)
                        },
                        onManualValue = { onSetSs(true, it) }
                    ),
                    axis = axis
                )
            }
            ManualControl.EXPOSURE_COMPENSATION -> {
                val scale = remember(caps?.evRange, caps?.evStep) { SliderScales.exposureCompensation(caps) }
                val aeIsOff = when {
                    state.isManualSs && state.isManualIso -> true
                    state.isManualSs -> caps?.supportsShutterPriorityAe != true
                    state.isManualIso -> caps?.supportsIsoPriorityAe != true
                    else -> false
                }
                val isManualEv = state.evValueIndex != 0
                BaseSlider(
                    value = scale.progress(state.evValueIndex),
                    axis = axis,
                    labelString = scale.formatValue(state.evValueIndex),
                    ticks = scale.ticks,
                    layoutLabels = scale.layoutLabels,
                    onValueChange = { onSetEv(true, scale.value(it)) },
                    primaryButtonText = "RES",
                    sliderEnabled = !aeIsOff,
                    primaryButtonEnabled = true,
                    primaryButtonColor = if (isManualEv) Color.White else Color.DarkGray,
                    primaryButtonTextColor = if (isManualEv) Color.Black else Color.LightGray,
                    onPrimaryButtonClick = { onSetEv(true, 0) },
                    secondaryButtonText = if (caps?.supportsNightMode == true) "NHT" else null,
                    secondaryButtonEnabled = !aeIsOff,
                    secondaryButtonColor = if (state.isNightModeAeEnabled && !aeIsOff) Color.Yellow else Color.DarkGray,
                    secondaryButtonTextColor = if (state.isNightModeAeEnabled && !aeIsOff) Color.Black else Color.LightGray,
                    onSecondaryButtonClick = if (caps?.supportsNightMode == true) {
                        { onSetNightMode(!state.isNightModeAeEnabled) }
                    } else null
                )
            }
            ManualControl.FOCUS -> {
                val scale = remember(caps?.focusMinDistanceDiopters) { SliderScales.focus(caps) }
                val effective = telemetry.focusDistanceDiopters ?: state.focusDistanceDiopters ?: scale.fallbackValue
                StandardSlider(
                    scale = scale,
                    binding = StandardSliderBinding(
                        isManual = state.isManualFocus,
                        effectiveValue = if (state.isManualFocus) state.focusDistanceDiopters ?: effective else effective,
                        onToggleAuto = {
                            if (state.isManualFocus) onSetFocus(false, null) else onSetFocus(true, effective)
                        },
                        onManualValue = { onSetFocus(true, it) }
                    ),
                    axis = axis
                )
            }
            ManualControl.WHITE_BALANCE -> {
                val scale = remember(caps?.cctTemperatureRange) { SliderScales.whiteBalance(caps) }
                val effective = resolveEffectiveWhiteBalance(state, telemetry, caps)
                StandardSlider(
                    scale = scale,
                    binding = StandardSliderBinding(
                        isManual = state.isManualWb,
                        effectiveValue = if (state.isManualWb) state.wbTemp ?: effective.temperatureKelvin else effective.temperatureKelvin,
                        onToggleAuto = {
                            if (state.isManualWb) onSetWb(false, null, null)
                            else onSetWb(true, effective.temperatureKelvin, effective.tint)
                        },
                        onManualValue = { onSetWb(true, it, effective.tint) }
                    ),
                    axis = axis
                )
            }
            ManualControl.TINT -> {
                val scale = remember { SliderScales.tint() }
                val effective = resolveEffectiveWhiteBalance(state, telemetry, caps)
                StandardSlider(
                    scale = scale,
                    binding = StandardSliderBinding(
                        isManual = state.isManualWb,
                        effectiveValue = if (state.isManualWb) state.wbTint ?: effective.tint else effective.tint,
                        onToggleAuto = {
                            if (state.isManualWb) onSetWb(false, null, null)
                            else onSetWb(true, effective.temperatureKelvin, effective.tint)
                        },
                        onManualValue = {
                            onSetWb(true, state.wbTemp ?: effective.temperatureKelvin, it)
                        }
                    ),
                    axis = axis
                )
            }
                null -> Unit
            }
        }
    }
}

@Composable
private fun ResolutionPanel(
    selectedResolution: Resolution,
    selectedAspectRatio: RecordingAspectRatio,
    state: ManualControlsState,
    caps: CameraCapabilities?,
    autoFps: Int,
    isRecording: Boolean,
    axis: AxisSpec,
    onCycleResolution: () -> Unit,
    onCycleAspectRatio: () -> Unit,
    onCycleFps: () -> Unit,
    onSetFps: (Boolean, Range<Int>?) -> Unit
) {
    val currentRange = state.fpsRange ?: Range(30, 30)
    SliderPanelLayout(
        axis = axis,
        actionCount = 3,
        modifier = Modifier.fillMaxSize(),
        edgePadding = SliderPaneStyle.actionEdgePadding,
        contentGap = SliderPaneStyle.actionEdgePadding,
        actions = {
            SliderActionButton(
                text = selectedResolution.qualityName,
                enabled = !isRecording,
                background = Color.DarkGray,
                textColor = Color.White,
                onClick = onCycleResolution
            )
            SliderActionButton(
                text = selectedAspectRatio.displayLabel,
                enabled = !isRecording,
                background = Color.DarkGray,
                textColor = Color.White,
                onClick = onCycleAspectRatio
            )
            SliderActionButton(
                text = if (state.isManualFps) "MAN" else "$autoFps",
                enabled = !isRecording,
                background = if (state.isManualFps) Color.DarkGray else Color.White,
                textColor = if (state.isManualFps) Color.LightGray else Color.Black,
                onClick = onCycleFps
            )
        },
        content = { sliderModifier, axisReservation ->
            FpsSlider(
                isManualFps = state.isManualFps,
                currentRange = currentRange.lower.toFloat()..currentRange.upper.toFloat(),
                isRecording = isRecording,
                caps = caps,
                onValueChange = { newRange ->
                    onSetFps(true, Range(newRange.start.roundToInt(), newRange.endInclusive.roundToInt()))
                },
                axis = axis,
                axisReservation = axisReservation,
                modifier = sliderModifier
            )
        }
    )
}

@Composable
private fun <T : Any> StandardSlider(
    scale: SliderScale<T>,
    binding: StandardSliderBinding<T>,
    axis: AxisSpec
) {
    BaseSlider(
        value = scale.progress(binding.effectiveValue),
        axis = axis,
        labelString = scale.formatValue(binding.effectiveValue),
        ticks = scale.ticks,
        layoutLabels = scale.layoutLabels,
        onValueChange = { binding.onManualValue(scale.value(it)) },
        primaryButtonText = if (binding.isManual) "MAN" else "AUTO",
        primaryButtonColor = if (binding.isManual) Color.DarkGray else Color.White,
        primaryButtonTextColor = if (binding.isManual) Color.White else Color.Black,
        onPrimaryButtonClick = binding.onToggleAuto
    )
}

private fun resolveEffectiveWhiteBalance(
    state: ManualControlsState,
    telemetry: CameraTelemetry,
    caps: CameraCapabilities?
): EffectiveWhiteBalance = EffectiveWhiteBalance(
    temperatureKelvin = telemetry.cctTemperatureKelvin
        ?: state.wbTemp
        ?: caps?.cctTemperatureRange?.let { (it.lower + it.upper) / 2 }
        ?: 5000,
    tint = telemetry.cctTint ?: state.wbTint ?: 0
)
