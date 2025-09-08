package com.pilerks1.hdrrecorder.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * The main entry point for the Camera UI.
 * This composable is now a clean container. It observes state from the ViewModel
 * and composes the smaller, stateless UI components (Stats, Preview, Controls).
 */
@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun CameraUI(viewModel: CameraViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    // --- Effects ---
    LaunchedEffect(uiState.selectedResolution, uiState.selectedFps, uiState.gammaMode) {
        viewModel.startCamera(lifecycleOwner, previewView.surfaceProvider)
    }

    // --- System UI Management ---
    SystemUiManagement()
    ScreenTimeoutManagement(isRecording = uiState.isRecording)
    ScreenOrientationManagement(isRecording = uiState.isRecording)

    // --- Main UI Layout ---
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {

                StatsUI(
                    shutterSpeed = uiState.shutterSpeed,
                    iso = uiState.iso,
                    isRecording = uiState.isRecording,
                    effectiveFps = uiState.effectiveFps,
                    droppedFrames = uiState.droppedFrames,
                    addedFrames = uiState.addedFrames,
                    modifier = Modifier.weight(1f)
                )

                PreviewUI(
                    previewView = previewView,
                    recordingTime = uiState.recordingTime,
                    isRecording = uiState.isRecording,
                    onEvent = viewModel::onEvent,
                    modifier = Modifier.weight(3f)
                )

                ControlsUI(
                    uiState = uiState,
                    onEvent = viewModel::onEvent,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Settings Screen Overlay
        if (uiState.isSettingsSheetVisible) {
            SettingsUI(
                gammaMode = uiState.gammaMode,
                onGammaChange = { viewModel.onEvent(CameraUiEvent.CycleGammaMode) },
                noiseReductionEnabled = uiState.isNoiseReductionEnabled,
                onNoiseReductionChange = { viewModel.onEvent(CameraUiEvent.SetNoiseReduction(it)) },
                onClose = { viewModel.onEvent(CameraUiEvent.CloseSettings) }
            )
        }
    }
}

// --- Helper Composables for System Management ---

@Composable
private fun SystemUiManagement() {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        DisposableEffect(Unit) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            onDispose {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

@Composable
private fun ScreenTimeoutManagement(isRecording: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        LaunchedEffect(isRecording) {
            if (isRecording) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}

@Composable
private fun ScreenOrientationManagement(isRecording: Boolean) {
    val context = LocalContext.current
    val activity = context as Activity
    LaunchedEffect(isRecording) {
        if (isRecording) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
