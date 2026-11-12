package com.pilerks1.hdrrecorder.ui.camera.controls

import android.util.Range
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pilerks1.hdrrecorder.camera.model.CameraCapabilities
import com.pilerks1.hdrrecorder.camera.model.CameraZoomState
import com.pilerks1.hdrrecorder.camera.model.RecordingAspectRatio
import com.pilerks1.hdrrecorder.camera.model.Resolution
import com.pilerks1.hdrrecorder.data.telemetry.CameraTelemetry
import com.pilerks1.hdrrecorder.ui.camera.controls.panels.ExposureCompensationControlPanel
import com.pilerks1.hdrrecorder.ui.camera.controls.panels.FocusControlPanel
import com.pilerks1.hdrrecorder.ui.camera.controls.panels.IsoPanel
import com.pilerks1.hdrrecorder.ui.camera.controls.panels.ResolutionControlPanel
import com.pilerks1.hdrrecorder.ui.camera.controls.panels.ShutterControlPanel
import com.pilerks1.hdrrecorder.ui.camera.controls.panels.TintControlPanel
import com.pilerks1.hdrrecorder.ui.camera.controls.panels.WhiteBalanceControlPanel
import com.pilerks1.hdrrecorder.ui.camera.controls.panels.ZoomControlPanel
import com.pilerks1.hdrrecorder.ui.camera.layout.AxisSpec
import com.pilerks1.hdrrecorder.ui.camera.layout.EdgeInsets
import com.pilerks1.hdrrecorder.ui.camera.state.ManualControlsState
import kotlinx.coroutines.flow.StateFlow

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
    isLowLightBoostEligible: Boolean,
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
                CameraControlPanel.ZOOM -> ZoomControlPanel(
                    zoomState = zoomState,
                    onZoomRatioChange = onZoomRatioChange,
                    onSelectZoomLens = onSelectZoomLens,
                    axis = axis,
                )
                CameraControlPanel.RESOLUTION -> ResolutionControlPanel(
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
                CameraControlPanel.SHUTTER -> ShutterControlPanel(
                    state, caps, cameraTelemetry, axis, controlsEnabled, onSetSs, onManualControlInteractionFinished
                )
                CameraControlPanel.EXPOSURE_COMPENSATION -> ExposureCompensationControlPanel(
                    state, caps, axis, controlsEnabled, isLowLightBoostEligible, onSetEv, onSetLowLightBoost, onManualControlInteractionFinished
                )
                CameraControlPanel.FOCUS -> FocusControlPanel(
                    state, caps, cameraTelemetry, axis, onSetFocus, onManualControlInteractionFinished
                )
                CameraControlPanel.WHITE_BALANCE -> WhiteBalanceControlPanel(
                    state, caps, cameraTelemetry, axis, onSetWb, onManualControlInteractionFinished
                )
                CameraControlPanel.TINT -> TintControlPanel(
                    state, caps, cameraTelemetry, axis, onSetWb, onManualControlInteractionFinished
                )
            }
        }
    }
}
