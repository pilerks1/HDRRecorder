package com.pilerks1.hdrrecorder.ui

import android.view.Surface
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import android.app.Application
import com.pilerks1.hdrrecorder.ui.settingsUI.SettingsUI
import com.pilerks1.hdrrecorder.ui.helpers.*
import com.pilerks1.hdrrecorder.ui.viewmodels.CameraViewModel
import com.pilerks1.hdrrecorder.ui.viewmodels.CameraViewModelFactory
import kotlinx.coroutines.delay

/**
 * The main entry point for the Camera UI.
 *
 * Rotation architecture:
 * - DeviceOrientationListener (accelerometer) is the SINGLE source of rotation truth.
 * - It feeds raw degrees to the ViewModel, which quantizes and stores deviceRotation.
 * - isLandscape is derived from deviceRotation (NOT LocalConfiguration).
 * - CameraX targetRotation is set from the same deviceRotation value.
 * - DeviceOrientationManagement forces the Activity to match the detected tilt,
 *   overriding the OS rotation lock (rotate-while-locked behavior).
 * - During recording, orientation is frozen (ViewModel guard + Activity lock).
 */
@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun CameraUI(
    onNavigateToCompatibility: () -> Unit,
    viewModel: CameraViewModel = viewModel(
        factory = CameraViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val surfaceRequest by viewModel.surfaceRequest.collectAsState()

    // Stable callback surface so control composables never touch the concrete ViewModel.
    val actions = remember(viewModel) {
        CameraActions(
            onEvent = viewModel::onEvent,
            onManualControlsChange = viewModel::updateManualControls
        )
    }

    // --- Effects ---
    LaunchedEffect(uiState.cameraRebindTrigger) {
        delay(500)
        viewModel.startCamera(lifecycleOwner)
    }

    // --- System UI ---
    SystemUiManagement()
    ScreenTimeoutManagement(isRecording = uiState.isRecording)
    HdrBrightnessManagement(shouldLimitBrightness = uiState.isForceDisplaySdrEnabled)

    // --- Orientation: single source of truth ---
    // Accelerometer-based listener fires regardless of OS rotation lock.
    DeviceOrientationListener { degrees ->
        viewModel.onDeviceOrientationChanged(degrees)
    }
    // Forces the Activity window to match the detected tilt (overrides OS lock).
    // During recording, locks to the current orientation.
    DeviceOrientationManagement(
        deviceRotation = uiState.deviceRotation,
        isRecording = uiState.isRecording
    )

    // --- Layout Logic ---
    // isLandscape is derived INSIDE BoxWithConstraints so it can gate on both the
    // ViewModel's deviceRotation AND the actual window dimensions agreeing.
    // This prevents the 1-2 frame glitch where state says "landscape" but the
    // Activity window hasn't physically rotated yet.

    // --- Main UI Layout ---
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val screenWidthDp = maxWidth
        val screenHeightDp = maxHeight
        val density = LocalDensity.current

        // Gate: device says landscape AND the window is actually wider than tall.
        val deviceIsLandscape = uiState.deviceRotation == Surface.ROTATION_90
                || uiState.deviceRotation == Surface.ROTATION_270
        val isLandscape = deviceIsLandscape && maxWidth > maxHeight

        var previewRect by remember { mutableStateOf(Rect.Zero) }

        // 1. PREVIEW LAYER (Bottom)
        PreviewUI(
            surfaceRequest = surfaceRequest,
            stats = uiState.stats,
            isRecording = uiState.isRecording,
            isLandscape = isLandscape,
            onEvent = actions.onEvent,
            modifier = Modifier
                .align(Alignment.Center)
                .aspectRatio(if (isLandscape) 4f / 3f else 3f / 4f)
                .then(if (isLandscape) Modifier.fillMaxHeight() else Modifier.fillMaxWidth())
                .onGloballyPositioned { coordinates ->
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

                // CENTER PREVIEW & SLIDERS
                Box(
                    modifier = Modifier
                        .offset(x = previewLeft, y = previewTop)
                        .size(width = previewWidth, height = previewHeight)
                ) {
                    ControlsUISliders(uiState = uiState, actions = actions, isLandscape = isLandscape, modifier = Modifier.fillMaxSize())
                }

                // RIGHT BLACK BAR (Buttons)
                Box(
                    modifier = Modifier
                        .offset(x = previewRight, y = 0.dp)
                        .size(width = screenWidthDp - previewRight, height = screenHeightDp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    ControlsUIButtons(uiState = uiState, actions = actions, isLandscape = isLandscape, modifier = Modifier.fillMaxSize())
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

                // CENTER PREVIEW & SLIDERS
                Box(
                    modifier = Modifier
                        .offset(x = previewLeft, y = previewTop)
                        .size(width = previewWidth, height = previewHeight)
                ) {
                    ControlsUISliders(uiState = uiState, actions = actions, isLandscape = isLandscape, modifier = Modifier.fillMaxSize())
                }

                // BOTTOM BLACK BAR (Buttons)
                Box(
                    modifier = Modifier
                        .offset(x = 0.dp, y = previewBottom)
                        .size(width = screenWidthDp, height = screenHeightDp - previewBottom),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    ControlsUIButtons(uiState = uiState, actions = actions, isLandscape = isLandscape, modifier = Modifier.fillMaxSize())
                }
            }
        }

        // 2. NIGHT MODE AE ICON
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

        // 3. SETTINGS OVERLAY
        if (uiState.isSettingsSheetVisible) {
            SettingsUI(
                currentPreset = uiState.currentPresetName,
                presetsList = uiState.presetsList,
                onSavePreset = { actions.onEvent(CameraUiEvent.SavePreset(it)) },
                onLoadPreset = { actions.onEvent(CameraUiEvent.LoadPreset(it)) },
                onDeletePreset = { actions.onEvent(CameraUiEvent.DeletePreset(it)) },
                onDeleteAllPresets = { actions.onEvent(CameraUiEvent.DeleteAllPresets) },

                colorFormat = uiState.colorFormat,
                onColorFormatChange = { actions.onEvent(CameraUiEvent.CycleColorFormat) },
                gammaCurve = uiState.gammaCurve,
                onGammaCurveChange = { actions.onEvent(CameraUiEvent.CycleGammaCurve) },

                noiseReductionEnabled = uiState.isNoiseReductionEnabled,
                onNoiseReductionChange = { actions.onEvent(CameraUiEvent.SetNoiseReduction(it)) },
                bitrate = uiState.bitrate,
                onBitrateChange = { actions.onEvent(CameraUiEvent.SetBitrate(it)) },
                isStabilizationEnabled = uiState.isStabilizationEnabled,
                onStabilizationChange = { actions.onEvent(CameraUiEvent.SetStabilization(it)) },

                isSdrToneMapEnabled = uiState.isSdrToneMapEnabled,
                onSdrToneMapChange = { actions.onEvent(CameraUiEvent.SetSdrToneMap(it)) },
                isForceDisplaySdrEnabled = uiState.isForceDisplaySdrEnabled,
                onForceDisplaySdrChange = { actions.onEvent(CameraUiEvent.SetForceDisplaySdr(it)) },

                storageUri = uiState.storageUri,
                onStorageUriSelected = { actions.onEvent(CameraUiEvent.SetStorageUri(it)) },

                onNavigateToCompatibility = onNavigateToCompatibility,
                onClose = { actions.onEvent(CameraUiEvent.CloseSettings) }
            )
        }
    } // End of root Box
}
