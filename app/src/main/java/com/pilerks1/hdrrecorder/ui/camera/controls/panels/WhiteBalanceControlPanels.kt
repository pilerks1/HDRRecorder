package com.pilerks1.hdrrecorder.ui.camera.controls.panels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pilerks1.hdrrecorder.camera.model.CameraCapabilities
import com.pilerks1.hdrrecorder.data.telemetry.CameraTelemetry
import com.pilerks1.hdrrecorder.ui.camera.controls.slider.AutoManualSlider
import com.pilerks1.hdrrecorder.ui.camera.controls.slider.AutoManualSliderBinding
import com.pilerks1.hdrrecorder.ui.camera.controls.slider.CameraSliderScales
import com.pilerks1.hdrrecorder.ui.camera.layout.AxisSpec
import com.pilerks1.hdrrecorder.ui.camera.state.ManualControlsState
import kotlinx.coroutines.flow.StateFlow

private data class EffectiveWhiteBalance(
    val temperatureKelvin: Int?,
    val tint: Int?
)

@Composable
internal fun WhiteBalanceControlPanel(
    state: ManualControlsState,
    caps: CameraCapabilities?,
    cameraTelemetry: StateFlow<CameraTelemetry>,
    axis: AxisSpec,
    onSetWb: (Boolean, Int?, Int?) -> Unit,
    onManualControlInteractionFinished: () -> Unit
) {
    val telemetry by cameraTelemetry.collectAsStateWithLifecycle()
        val scale = remember(caps?.cctTemperatureRange) { CameraSliderScales.whiteBalance(caps) }
        val effective = resolveEffectiveWhiteBalance(state, telemetry)
        AutoManualSlider(
            scale = scale,
            binding = whiteBalanceBinding(state, effective, editsTemperature = true, onSetWb = onSetWb),
            contentDescription = "White balance temperature",
            axis = axis,
            onInteractionFinished = onManualControlInteractionFinished
        )
}

@Composable
internal fun TintControlPanel(
    state: ManualControlsState,
    caps: CameraCapabilities?,
    cameraTelemetry: StateFlow<CameraTelemetry>,
    axis: AxisSpec,
    onSetWb: (Boolean, Int?, Int?) -> Unit,
    onManualControlInteractionFinished: () -> Unit
) {
    val telemetry by cameraTelemetry.collectAsStateWithLifecycle()
        val scale = remember { CameraSliderScales.tint() }
        val effective = resolveEffectiveWhiteBalance(state, telemetry)
        AutoManualSlider(
            scale = scale,
            binding = whiteBalanceBinding(state, effective, editsTemperature = false, onSetWb = onSetWb),
            contentDescription = "White balance tint",
            axis = axis,
            onInteractionFinished = onManualControlInteractionFinished
        )
}

/** Temperature and tint share one Auto/manual mode; editing one keeps the other's current value. */
private fun whiteBalanceBinding(
    state: ManualControlsState,
    effective: EffectiveWhiteBalance,
    editsTemperature: Boolean,
    onSetWb: (Boolean, Int?, Int?) -> Unit
) = AutoManualSliderBinding(
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

private fun resolveEffectiveWhiteBalance(
    state: ManualControlsState,
    telemetry: CameraTelemetry
): EffectiveWhiteBalance = EffectiveWhiteBalance(
    temperatureKelvin = if (state.isManualWb) state.wbTemp else telemetry.cctTemperatureKelvin,
    tint = if (state.isManualWb) state.wbTint else telemetry.cctTint
)
