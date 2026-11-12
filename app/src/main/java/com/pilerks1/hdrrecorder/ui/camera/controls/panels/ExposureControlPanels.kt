package com.pilerks1.hdrrecorder.ui.camera.controls.panels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pilerks1.hdrrecorder.camera.model.CameraCapabilities
import com.pilerks1.hdrrecorder.camera.model.ExposureMode
import com.pilerks1.hdrrecorder.camera.model.LowLightBoostPolicy
import com.pilerks1.hdrrecorder.data.telemetry.CameraTelemetry
import com.pilerks1.hdrrecorder.ui.camera.controls.slider.AutoManualSlider
import com.pilerks1.hdrrecorder.ui.camera.controls.slider.BaseSlider
import com.pilerks1.hdrrecorder.ui.camera.controls.slider.CameraSliderScales
import com.pilerks1.hdrrecorder.ui.camera.controls.slider.autoManualBinding
import com.pilerks1.hdrrecorder.ui.camera.layout.AxisSpec
import com.pilerks1.hdrrecorder.ui.camera.state.ManualControlsState
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun IsoPanel(
    state: ManualControlsState,
    caps: CameraCapabilities?,
    telemetry: CameraTelemetry,
    axis: AxisSpec,
    enabled: Boolean,
    onSetIso: (Boolean, Int?) -> Unit,
    onSetPostRawSensitivity: (Boolean, Int?) -> Unit,
    onInteractionFinished: () -> Unit
) {
    val isoScale = remember(caps?.isoRange) { CameraSliderScales.iso(caps) }
    val sensorSlider: @Composable (Modifier) -> Unit = { modifier ->
        AutoManualSlider(
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
        CameraSliderScales.postRawSensitivity(caps)
    }
    val postSlider: @Composable (Modifier) -> Unit = { modifier ->
        AutoManualSlider(
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

@Composable
internal fun ShutterControlPanel(
    state: ManualControlsState,
    caps: CameraCapabilities?,
    cameraTelemetry: StateFlow<CameraTelemetry>,
    axis: AxisSpec,
    controlsEnabled: Boolean,
    onSetSs: (Boolean, Long?) -> Unit,
    onManualControlInteractionFinished: () -> Unit
) {
    val telemetry by cameraTelemetry.collectAsStateWithLifecycle()
        val scale = remember(caps?.ssRangeNanos) { CameraSliderScales.shutter(caps) }
        AutoManualSlider(
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

@Composable
internal fun ExposureCompensationControlPanel(
    state: ManualControlsState,
    caps: CameraCapabilities?,
    axis: AxisSpec,
    controlsEnabled: Boolean,
    isLowLightBoostEligible: Boolean,
    onSetEv: (Boolean, Int?) -> Unit,
    onSetLowLightBoost: (Boolean) -> Unit,
    onManualControlInteractionFinished: () -> Unit
) {
    val scale = remember(caps?.evRange, caps?.evStep) { CameraSliderScales.exposureCompensation(caps) }
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
            secondaryButtonText = if (LowLightBoostPolicy.isSupported(caps?.supportsLowLightBoost == true)) "NHT" else null,
            secondaryButtonEnabled = controlsEnabled && isLowLightBoostEligible,
            secondaryButtonColor = if (state.isLowLightBoostEnabled) Color.Yellow else Color.DarkGray,
            secondaryButtonTextColor = if (state.isLowLightBoostEnabled) Color.Black else Color.LightGray,
            onSecondaryButtonClick = if (LowLightBoostPolicy.isSupported(caps?.supportsLowLightBoost == true)) {
                {
                    onSetLowLightBoost(!state.isLowLightBoostEnabled)
                    onManualControlInteractionFinished()
                }
            } else null
        )
}
