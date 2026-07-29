package com.pilerks1.hdrrecorder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pilerks1.hdrrecorder.ui.layout.AxisSpec
import com.pilerks1.hdrrecorder.ui.layout.AxisStack
import com.pilerks1.hdrrecorder.ui.layout.ActionRailSpec
import com.pilerks1.hdrrecorder.ui.layout.EdgeInsets
import com.pilerks1.hdrrecorder.ui.manualcontrols.ActiveControlPanel
import com.pilerks1.hdrrecorder.ui.manualcontrols.SecondaryControlsRail
import com.pilerks1.hdrrecorder.ui.manualcontrols.SliderPaneStyle
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
        controlsEnabled = !uiState.isExposureTransitioning,
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
    AxisStack(axis = axis, modifier = modifier, spacing = 0.dp) {
        if (axis.usesVerticalTrack) {
            RecordButton(uiState, actions.onEvent)
            PauseOrSettingsButton(uiState, actions.onEvent)
        } else {
            PauseOrSettingsButton(uiState, actions.onEvent)
            RecordButton(uiState, actions.onEvent)
        }
    }
}

@Composable
fun RecordButton(uiState: CameraUiState, onEvent: (CameraUiEvent) -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clickable(enabled = uiState.isCameraReady) {
                if (!uiState.isSettingsSheetVisible) {
                    onEvent(CameraUiEvent.ToggleRecording)
                }
            }
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
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clickable { onEvent(CameraUiEvent.TogglePause) }
        ) {
            Icon(
                imageVector = if (uiState.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = if (uiState.isPaused) "Play" else "Pause",
                tint = Color.White,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(32.dp)
                .clickable { onEvent(CameraUiEvent.OpenSettings) }
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = Color.White,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ManualResetButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                color = Color.Black.copy(alpha = if (enabled) 0.5f else 0.25f),
                shape = RoundedCornerShape(2.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "RES",
            color = if (enabled) Color.White else Color.Gray,
            style = SliderPaneStyle.tickTextStyle.copy(
                color = if (enabled) Color.White else Color.Gray,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center
        )
    }
}
