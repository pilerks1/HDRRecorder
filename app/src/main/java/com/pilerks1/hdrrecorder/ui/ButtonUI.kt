package com.pilerks1.hdrrecorder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ControlsUI(
    uiState: CameraUiState,
    onEvent: (CameraUiEvent) -> Unit,
    isLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    if (isLandscape) {
        // --- LANDSCAPE MODE ---
        // Container: Pushes content to the Right (End)
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(end = 8.dp), // Padding from the charging port
            contentAlignment = Alignment.CenterEnd
        ) {
            // Group: Buttons are centered horizontally relative to each other
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp) // TIGHT GROUPING
            ) {
                // ORDER: Top -> Bottom
                if (!uiState.isRecording) {
                    ResolutionButton(uiState, onEvent)
                    FpsButton(uiState, onEvent)
                }
                FocusButton(uiState, onEvent)
                RecordButton(uiState, onEvent)
                PauseOrSettingsButton(uiState, onEvent)
            }
        }
    } else {
        // --- PORTRAIT MODE ---
        // Container: Pushes content to the Bottom
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(bottom = 8.dp), // Padding from the charging port
            contentAlignment = Alignment.BottomCenter
        ) {
            // Group: Buttons are centered vertically relative to each other (Fixes the jagged look)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp) // TIGHT GROUPING
            ) {
                // ORDER: Left -> Right
                // To make this "Right to Left", simply reverse the order of these calls:

                PauseOrSettingsButton(uiState, onEvent)
                RecordButton(uiState, onEvent)
                FocusButton(uiState, onEvent)
                if (!uiState.isRecording) {
                    FpsButton(uiState, onEvent)
                    ResolutionButton(uiState, onEvent)
                }
            }
        }
    }
}

// --- Reused Components ---

@Composable
fun ResolutionButton(uiState: CameraUiState, onEvent: (CameraUiEvent) -> Unit) {
    Button(
        onClick = { onEvent(CameraUiEvent.CycleResolution) },
        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.defaultMinSize(minWidth = 60.dp)
    ) {
        Text(
            text = uiState.selectedResolution.qualityName,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun FpsButton(uiState: CameraUiState, onEvent: (CameraUiEvent) -> Unit) {
    Button(
        onClick = { onEvent(CameraUiEvent.CycleFps) },
        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.defaultMinSize(minWidth = 60.dp)
    ) {
        Text(
            text = "${uiState.selectedFps} fps",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun FocusButton(uiState: CameraUiState, onEvent: (CameraUiEvent) -> Unit) {
    Button(
        onClick = { onEvent(CameraUiEvent.CycleFocusMode) },
        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.defaultMinSize(minWidth = 60.dp)
    ) {
        Text(
            text = uiState.focusMode,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
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
        modifier = Modifier.size(84.dp)
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
                modifier = Modifier.size(48.dp)
            )
        }
    } else {
        IconButton(onClick = { onEvent(CameraUiEvent.OpenSettings) }) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}