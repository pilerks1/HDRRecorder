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
 * A composable for the right-hand side control column.
 * It contains all user controls: record/stop, pause/resume, settings,
 * FPS, resolution, and focus mode buttons.
 */
@Composable
fun ControlsUI(
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
        // These buttons are only visible when not recording.
        if (!uiState.isRecording) {
            Button(
                onClick = { onEvent(CameraUiEvent.CycleResolution) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) {
                Text(text = uiState.selectedResolution.qualityName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = { onEvent(CameraUiEvent.CycleFps) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) {
                Text(text = "${uiState.selectedFps} fps", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            // This spacer maintains layout consistency when the buttons are hidden.
            Spacer(modifier = Modifier.height(100.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))

        // The focus button is always visible.
        Button(
            onClick = { onEvent(CameraUiEvent.CycleFocusMode) },
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
        ) {
            Text(text = uiState.focusMode, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        // --- Main Action Buttons ---
        Spacer(modifier = Modifier.height(8.dp))

        // Record/Stop Button
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

        Spacer(modifier = Modifier.height(4.dp))

        // Pause/Resume or Settings Button
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
}
