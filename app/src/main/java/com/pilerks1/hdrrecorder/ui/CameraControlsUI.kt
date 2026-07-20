package com.pilerks1.hdrrecorder.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pilerks1.hdrrecorder.ui.layout.AxisSpec
import com.pilerks1.hdrrecorder.ui.layout.AxisStack
import com.pilerks1.hdrrecorder.ui.layout.ActionRailSpec
import com.pilerks1.hdrrecorder.ui.layout.EdgeInsets
import com.pilerks1.hdrrecorder.ui.manualcontrols.ActiveControlPanel
import com.pilerks1.hdrrecorder.ui.manualcontrols.SecondaryControlsRail
import com.pilerks1.hdrrecorder.ui.manualcontrols.availableControlPanels

@Composable
fun ExpandedControlPanelUI(
    uiState: CameraUiState,
    actions: CameraActions,
    axis: AxisSpec,
    contentInsets: EdgeInsets,
    modifier: Modifier = Modifier
) {
    ActiveControlPanel(
        state = uiState.manualControlsState,
        caps = uiState.cameraCapabilities,
        cameraTelemetry = actions.cameraTelemetry,
        isRecording = uiState.isRecording,
        selectedResolution = uiState.selectedResolution,
        selectedAspectRatio = uiState.selectedAspectRatio,
        axis = axis,
        contentInsets = contentInsets,
        onCycleResolution = { actions.onEvent(CameraUiEvent.CycleResolution) },
        onCycleAspectRatio = { actions.onEvent(CameraUiEvent.CycleAspectRatio) },
        onSetIso = { isManual, value -> actions.onManualControlsChange { it.copy(isManualIso = isManual, isoValue = value) } },
        onSetSs = { isManual, value -> actions.onManualControlsChange { it.copy(isManualSs = isManual, ssValueNanos = value) } },
        onSetEv = { isManual, value -> actions.onManualControlsChange { it.copy(isManualEv = isManual, evValueIndex = value ?: 0) } },
        onSetNightMode = { enabled -> actions.onManualControlsChange { it.copy(isNightModeAeEnabled = enabled) } },
        onSetFocus = { isManual, value -> actions.onManualControlsChange { it.copy(isManualFocus = isManual, focusDistanceDiopters = value) } },
        onSetWb = { isManual, temp, tint -> actions.onManualControlsChange { it.copy(isManualWb = isManual, wbTemp = temp, wbTint = tint) } },
        onSetFps = { isManual, range -> actions.onManualControlsChange { it.copy(isManualFps = isManual, fpsRange = range) } },
        autoFps = uiState.selectedFps,
        onCycleFps = {
            if (uiState.manualControlsState.isManualFps) {
                actions.onManualControlsChange { it.copy(isManualFps = false) }
            } else {
                actions.onEvent(CameraUiEvent.CycleFps)
            }
        },
        modifier = modifier
    )
}

@Composable
fun SecondaryControlsUI(
    uiState: CameraUiState,
    actions: CameraActions,
    axis: AxisSpec,
    railSpec: ActionRailSpec,
    modifier: Modifier = Modifier
) {
    val panels = availableControlPanels(uiState.cameraCapabilities)
    SecondaryControlsRail(
        panels = panels,
        activePanel = uiState.manualControlsState.activePanel,
        axis = axis,
        railSpec = railSpec,
        onTogglePanel = { panel ->
            actions.onManualControlsChange {
                it.copy(activePanel = if (it.activePanel == panel) null else panel)
            }
        },
        modifier = modifier
    )
}

@Composable
fun PrimaryActionsUI(
    uiState: CameraUiState,
    actions: CameraActions,
    axis: AxisSpec,
    modifier: Modifier = Modifier
) {
    if (uiState.manualControlsState.activePanel != null) return

    AxisStack(axis = axis, modifier = modifier, spacing = 0.dp) {
        PauseOrSettingsButton(uiState, actions.onEvent)
        RecordButton(uiState, actions.onEvent)
    }
}

@Composable
fun RecordButton(uiState: CameraUiState, onEvent: (CameraUiEvent) -> Unit) {
    IconButton(
        enabled = uiState.isCameraReady,
        onClick = {
            if (uiState.isSettingsSheetVisible) return@IconButton
            onEvent(CameraUiEvent.ToggleRecording)
        },
        modifier = Modifier.size(64.dp)
    ) {
        Icon(
            imageVector = if (uiState.isRecording) Icons.Filled.Stop else Icons.Filled.RadioButtonChecked,
            contentDescription = if (uiState.isRecording) "Stop" else "Record",
            tint = if (uiState.isRecording) Color.White else Color.Red,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun PauseOrSettingsButton(uiState: CameraUiState, onEvent: (CameraUiEvent) -> Unit) {
    if (uiState.isRecording) {
        IconButton(onClick = { onEvent(CameraUiEvent.TogglePause) }) {
            Icon(
                imageVector = if (uiState.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = if (uiState.isPaused) "Play" else "Pause",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    } else {
        IconButton(onClick = { onEvent(CameraUiEvent.OpenSettings) }) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
