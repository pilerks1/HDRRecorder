package com.pilerks1.hdrrecorder.ui.manualcontrols

import android.util.Range
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.pilerks1.hdrrecorder.data.camera.core.CameraZoomState
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
    zoomState: CameraZoomState,
    onLinearZoomChange: (Float) -> Unit,
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
    controlsEnabled: Boolean = true,
    onManualControlInteractionFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activePanel = state.activePanel ?: return
    val telemetry = cameraTelemetry.collectAsStateWithLifecycle().value
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
            if (activePanel == CameraControlPanel.ZOOM) {
                ZoomPanel(
                    zoomState = zoomState,
                    onLinearZoomChange = onLinearZoomChange,
                    onSelectZoomLens = onSelectZoomLens,
                    axis = axis,
                )
                return@panelContent
            }

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
                    onSetFps = onSetFps,
                    controlsEnabled = controlsEnabled
                )
                return@panelContent
            }

            when (activePanel.manualControl) {
            ManualControl.ISO -> {
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
            ManualControl.SHUTTER -> {
                val scale = remember(caps?.ssRangeNanos) { SliderScales.shutter(caps) }
                val effective = if (state.isManualSs) state.ssValueNanos else telemetry.shutterNanos
                StandardSlider(
                    scale = scale,
                    binding = StandardSliderBinding(
                        isManual = state.isManualSs,
                        effectiveValue = effective,
                        onToggleAuto = {
                            if (state.isManualSs) {
                                onSetSs(false, null)
                            } else {
                                effective?.let { onSetSs(true, it) }
                            }
                        },
                        onManualValue = { onSetSs(true, it) }
                    ),
                    axis = axis,
                    enabled = controlsEnabled,
                    onInteractionFinished = onManualControlInteractionFinished
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
                    onValueChangeFinished = onManualControlInteractionFinished,
                    primaryButtonText = "RES",
                    sliderEnabled = !aeIsOff,
                    primaryButtonEnabled = true,
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
            ManualControl.FOCUS -> {
                val scale = remember(caps?.focusMinDistanceDiopters) { SliderScales.focus(caps) }
                val effective = if (state.isManualFocus) {
                    state.focusDistanceDiopters
                } else {
                    telemetry.focusDistanceDiopters
                }
                StandardSlider(
                    scale = scale,
                    binding = StandardSliderBinding(
                        isManual = state.isManualFocus,
                        effectiveValue = effective,
                        onToggleAuto = {
                            if (state.isManualFocus) {
                                onSetFocus(false, null)
                            } else {
                                effective?.let { onSetFocus(true, it) }
                            }
                        },
                        onManualValue = { onSetFocus(true, it) }
                    ),
                    axis = axis,
                    onInteractionFinished = onManualControlInteractionFinished
                )
            }
            ManualControl.WHITE_BALANCE -> {
                val scale = remember(caps?.cctTemperatureRange) { SliderScales.whiteBalance(caps) }
                val effective = resolveEffectiveWhiteBalance(state, telemetry)
                StandardSlider(
                    scale = scale,
                    binding = StandardSliderBinding(
                        isManual = state.isManualWb,
                        effectiveValue = effective.temperatureKelvin,
                        onToggleAuto = {
                            if (state.isManualWb) {
                                onSetWb(false, null, null)
                            } else {
                                val temperature = effective.temperatureKelvin
                                val tint = effective.tint
                                if (temperature != null && tint != null) {
                                    onSetWb(true, temperature, tint)
                                }
                            }
                        },
                        onManualValue = { temperature ->
                            effective.tint?.let { tint -> onSetWb(true, temperature, tint) }
                        }
                    ),
                    axis = axis,
                    onInteractionFinished = onManualControlInteractionFinished
                )
            }
            ManualControl.TINT -> {
                val scale = remember { SliderScales.tint() }
                val effective = resolveEffectiveWhiteBalance(state, telemetry)
                StandardSlider(
                    scale = scale,
                    binding = StandardSliderBinding(
                        isManual = state.isManualWb,
                        effectiveValue = effective.tint,
                        onToggleAuto = {
                            if (state.isManualWb) {
                                onSetWb(false, null, null)
                            } else {
                                val temperature = effective.temperatureKelvin
                                val tint = effective.tint
                                if (temperature != null && tint != null) {
                                    onSetWb(true, temperature, tint)
                                }
                            }
                        },
                        onManualValue = { tint ->
                            effective.temperatureKelvin?.let { temperature ->
                                onSetWb(true, temperature, tint)
                            }
                        }
                    ),
                    axis = axis,
                    onInteractionFinished = onManualControlInteractionFinished
                )
            }
                null -> Unit
            }
        }
    }
}

@Composable
private fun ZoomPanel(
    zoomState: CameraZoomState,
    onLinearZoomChange: (Float) -> Unit,
    onSelectZoomLens: (String) -> Unit,
    axis: AxisSpec
) {
    val scale = remember(zoomState.minRatio, zoomState.maxRatio, zoomState.inventory) { SliderScales.zoom(zoomState) }
    val lenses = zoomState.inventory?.buttonLenses.orEmpty()
    SliderPanelLayout(
        axis = axis,
        actionCount = lenses.size,
        modifier = Modifier.fillMaxSize(),
        actions = {
            lenses.forEach { lens ->
                val lensRatio = checkNotNull(lens.zoomRatio)
                val selected = kotlin.math.abs(zoomState.ratio - lensRatio) < 0.005f
                SliderActionButton(
                    text = SliderScales.formatZoomFactor(lensRatio),
                    background = if (selected) Color.White else Color.DarkGray,
                    textColor = if (selected) Color.Black else Color.White,
                    onClick = { onSelectZoomLens(lens.cameraId) }
                )
            }
        },
        content = { sliderModifier, axisReservation ->
            RibbonSlider(
                value = scale.progress(zoomState.ratio),
                onValueChange = onLinearZoomChange,
                axis = axis,
                labelString = if (zoomState.isReady) scale.formatValue(zoomState.ratio) else "—",
                ticks = scale.ticks,
                layoutLabels = scale.layoutLabels,
                axisReservation = axisReservation,
                modifier = sliderModifier.fillMaxSize()
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
                isExposureFullyManual = state.isManualIso &&
                    state.isManualSs &&
                    caps?.supportsFullManualExposure == true,
                caps = caps,
                onValueChange = { newRange ->
                    onSetFps(true, Range(newRange.start.roundToInt(), newRange.endInclusive.roundToInt()))
                },
                axis = axis,
                axisReservation = axisReservation,
                enabled = controlsEnabled,
                modifier = sliderModifier
            )
        }
    )
}

@Composable
private fun <T : Any> StandardSlider(
    scale: SliderScale<T>,
    binding: StandardSliderBinding<T>,
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
        labelString = effectiveValue?.let(scale.formatValue) ?: "—",
        ticks = if (hasValue) scale.ticks else emptyList(),
        layoutLabels = if (hasValue) scale.layoutLabels else listOf("—"),
        onValueChange = { binding.onManualValue(scale.value(it)) },
        onValueChangeFinished = onInteractionFinished,
        modifier = modifier,
        sliderEnabled = enabled && hasValue,
        primaryButtonEnabled = enabled && (hasValue || binding.isManual),
        primaryButtonText = if (binding.isManual) "MAN" else "AUTO",
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
    val effectiveIso = if (state.isManualIso) state.isoValue else telemetry.iso
    val sensorSlider: @Composable (Modifier) -> Unit = { modifier ->
        StandardSlider(
            scale = isoScale,
            binding = StandardSliderBinding(
                isManual = state.isManualIso,
                effectiveValue = effectiveIso,
                onToggleAuto = {
                    if (state.isManualIso) {
                        onSetIso(false, null)
                    } else {
                        effectiveIso?.let { onSetIso(true, it) }
                    }
                },
                onManualValue = { onSetIso(true, it) }
            ),
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
    val effectivePost = if (state.isManualPostRawSensitivity) {
        state.postRawSensitivityBoost
    } else {
        telemetry.postRawSensitivityBoost
    }
    val postSlider: @Composable (Modifier) -> Unit = { modifier ->
        StandardSlider(
            scale = postScale,
            binding = StandardSliderBinding(
                isManual = state.isManualPostRawSensitivity,
                effectiveValue = effectivePost,
                onToggleAuto = {
                    if (state.isManualPostRawSensitivity) {
                        onSetPostRawSensitivity(false, null)
                    } else {
                        effectivePost?.let { onSetPostRawSensitivity(true, it) }
                    }
                },
                onManualValue = { onSetPostRawSensitivity(true, it) }
            ),
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
