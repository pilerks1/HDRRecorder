package com.pilerks1.hdrrecorder.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * The main entry point for the Camera UI.
 * Uses a persistent Box layout to ensure the Camera Preview Surface is never destroyed or moved.
 * UI Controls float on top and adapt their position based on system orientation.
 */
@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun CameraUI(
    viewModel: CameraViewModel = viewModel(),
    onNavigateToCompatibility: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val surfaceRequest by viewModel.surfaceRequest.collectAsState()

    // Track system display rotation
    var displayRotation by remember { mutableIntStateOf(Surface.ROTATION_0) }

    // --- Effects ---
    LaunchedEffect(
        uiState.selectedResolution,
        uiState.selectedFps,
        uiState.gammaMode,
        uiState.isSdrToneMapEnabled
    ) {
        viewModel.startCamera(lifecycleOwner)
    }

    // --- System UI ---
    SystemUiManagement()
    ScreenTimeoutManagement(isRecording = uiState.isRecording)
    HdrBrightnessManagement(shouldLimitBrightness = uiState.isForceDisplaySdrEnabled)

    // --- Display Rotation Listener ---
    DisplayRotationListener { rotation ->
        displayRotation = rotation
        viewModel.onOrientationChanged(rotation)
    }

    // --- Layout Logic ---
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // --- Main UI Layout ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. PREVIEW LAYER (Bottom)
        // Always fills screen. Never moves in the tree.
        PreviewUI(
            surfaceRequest = surfaceRequest,
            recordingTime = uiState.recordingTime,
            isRecording = uiState.isRecording,
            onEvent = viewModel::onEvent,
            modifier = Modifier.fillMaxSize()
        )

        // 2. STATS OVERLAY
        // Landscape: Left side (25% width)
        // Portrait: Top side (20% height), aligned to Start (Left)
        Box(
            modifier = Modifier
                .align(if (isLandscape) Alignment.CenterStart else Alignment.TopStart)
                .fillMaxWidth(if (isLandscape) 0.25f else 1f)
                .fillMaxHeight(if (isLandscape) 1f else 0.2f)
        ) {
            StatsUI(
                shutterSpeed = uiState.shutterSpeed,
                iso = uiState.iso,
                isRecording = uiState.isRecording,
                effectiveFps = uiState.effectiveFps,
                droppedFrames = uiState.droppedFrames,
                addedFrames = uiState.addedFrames,
                // Pass modifier to StatsUI to let it handle internal padding
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. CONTROLS OVERLAY
        // Landscape: Right side (25% width)
        // Portrait: Bottom side (25% height)
        Box(
            modifier = Modifier
                .align(if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter)
                .fillMaxWidth(if (isLandscape) 0.25f else 1f)
                .fillMaxHeight(if (isLandscape) 1f else 0.25f)
        ) {
            ControlsUI(
                uiState = uiState,
                onEvent = viewModel::onEvent,
                isLandscape = isLandscape,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 4. SETTINGS OVERLAY
        if (uiState.isSettingsSheetVisible) {
            SettingsUI(
                gammaMode = uiState.gammaMode,
                onGammaChange = { viewModel.onEvent(CameraUiEvent.CycleGammaMode) },
                noiseReductionEnabled = uiState.isNoiseReductionEnabled,
                onNoiseReductionChange = { viewModel.onEvent(CameraUiEvent.SetNoiseReduction(it)) },
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

// --- Helper Composables ---

@Composable
private fun DisplayRotationListener(onRotationChanged: (Int) -> Unit) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                val display = displayManager.getDisplay(displayId) ?: return
                if (display.displayId == Display.DEFAULT_DISPLAY) {
                    @Suppress("DEPRECATION")
                    onRotationChanged(display.rotation)
                }
            }
        }

        displayManager.registerDisplayListener(listener, null)

        val initialDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        if (initialDisplay != null) {
            @Suppress("DEPRECATION")
            onRotationChanged(initialDisplay.rotation)
        }

        onDispose {
            displayManager.unregisterDisplayListener(listener)
        }
    }
}

@Composable
private fun HdrBrightnessManagement(shouldLimitBrightness: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val context = LocalContext.current
        val window = (context as? Activity)?.window
        LaunchedEffect(shouldLimitBrightness) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
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