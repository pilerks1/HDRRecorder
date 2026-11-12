package com.pilerks1.hdrrecorder.ui.camera.controls.panels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pilerks1.hdrrecorder.camera.model.CameraCapabilities
import com.pilerks1.hdrrecorder.data.telemetry.CameraTelemetry
import com.pilerks1.hdrrecorder.ui.camera.controls.slider.AutoManualSlider
import com.pilerks1.hdrrecorder.ui.camera.controls.slider.CameraSliderScales
import com.pilerks1.hdrrecorder.ui.camera.controls.slider.autoManualBinding
import com.pilerks1.hdrrecorder.ui.camera.layout.AxisSpec
import com.pilerks1.hdrrecorder.ui.camera.state.ManualControlsState
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun FocusControlPanel(
    state: ManualControlsState,
    caps: CameraCapabilities?,
    cameraTelemetry: StateFlow<CameraTelemetry>,
    axis: AxisSpec,
    onSetFocus: (Boolean, Float?) -> Unit,
    onManualControlInteractionFinished: () -> Unit
) {
    val telemetry by cameraTelemetry.collectAsStateWithLifecycle()
        val scale = remember(caps?.focusMinDistanceDiopters) { CameraSliderScales.focus(caps) }
        AutoManualSlider(
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
