package com.pilerks1.hdrrecorder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pilerks1.hdrrecorder.ui.manualcontrols.ActiveSliderPanel
import com.pilerks1.hdrrecorder.ui.manualcontrols.ManualControlsGrid

@Composable
fun ControlsUISliders(
    uiState: CameraUiState,
    actions: CameraActions,
    isLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter
    ) {
        ActiveSliderPanel(
            state = uiState.manualControlsState,
            caps = uiState.cameraCapabilities,
            isRecording = uiState.isRecording,
            isLandscape = isLandscape,
            onClose = { actions.onManualControlsChange { it.copy(activeSlider = null) } },
            onSetIso = { isManual, value -> actions.onManualControlsChange { it.copy(isManualIso = isManual, isoValue = value ?: it.isoValue) } },
            onSetSs = { isManual, value -> actions.onManualControlsChange { it.copy(isManualSs = isManual, ssValueNanos = value ?: it.ssValueNanos) } },
            onSetEv = { isManual, value -> actions.onManualControlsChange { it.copy(isManualEv = isManual, evValueIndex = value ?: it.evValueIndex) } },
            onSetNightMode = { enabled -> actions.onManualControlsChange { it.copy(isNightModeAeEnabled = enabled) } },
            onSetFocus = { isManual, value -> actions.onManualControlsChange { it.copy(isManualFocus = isManual, focusDistanceDiopters = value ?: it.focusDistanceDiopters) } },
            onSetWb = { isManual, temp, tint -> actions.onManualControlsChange { it.copy(isManualWb = isManual, wbTemp = temp ?: it.wbTemp, wbTint = tint ?: it.wbTint) } },
            onSetFps = { isManual, range -> actions.onManualControlsChange { it.copy(isManualFps = isManual, fpsRange = range ?: it.fpsRange) } },
            autoFps = uiState.selectedFps,
            onCycleFps = {
                if (uiState.manualControlsState.isManualFps) {
                    actions.onManualControlsChange { it.copy(isManualFps = false) }
                } else {
                    actions.onEvent(CameraUiEvent.CycleFps)
                }
            }
        )
    }
}

@Composable
fun ControlsUIButtons(
    uiState: CameraUiState,
    actions: CameraActions,
    isLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    if (isLandscape) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = modifier
                .fillMaxHeight()
                .padding(top = 0.dp, bottom = 4.dp)
        ) {
            ManualControlsGrid(
                state = uiState.manualControlsState,
                caps = uiState.cameraCapabilities,
                resLabel = uiState.selectedResolution.qualityName,
                isRecording = uiState.isRecording,
                onCycleResolution = { actions.onEvent(CameraUiEvent.CycleResolution) },
                onToggleSlider = { sliderId ->
                    actions.onManualControlsChange {
                        if (it.activeSlider == sliderId) it.copy(activeSlider = null) else it.copy(activeSlider = sliderId)
                    }
                },
                isLandscape = isLandscape,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                PauseOrSettingsButton(uiState, actions.onEvent)
                RecordButton(uiState, actions.onEvent)
            }
        }
    } else {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 0.dp, end = 4.dp)
        ) {
            ManualControlsGrid(
                state = uiState.manualControlsState,
                caps = uiState.cameraCapabilities,
                resLabel = uiState.selectedResolution.qualityName,
                isRecording = uiState.isRecording,
                onCycleResolution = { actions.onEvent(CameraUiEvent.CycleResolution) },
                onToggleSlider = { sliderId ->
                    actions.onManualControlsChange {
                        if (it.activeSlider == sliderId) it.copy(activeSlider = null) else it.copy(activeSlider = sliderId)
                    }
                },
                isLandscape = isLandscape,
                modifier = Modifier.weight(1f)
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                PauseOrSettingsButton(uiState, actions.onEvent)
                RecordButton(uiState, actions.onEvent)
            }
        }
    }
}

// --- Reused Components ---

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
            imageVector = if (uiState.isRecording) Icons.Filled.Stop else Icons.Default.RadioButtonChecked,
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
                imageVector = if (uiState.isPaused) Icons.Filled.PlayArrow else Icons.Default.Pause,
                contentDescription = if (uiState.isPaused) "Play" else "Pause",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    } else {
        IconButton(onClick = { onEvent(CameraUiEvent.OpenSettings) }) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
