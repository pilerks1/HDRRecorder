package com.pilerks1.hdrrecorder.ui.camera.controls

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pilerks1.hdrrecorder.ui.camera.layout.CameraLayoutSpec
import com.pilerks1.hdrrecorder.ui.camera.layout.EdgeInsets
import com.pilerks1.hdrrecorder.ui.camera.state.CameraActions
import com.pilerks1.hdrrecorder.ui.camera.state.CameraUiEvent
import com.pilerks1.hdrrecorder.ui.camera.state.CameraUiState

@Composable
internal fun CameraControlPanelContent(
    activePanel: CameraControlPanel,
    uiState: CameraUiState,
    actions: CameraActions,
    layoutSpec: CameraLayoutSpec,
    contentInsets: EdgeInsets
) {
    ActiveControlPanel(
        activePanel = activePanel,
        state = uiState.manualControlsState,
        zoomState = uiState.zoomState,
        onZoomRatioChange = { actions.onEvent(CameraUiEvent.SetZoomRatio(it)) },
        onSelectZoomLens = { actions.onEvent(CameraUiEvent.SelectZoomLens(it)) },
        caps = uiState.cameraCapabilities,
        cameraTelemetry = actions.sliderTelemetry,
        isRecording = uiState.isRecording,
        selectedResolution = uiState.selectedResolution,
        selectedAspectRatio = uiState.selectedAspectRatio,
        axis = layoutSpec.axis,
        contentInsets = contentInsets,
        onCycleResolution = { actions.onEvent(CameraUiEvent.CycleResolution) },
        onCycleAspectRatio = { actions.onEvent(CameraUiEvent.CycleAspectRatio) },
        onSetIso = { isManual, value -> actions.onManualControlsChange { it.copy(isManualIso = isManual, isoValue = value) } },
        onSetPostRawSensitivity = { isManual, value ->
            actions.onManualControlsChange {
                it.copy(
                    isManualPostRawSensitivity = isManual,
                    postRawSensitivityBoost = value
                )
            }
        },
        onSetSs = { isManual, value -> actions.onManualControlsChange { it.copy(isManualSs = isManual, ssValueNanos = value) } },
        onSetEv = { isManual, value -> actions.onManualControlsChange { it.copy(isManualEv = isManual, evValueIndex = value ?: 0) } },
        onSetLowLightBoost = { enabled -> actions.onEvent(CameraUiEvent.SetLowLightBoost(enabled)) },
        isLowLightBoostEligible = uiState.isCameraReady && uiState.isLowLightBoostEligible,
        onSetFocus = { isManual, value -> actions.onManualControlsChange { it.copy(isManualFocus = isManual, focusDistanceDiopters = value) } },
        onSetWb = { isManual, temp, tint -> actions.onManualControlsChange { it.copy(isManualWb = isManual, wbTemp = temp, wbTint = tint) } },
        onSetFps = { isManual, range ->
            actions.onManualControlsChange {
                it.copy(isManualFps = isManual, fpsRange = range)
            }
            actions.onManualControlInteractionFinished()
        },
        autoFps = uiState.selectedFps,
        onCycleFps = {
            if (uiState.manualControlsState.isManualFps) {
                actions.onManualControlsChange { it.copy(isManualFps = false) }
            } else {
                actions.onEvent(CameraUiEvent.CycleFps)
            }
        },
        controlsEnabled = !uiState.isExposureTransitioning,
        onManualControlInteractionFinished = actions.onManualControlInteractionFinished,
        modifier = Modifier.fillMaxSize()
    )
}
