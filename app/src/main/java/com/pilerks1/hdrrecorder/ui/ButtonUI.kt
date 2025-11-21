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

/**
 * Handles the layout of camera controls based on orientation.
 * Uses the original button styling but adapts position for Vertical vs Horizontal.
 */
@Composable
fun ControlsUI(
    uiState: CameraUiState,
    onEvent: (CameraUiEvent) -> Unit,
    isLandscape: Boolean, // Added to detect orientation
    modifier: Modifier = Modifier
) {
    if (isLandscape) {
        LandscapeControls(uiState, onEvent, modifier)
    } else {
        PortraitControls(uiState, onEvent, modifier)
    }
}

/**
 * Original Vertical Stack Layout (for Horizontal/Landscape Device Orientation).
 * Buttons are stacked in a Column on the right side.
 */
@Composable
fun LandscapeControls(
    uiState: CameraUiState,
    onEvent: (CameraUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // --- FPS, Resolution, and Focus Buttons ---
        if (!uiState.isRecording) {
            ResolutionButton(uiState, onEvent)
            Spacer(modifier = Modifier.height(4.dp))
            FpsButton(uiState, onEvent)
        } else {
            Spacer(modifier = Modifier.height(100.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))
        FocusButton(uiState, onEvent)

        // --- Main Action Buttons ---
        Spacer(modifier = Modifier.height(8.dp))
        RecordButton(uiState, onEvent)

        Spacer(modifier = Modifier.height(4.dp))
        PauseOrSettingsButton(uiState, onEvent)
    }
}

/**
 * New Horizontal Layout (for Vertical/Portrait Device Orientation).
 * Buttons are arranged at the bottom.
 * Left: Settings Group | Center: Record | Right: Pause/Settings
 */
@Composable
fun PortraitControls(
    uiState: CameraUiState,
    onEvent: (CameraUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
    ) {
        // LEFT: Resolution, FPS, Focus
        // Stacked vertically on the left side to save width
        Column(
            modifier = Modifier.align(Alignment.CenterStart),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // We keep them visible or hidden logic same as original
            if (!uiState.isRecording) {
                ResolutionButton(uiState, onEvent)
                FpsButton(uiState, onEvent)
            }
            // Focus is always visible
            FocusButton(uiState, onEvent)
        }

        // CENTER: Record Button
        Box(modifier = Modifier.align(Alignment.Center)) {
            RecordButton(uiState, onEvent)
        }

        // RIGHT: Settings or Pause
        // Added padding to push it left, closer to center
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
        ) {
            PauseOrSettingsButton(uiState, onEvent)
        }
    }
}

@Composable
fun ResolutionButton(uiState: CameraUiState, onEvent: (CameraUiEvent) -> Unit) {
    Button(
        onClick = { onEvent(CameraUiEvent.CycleResolution) },
        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
    ) {
        Text(text = uiState.selectedResolution.qualityName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FpsButton(uiState: CameraUiState, onEvent: (CameraUiEvent) -> Unit) {
    Button(
        onClick = { onEvent(CameraUiEvent.CycleFps) },
        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
    ) {
        Text(text = "${uiState.selectedFps} fps", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FocusButton(uiState: CameraUiState, onEvent: (CameraUiEvent) -> Unit) {
    Button(
        onClick = { onEvent(CameraUiEvent.CycleFocusMode) },
        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
    ) {
        Text(text = uiState.focusMode, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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