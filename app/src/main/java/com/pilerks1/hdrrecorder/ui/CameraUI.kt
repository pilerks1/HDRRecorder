package com.pilerks1.hdrrecorder.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
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
fun CameraUI(
    viewModel: CameraViewModel = viewModel(),
    onNavigateToCompatibility: () -> Unit
) {

    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val surfaceRequest by viewModel.surfaceRequest.collectAsState()

    // --- Effects ---
    // Added isSdrToneMapEnabled to keys: Toggling Hack 1 requires a camera restart (builder change)
    LaunchedEffect(
        uiState.selectedResolution,
        uiState.selectedFps,
        uiState.gammaMode,
        uiState.isSdrToneMapEnabled
    ) {
        viewModel.startCamera(lifecycleOwner)
    }

    // --- System UI Management ---
    SystemUiManagement()
    ScreenTimeoutManagement(isRecording = uiState.isRecording)
    ScreenOrientationManagement(isRecording = uiState.isRecording)

    // --- Hack 2: Force Display SDR ---
    // Controls the screen brightness logic via setDesiredHdrHeadroom.
    // This prevents the screen from entering High Brightness Mode, saving power.
    HdrBrightnessManagement(shouldLimitBrightness = uiState.isForceDisplaySdrEnabled)

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
                    surfaceRequest = surfaceRequest,
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

                // SDR Hacks State
                isSdrToneMapEnabled = uiState.isSdrToneMapEnabled,
                onSdrToneMapChange = { viewModel.onEvent(CameraUiEvent.SetSdrToneMap(it)) },
                isForceDisplaySdrEnabled = uiState.isForceDisplaySdrEnabled,
                onForceDisplaySdrChange = { viewModel.onEvent(CameraUiEvent.SetForceDisplaySdr(it)) },

                onNavigateToCompatibility = onNavigateToCompatibility,
                onClose = { viewModel.onEvent(CameraUiEvent.CloseSettings) }
            )
        }
    }
}

// --- Helper Composables for System Management ---

@Composable
private fun HdrBrightnessManagement(shouldLimitBrightness: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val context = LocalContext.current
        val window = (context as? Activity)?.window

        LaunchedEffect(shouldLimitBrightness) {
            // Guard: Only run on Android 15 (API 35+) as requested.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                // 1.0f = SDR brightness range (saves power).
                // 0.0f = System default (full HDR boost).
                window?.setDesiredHdrHeadroom(if (shouldLimitBrightness) 1.0f else 0.0f)
            }
        }
    }
}

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