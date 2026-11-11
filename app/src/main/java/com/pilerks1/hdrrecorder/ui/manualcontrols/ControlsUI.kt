package com.pilerks1.hdrrecorder.ui.manualcontrols

import android.util.Range
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import com.pilerks1.hdrrecorder.data.camera.CameraCapabilities
import com.pilerks1.hdrrecorder.model.CameraTelemetry
import com.pilerks1.hdrrecorder.model.RecordingAspectRatio
import com.pilerks1.hdrrecorder.model.Resolution
import com.pilerks1.hdrrecorder.ui.CameraControlPanel
import com.pilerks1.hdrrecorder.ui.ManualControlsState
import com.pilerks1.hdrrecorder.data.camera.core.CameraZoomState
import com.pilerks1.hdrrecorder.data.camera.core.ExposureMode
import com.pilerks1.hdrrecorder.ui.layout.AxisSpec
import com.pilerks1.hdrrecorder.ui.layout.ActionRailSpec
import com.pilerks1.hdrrecorder.ui.layout.EdgeInsets
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

private data class EffectiveWhiteBalance(
    val temperatureKelvin: Int?,
    val tint: Int?
)

fun availableControlPanels(
    caps: CameraCapabilities?
): List<CameraControlPanel> = orderedControlPanels(
    hasShutter = caps?.hasManualShutterControl == true,
    hasIso = caps?.hasManualIsoControl == true,
    hasExposureCompensation = caps?.let {
        it.hasExposureCompensationControl || it.supportsLowLightBoost
    } == true,
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
    add(CameraControlPanel.ZOOM)
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

/** A panel tab: exposes selection to accessibility services. */
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
            .selectable(
                selected = isActive,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
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
    activePanel: CameraControlPanel,
    state: ManualControlsState,
    zoomState: CameraZoomState,
    onZoomRatioChange: (Float) -> Unit,
    onSelectZoomLens: (String) -> Unit,
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
    onSetPostRawSensitivity: (Boolean, Int?) -> Unit,
    onSetSs: (Boolean, Long?) -> Unit,
    onSetEv: (Boolean, Int?) -> Unit,
    onSetLowLightBoost: (Boolean) -> Unit,
    onSetFocus: (Boolean, Float?) -> Unit,
    onSetWb: (Boolean, Int?, Int?) -> Unit,
    onSetFps: (Boolean, Range<Int>?) -> Unit,
    autoFps: Int,
    onCycleFps: () -> Unit,
    controlsEnabled: Boolean,
    onManualControlInteractionFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        ) {
            when (activePanel) {
                CameraControlPanel.ZOOM -> ZoomPanel(
                    zoomState = zoomState,
                    onZoomRatioChange = onZoomRatioChange,
                    onSelectZoomLens = onSelectZoomLens,
                    axis = axis,
                )
                CameraControlPanel.RESOLUTION -> ResolutionPanel(
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
                    onSetFps = onSetFps,
                    controlsEnabled = controlsEnabled
                )
                CameraControlPanel.ISO -> {
                    val telemetry by cameraTelemetry.collectAsStateWithLifecycle()
                    IsoPanel(
                        state = state,
                        caps = caps,
                        telemetry = telemetry,
                        axis = axis,
                        enabled = controlsEnabled,
                        onSetIso = onSetIso,
                        onSetPostRawSensitivity = onSetPostRawSensitivity,
                        onInteractionFinished = onManualControlInteractionFinished
                    )
                }
                CameraControlPanel.SHUTTER -> {
                    val telemetry by cameraTelemetry.collectAsStateWithLifecycle()
                    val scale = remember(caps?.ssRangeNanos) { SliderScales.shutter(caps) }
                    StandardSlider(
                        scale = scale,
                        binding = autoManualBinding(
                            isManual = state.isManualSs,
                            effectiveValue = if (state.isManualSs) state.ssValueNanos else telemetry.shutterNanos,
                            onSet = onSetSs
                        ),
                        contentDescription = "Shutter speed",
                        axis = axis,
                        enabled = controlsEnabled,
                        onInteractionFinished = onManualControlInteractionFinished
                    )
                }
                CameraControlPanel.EXPOSURE_COMPENSATION -> {
                    val scale = remember(caps?.evRange, caps?.evStep) { SliderScales.exposureCompensation(caps) }
                    val aeIsOff = ExposureMode.resolve(state, caps) == ExposureMode.FULL_MANUAL
                    val isManualEv = state.evValueIndex != 0
                    BaseSlider(
                        value = scale.progress(state.evValueIndex),
                        axis = axis,
                        labelString = scale.formatValue(state.evValueIndex),
                        contentDescription = "Exposure compensation",
                        ruler = scale.ruler,
                        layoutLabels = scale.layoutLabels,
                        onValueChange = { onSetEv(true, scale.value(it)) },
                        onValueChangeFinished = onManualControlInteractionFinished,
                        primaryButtonText = "RES",
                        modeKey = isManualEv,
                        sliderEnabled = !aeIsOff,
                        primaryButtonColor = if (isManualEv) Color.White else Color.DarkGray,
                        primaryButtonTextColor = if (isManualEv) Color.Black else Color.LightGray,
                        onPrimaryButtonClick = {
                            onSetEv(false, 0)
                            onManualControlInteractionFinished()
                        },
                        secondaryButtonText = if (caps?.supportsLowLightBoost == true) "NHT" else null,
                        secondaryButtonEnabled = !isRecording || maxOf(
                            if (state.isManualFps) state.fpsRange?.upper ?: autoFps else autoFps,
                            0
                        ) <= 30,
                        secondaryButtonColor = if (state.isLowLightBoostEnabled) Color.Yellow else Color.DarkGray,
                        secondaryButtonTextColor = if (state.isLowLightBoostEnabled) Color.Black else Color.LightGray,
                        onSecondaryButtonClick = if (caps?.supportsLowLightBoost == true) {
                            {
                                onSetLowLightBoost(!state.isLowLightBoostEnabled)
                                onManualControlInteractionFinished()
                            }
                        } else null
                    )
                }
                CameraControlPanel.FOCUS -> {
                    val telemetry by cameraTelemetry.collectAsStateWithLifecycle()
                    val scale = remember(caps?.focusMinDistanceDiopters) { SliderScales.focus(caps) }
                    StandardSlider(
                        scale = scale,
                        binding = autoManualBinding(
                            isManual = state.isManualFocus,
                            effectiveValue = if (state.isManualFocus) {
                                state.focusDistanceDiopters
                            } else {
                                telemetry.focusDistanceDiopters
                            },
                            onSet = onSetFocus
                        ),
                        contentDescription = "Focus distance",
                        axis = axis,
                        onInteractionFinished = onManualControlInteractionFinished
                    )
                }
                CameraControlPanel.WHITE_BALANCE -> {
                    val telemetry by cameraTelemetry.collectAsStateWithLifecycle()
                    val scale = remember(caps?.cctTemperatureRange) { SliderScales.whiteBalance(caps) }
                    val effective = resolveEffectiveWhiteBalance(state, telemetry)
                    StandardSlider(
                        scale = scale,
                        binding = whiteBalanceBinding(state, effective, editsTemperature = true, onSetWb = onSetWb),
                        contentDescription = "White balance temperature",
                        axis = axis,
                        onInteractionFinished = onManualControlInteractionFinished
                    )
                }
                CameraControlPanel.TINT -> {
                    val telemetry by cameraTelemetry.collectAsStateWithLifecycle()
                    val scale = remember { SliderScales.tint() }
                    val effective = resolveEffectiveWhiteBalance(state, telemetry)
                    StandardSlider(
                        scale = scale,
                        binding = whiteBalanceBinding(state, effective, editsTemperature = false, onSetWb = onSetWb),
                        contentDescription = "White balance tint",
                        axis = axis,
                        onInteractionFinished = onManualControlInteractionFinished
                    )
                }
            }
        }
    }
}

/** Auto/manual toggle for one control: Auto releases the value; manual holds the effective value. */
private fun <T : Any> autoManualBinding(
    isManual: Boolean,
    effectiveValue: T?,
    onSet: (Boolean, T?) -> Unit
) = StandardSliderBinding(
    isManual = isManual,
    effectiveValue = effectiveValue,
    onToggleAuto = {
        if (isManual) onSet(false, null) else effectiveValue?.let { onSet(true, it) }
    },
    onManualValue = { onSet(true, it) }
)

/** Temperature and tint share one Auto/manual mode; editing one keeps the other's current value. */
private fun whiteBalanceBinding(
    state: ManualControlsState,
    effective: EffectiveWhiteBalance,
    editsTemperature: Boolean,
    onSetWb: (Boolean, Int?, Int?) -> Unit
) = StandardSliderBinding(
    isManual = state.isManualWb,
    effectiveValue = if (editsTemperature) effective.temperatureKelvin else effective.tint,
    onToggleAuto = {
        val temperature = effective.temperatureKelvin
        val tint = effective.tint
        if (state.isManualWb) onSetWb(false, null, null)
        else if (temperature != null && tint != null) onSetWb(true, temperature, tint)
    },
    onManualValue = { value ->
        if (editsTemperature) effective.tint?.let { onSetWb(true, value, it) }
        else effective.temperatureKelvin?.let { onSetWb(true, it, value) }
    }
)

@Composable
private fun ZoomPanel(
    zoomState: CameraZoomState,
    onZoomRatioChange: (Float) -> Unit,
    onSelectZoomLens: (String) -> Unit,
    axis: AxisSpec
) {
    val scale = remember(zoomState.minRatio, zoomState.maxRatio, zoomState.inventory) { SliderScales.zoom(zoomState) }
    val lenses = zoomState.shortcuts
    SliderPanelLayout(
        axis = axis,
        actionCount = lenses.size,
        modifier = Modifier.fillMaxSize(),
        actions = {
            lenses.forEach { lens ->
                val lensRatio = lens.zoomRatio
                val selected = kotlin.math.abs(zoomState.ratio - lensRatio) < 0.005f
                SliderActionButton(
                    text = SliderScales.formatZoomFactor(lensRatio),
                    background = if (selected) Color.White else Color.DarkGray,
                    textColor = if (selected) Color.Black else Color.White,
                    onClick = { onSelectZoomLens(lens.cameraId) }
                )
            }
        },
        content = { axisReservation ->
            RibbonSlider(
                value = scale.progress(zoomState.ratio),
                onValueChange = { progress ->
                    onZoomRatioChange(scale.value(progress))
                },
                axis = axis,
                labelString = if (zoomState.isReady) scale.formatValue(zoomState.ratio) else SliderScales.UNAVAILABLE_LABEL,
                contentDescription = "Zoom",
                ruler = scale.ruler,
                layoutLabels = scale.layoutLabels,
                axisReservation = axisReservation,
                modifier = Modifier.fillMaxSize()
            )
        }
    )
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
    onSetFps: (Boolean, Range<Int>?) -> Unit,
    controlsEnabled: Boolean
) {
    val currentRange = state.fpsRange ?: Range(autoFps, autoFps)
    SliderPanelLayout(
        axis = axis,
        actionCount = 3,
        modifier = Modifier.fillMaxSize(),
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
        content = { axisReservation ->
            FpsSlider(
                isManualFps = state.isManualFps,
                currentRange = currentRange.lower.toFloat()..currentRange.upper.toFloat(),
                isRecording = isRecording,
                isExposureFullyManual = ExposureMode.resolve(state, caps) == ExposureMode.FULL_MANUAL,
                caps = caps,
                onValueChange = { newRange ->
                    onSetFps(true, Range(newRange.start.roundToInt(), newRange.endInclusive.roundToInt()))
                },
                axis = axis,
                axisReservation = axisReservation,
                enabled = controlsEnabled
            )
        }
    )
}

@Composable
private fun <T : Any> StandardSlider(
    scale: SliderScale<T>,
    binding: StandardSliderBinding<T>,
    contentDescription: String,
    axis: AxisSpec,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onInteractionFinished: () -> Unit
) {
    val effectiveValue = binding.effectiveValue
    val hasValue = effectiveValue != null
    BaseSlider(
        value = effectiveValue?.let(scale::progress) ?: 0f,
        axis = axis,
        labelString = effectiveValue?.let(scale.formatValue) ?: SliderScales.UNAVAILABLE_LABEL,
        contentDescription = contentDescription,
        ruler = if (hasValue) scale.ruler else scale.ruler.copy(ticks = emptyList(), markers = emptyList()),
        layoutLabels = if (hasValue) scale.layoutLabels else listOf(SliderScales.UNAVAILABLE_LABEL),
        onValueChange = { binding.onManualValue(scale.value(it)) },
        onValueChangeFinished = onInteractionFinished,
        modifier = modifier,
        sliderEnabled = enabled && hasValue,
        primaryButtonEnabled = enabled && (hasValue || binding.isManual),
        primaryButtonText = if (binding.isManual) "MAN" else "AUTO",
        modeKey = binding.isManual,
        primaryButtonColor = if (binding.isManual) Color.DarkGray else Color.White,
        primaryButtonTextColor = if (binding.isManual) Color.White else Color.Black,
        onPrimaryButtonClick = {
            binding.onToggleAuto()
            onInteractionFinished()
        }
    )
}

@Composable
private fun IsoPanel(
    state: ManualControlsState,
    caps: CameraCapabilities?,
    telemetry: CameraTelemetry,
    axis: AxisSpec,
    enabled: Boolean,
    onSetIso: (Boolean, Int?) -> Unit,
    onSetPostRawSensitivity: (Boolean, Int?) -> Unit,
    onInteractionFinished: () -> Unit
) {
    val isoScale = remember(caps?.isoRange) { SliderScales.iso(caps) }
    val sensorSlider: @Composable (Modifier) -> Unit = { modifier ->
        StandardSlider(
            scale = isoScale,
            binding = autoManualBinding(
                isManual = state.isManualIso,
                effectiveValue = if (state.isManualIso) state.isoValue else telemetry.iso,
                onSet = onSetIso
            ),
            contentDescription = "ISO",
            axis = axis,
            modifier = modifier,
            enabled = enabled,
            onInteractionFinished = onInteractionFinished
        )
    }

    if (caps?.supportsPostRawSensitivityBoost != true) {
        sensorSlider(Modifier.fillMaxSize())
        return
    }

    val postScale = remember(caps.postRawSensitivityBoostRange) {
        SliderScales.postRawSensitivity(caps)
    }
    val postSlider: @Composable (Modifier) -> Unit = { modifier ->
        StandardSlider(
            scale = postScale,
            binding = autoManualBinding(
                isManual = state.isManualPostRawSensitivity,
                effectiveValue = if (state.isManualPostRawSensitivity) {
                    state.postRawSensitivityBoost
                } else {
                    telemetry.postRawSensitivityBoost ?: 100
                },
                onSet = onSetPostRawSensitivity
            ),
            contentDescription = "Post-RAW sensitivity boost",
            axis = axis,
            modifier = modifier,
            enabled = enabled,
            onInteractionFinished = onInteractionFinished
        )
    }

    if (axis.usesVerticalTrack) {
        Row(Modifier.fillMaxSize()) {
            postSlider(Modifier.weight(1f))
            sensorSlider(Modifier.weight(1f))
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            postSlider(Modifier.weight(1f))
            sensorSlider(Modifier.weight(1f))
        }
    }
}

private fun resolveEffectiveWhiteBalance(
    state: ManualControlsState,
    telemetry: CameraTelemetry
): EffectiveWhiteBalance = EffectiveWhiteBalance(
    temperatureKelvin = if (state.isManualWb) state.wbTemp else telemetry.cctTemperatureKelvin,
    tint = if (state.isManualWb) state.wbTint else telemetry.cctTint
)
