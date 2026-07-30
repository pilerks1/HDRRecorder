package com.pilerks1.hdrrecorder.ui

import android.app.Application
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ModeNight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pilerks1.hdrrecorder.data.camera.CameraRuntime
import com.pilerks1.hdrrecorder.ui.helpers.ActivityOrientationManagement
import com.pilerks1.hdrrecorder.ui.helpers.DisplayRotationListener
import com.pilerks1.hdrrecorder.ui.helpers.HdrBrightnessManagement
import com.pilerks1.hdrrecorder.ui.helpers.ScreenTimeoutManagement
import com.pilerks1.hdrrecorder.ui.helpers.SystemUiManagement
import com.pilerks1.hdrrecorder.ui.layout.CameraSlotsLayout
import com.pilerks1.hdrrecorder.ui.layout.EdgeInsets
import com.pilerks1.hdrrecorder.ui.layout.LayoutRect
import com.pilerks1.hdrrecorder.ui.layout.PanelThicknessSpec
import com.pilerks1.hdrrecorder.ui.manualcontrols.availableControlPanels
import com.pilerks1.hdrrecorder.ui.manualcontrols.rememberSliderPaneSizing
import com.pilerks1.hdrrecorder.ui.settingsUI.SettingsUI
import com.pilerks1.hdrrecorder.ui.viewmodels.CameraViewModel
import com.pilerks1.hdrrecorder.ui.viewmodels.CameraViewModelFactory

/** Main camera surface. UI geometry and CameraX output rotation intentionally stay separate. */
@OptIn(ExperimentalCamera2Interop::class)
@Composable
internal fun CameraUI(
    cameraRuntime: CameraRuntime,
    onNavigateToCompatibility: () -> Unit,
    viewModel: CameraViewModel = viewModel(
        factory = CameraViewModelFactory(
            application = LocalContext.current.applicationContext as Application,
            cameraRuntime = cameraRuntime
        )
    )
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        CameraActions(
            onEvent = viewModel::onEvent,
            onManualControlsChange = viewModel::updateManualControls,
            onManualControlInteractionFinished = viewModel::finishManualControlInteraction,
            stats = viewModel.stats,
            recordingDurationNanos = viewModel.recordingDurationNanos,
            sliderTelemetry = viewModel.sliderTelemetry
        )
    }

    LaunchedEffect(lifecycleOwner) {
        viewModel.attachCamera(lifecycleOwner, view.display?.rotation ?: 0)
    }

    SystemUiManagement()
    ScreenTimeoutManagement(isRecording = uiState.isRecording)
    HdrBrightnessManagement(shouldLimitBrightness = uiState.isForceDisplaySdrEnabled)
    ActivityOrientationManagement(isRecording = uiState.isRecording)
    DisplayRotationListener(viewModel::onDisplayRotationChanged)

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val displayCutout = WindowInsets.displayCutout
    val cutoutInsets = EdgeInsets(
        left = displayCutout.getLeft(density, layoutDirection).toFloat(),
        top = displayCutout.getTop(density).toFloat(),
        right = displayCutout.getRight(density, layoutDirection).toFloat(),
        bottom = displayCutout.getBottom(density).toFloat()
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val controlPanels = availableControlPanels(uiState.cameraCapabilities)
        val panelSizing = rememberSliderPaneSizing(controlPanels, uiState.cameraCapabilities)
        CameraSlotsLayout(
            aspectRatio = uiState.selectedAspectRatio,
            secondaryButtonCount = controlPanels.size,
            cutoutInsets = cutoutInsets,
            panelThickness = { axis ->
                PanelThicknessSpec(
                    activeThicknessPx = panelSizing.thicknessFor(
                        axis,
                        uiState.manualControlsState.activePanel
                    ),
                    maximumThicknessPx = panelSizing.maximumThicknessFor(axis)
                )
            },
            stats = { axis ->
                StatsUI(
                    stats = actions.stats,
                    isRecording = uiState.isRecording,
                    axis = axis
                )
            },
            preview = { layoutSpec ->
                val expandedBounds = if (uiState.manualControlsState.activePanel != null) {
                    layoutSpec.expandedPanel.relativeTo(layoutSpec.preview)
                } else null
                PreviewUI(
                    surfaceRequest = surfaceRequest,
                    placeholderLabel = uiState.previewPlaceholderLabel,
                    recordingDurationNanos = actions.recordingDurationNanos,
                    isRecording = uiState.isRecording,
                    expandedPanelBounds = expandedBounds,
                    onEvent = actions.onEvent,
                    modifier = Modifier.fillMaxSize()
                )
            },
            secondaryControls = { layoutSpec ->
                SecondaryControlsUI(
                    uiState = uiState,
                    actions = actions,
                    axis = layoutSpec.axis,
                    railSpec = layoutSpec.actionRail,
                    modifier = Modifier.fillMaxSize()
                )
            },
            expandedPanel = { layoutSpec ->
                ExpandedControlPanelUI(
                    uiState = uiState,
                    actions = actions,
                    axis = layoutSpec.axis,
                    contentInsets = cutoutInsets,
                    modifier = Modifier.fillMaxSize()
                )
            },
            primaryActions = { layoutSpec ->
                PrimaryActionsUI(
                    uiState = uiState,
                    actions = actions,
                    axis = layoutSpec.axis,
                    modifier = Modifier.fillMaxSize()
                )
            },
            manualReset = {
                if (uiState.manualControlsState.hasManualSliderOverride) {
                    ManualResetButton(
                        enabled = !uiState.isExposureTransitioning,
                        onClick = {
                            actions.onManualControlsChange(
                                ManualControlsState::resetManualSlidersToAuto
                            )
                            actions.onManualControlInteractionFinished()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            },
            lowLightBoostIndicator = {
                if (uiState.isLowLightBoostActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = Color.Black.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(2.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ModeNight,
                            contentDescription = "Low-light boost active",
                            tint = Color.Yellow,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

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
                onNavigateToCompatibility = {
                    if (uiState.isCameraHardwareEnabled) onNavigateToCompatibility()
                },
                onClose = { actions.onEvent(CameraUiEvent.CloseSettings) }
            )
        }

        uiState.exposureTransitionMessage?.let { message ->
            Text(
                text = message,
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.75f))
            )
        }
    }
}

private fun LayoutRect.relativeTo(parent: LayoutRect): LayoutRect = LayoutRect(
    left = left - parent.left,
    top = top - parent.top,
    right = right - parent.left,
    bottom = bottom - parent.top
)
