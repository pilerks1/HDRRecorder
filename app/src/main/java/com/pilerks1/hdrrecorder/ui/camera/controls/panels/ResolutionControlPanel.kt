package com.pilerks1.hdrrecorder.ui.camera.controls.panels

import android.util.Range
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.pilerks1.hdrrecorder.camera.model.CameraCapabilities
import com.pilerks1.hdrrecorder.camera.model.ExposureMode
import com.pilerks1.hdrrecorder.camera.model.RecordingAspectRatio
import com.pilerks1.hdrrecorder.camera.model.Resolution
import com.pilerks1.hdrrecorder.ui.camera.controls.slider.FpsSlider
import com.pilerks1.hdrrecorder.ui.camera.controls.slider.SliderActionButton
import com.pilerks1.hdrrecorder.ui.camera.controls.slider.SliderPanelLayout
import com.pilerks1.hdrrecorder.ui.camera.layout.AxisSpec
import com.pilerks1.hdrrecorder.ui.camera.state.ManualControlsState
import com.pilerks1.hdrrecorder.ui.style.SliderDimensions
import kotlin.math.roundToInt

@Composable
internal fun ResolutionControlPanel(
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
        contentGap = SliderDimensions.actionEdgePadding,
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
