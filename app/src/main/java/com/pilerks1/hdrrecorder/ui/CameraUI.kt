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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ModeNight
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pilerks1.hdrrecorder.ui.settingsUI.SettingsUI
import com.pilerks1.hdrrecorder.ui.helpers.*
import com.pilerks1.hdrrecorder.ui.viewmodels.CameraViewModel

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
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val surfaceRequest by viewModel.surfaceRequest.collectAsState()

    // Track system display rotation
    var displayRotation by remember { mutableIntStateOf(Surface.ROTATION_0) }

    // --- Effects ---
    LaunchedEffect(uiState.cameraRebindTrigger) {
        // Wait 500ms before applying. If another trigger happens during this time,
        // the effect cancels and restarts the 500ms timer (debouncing).
        kotlinx.coroutines.delay(500)

        // Rebind the camera ONLY when the explicit trigger increments
        // (e.g., on initial launch, or upon closing Settings if changes were made)
        viewModel.startCamera(lifecycleOwner)
    }

    // --- System UI ---
    SystemUiManagement()
    ScreenTimeoutManagement(isRecording = uiState.isRecording)
    ScreenOrientationManagement(isRecording = uiState.isRecording)
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
    // The root Box anchors the permanent PreviewUI in the exact center so it survives orientation changes.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val screenWidthDp = maxWidth
        val screenHeightDp = maxHeight
        val density = LocalDensity.current

        var previewRect by remember { mutableStateOf(Rect.Zero) }

        // 1. PREVIEW LAYER (Bottom)
        // Always fills screen but constrained to its natural aspect ratio.
        PreviewUI(
            surfaceRequest = surfaceRequest,
            stats = uiState.stats,
            isRecording = uiState.isRecording,
            onEvent = viewModel::onEvent,
            modifier = Modifier
                .align(Alignment.Center)
                .aspectRatio(if (isLandscape) 4f / 3f else 3f / 4f)
                .then(if (isLandscape) Modifier.fillMaxHeight() else Modifier.fillMaxWidth())
                .onGloballyPositioned { coordinates ->
                    // Read the exact pixel coordinates of the preview box after all notch padding and constraints are evaluated
                    previewRect = coordinates.boundsInRoot()
                }
        )

        // Only draw the overlays if we have successfully measured the preview box
        if (previewRect.width > 0f) {
            val previewTop = with(density) { previewRect.top.toDp() }
            val previewBottom = with(density) { previewRect.bottom.toDp() }
            val previewLeft = with(density) { previewRect.left.toDp() }
            val previewRight = with(density) { previewRect.right.toDp() }
            val previewWidth = with(density) { previewRect.width.toDp() }
            val previewHeight = with(density) { previewRect.height.toDp() }

            if (isLandscape) {
                // LEFT BLACK BAR (Stats)
                Box(
                    modifier = Modifier
                        .offset(x = 0.dp, y = 0.dp)
                        .size(width = previewLeft, height = screenHeightDp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    StatsUI(stats = uiState.stats, isRecording = uiState.isRecording, modifier = Modifier.fillMaxSize())
                }

                // CENTER PREVIEW & SLIDERS (Exactly matching the preview rect)
                Box(
                    modifier = Modifier
                        .offset(x = previewLeft, y = previewTop)
                        .size(width = previewWidth, height = previewHeight)
                ) {
                    ControlsUISliders(uiState = uiState, viewModel = viewModel, isLandscape = isLandscape, modifier = Modifier.fillMaxSize())
                }

                // RIGHT BLACK BAR (Buttons)
                Box(
                    modifier = Modifier
                        .offset(x = previewRight, y = 0.dp)
                        .size(width = screenWidthDp - previewRight, height = screenHeightDp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    ControlsUIButtons(uiState = uiState, viewModel = viewModel, isLandscape = isLandscape, modifier = Modifier.fillMaxSize())
                }
            } else {
                // TOP BLACK BAR (Stats)
                Box(
                    modifier = Modifier
                        .offset(x = 0.dp, y = 0.dp)
                        .size(width = screenWidthDp, height = previewTop),
                    contentAlignment = Alignment.TopStart
                ) {
                    StatsUI(stats = uiState.stats, isRecording = uiState.isRecording, modifier = Modifier.fillMaxSize())
                }

                // CENTER PREVIEW & SLIDERS (Exactly matching the preview rect)
                Box(
                    modifier = Modifier
                        .offset(x = previewLeft, y = previewTop)
                        .size(width = previewWidth, height = previewHeight)
                ) {
                    ControlsUISliders(uiState = uiState, viewModel = viewModel, isLandscape = isLandscape, modifier = Modifier.fillMaxSize())
                }

                // BOTTOM BLACK BAR (Buttons)
                Box(
                    modifier = Modifier
                        .offset(x = 0.dp, y = previewBottom)
                        .size(width = screenWidthDp, height = screenHeightDp - previewBottom),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    ControlsUIButtons(uiState = uiState, viewModel = viewModel, isLandscape = isLandscape, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }    
        // 4. NIGHT MODE AE ICON
        if (uiState.manualControlsState.isNightModeAeEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 24.dp, end = if (isLandscape) 140.dp else 24.dp)
                    .windowInsetsPadding(WindowInsets.displayCutout),
                contentAlignment = Alignment.TopEnd
            ) {
                Icon(
                    imageVector = Icons.Default.ModeNight,
                    contentDescription = "Night Mode AE Active",
                    tint = Color.Yellow,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // 5. SETTINGS OVERLAY
        if (uiState.isSettingsSheetVisible) {
            SettingsUI(
                currentPreset = uiState.currentPresetName,
                presetsList = uiState.presetsList,
                onSavePreset = { viewModel.onEvent(CameraUiEvent.SavePreset(it)) },
                onLoadPreset = { viewModel.onEvent(CameraUiEvent.LoadPreset(it)) },
                onDeletePreset = { viewModel.onEvent(CameraUiEvent.DeletePreset(it)) },
                onDeleteAllPresets = { viewModel.onEvent(CameraUiEvent.DeleteAllPresets) },

                colorFormat = uiState.colorFormat,
                onColorFormatChange = { viewModel.onEvent(CameraUiEvent.CycleColorFormat) },
                gammaCurve = uiState.gammaCurve,
                onGammaCurveChange = { viewModel.onEvent(CameraUiEvent.CycleGammaCurve) },

                noiseReductionEnabled = uiState.isNoiseReductionEnabled,
                onNoiseReductionChange = { viewModel.onEvent(CameraUiEvent.SetNoiseReduction(it)) },
                bitrate = uiState.bitrate,
                onBitrateChange = { viewModel.onEvent(CameraUiEvent.SetBitrate(it)) },
                isStabilizationEnabled = uiState.isStabilizationEnabled,
                onStabilizationChange = { viewModel.onEvent(CameraUiEvent.SetStabilization(it)) },

                isSdrToneMapEnabled = uiState.isSdrToneMapEnabled,
                onSdrToneMapChange = { viewModel.onEvent(CameraUiEvent.SetSdrToneMap(it)) },
                isForceDisplaySdrEnabled = uiState.isForceDisplaySdrEnabled,
                onForceDisplaySdrChange = { viewModel.onEvent(CameraUiEvent.SetForceDisplaySdr(it)) },

                storageUri = uiState.storageUri,
                onStorageUriSelected = { viewModel.onEvent(CameraUiEvent.SetStorageUri(it)) },

                onNavigateToCompatibility = onNavigateToCompatibility,
                onClose = { viewModel.onEvent(CameraUiEvent.CloseSettings) }
            )
        }
    } // End of root Box
