package com.pilerks1.hdrrecorder.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ModeNight
import androidx.compose.material3.Icon
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
import com.pilerks1.hdrrecorder.ui.manualcontrols.ActiveControlPanel
import com.pilerks1.hdrrecorder.ui.manualcontrols.SecondaryControlsRail
import com.pilerks1.hdrrecorder.ui.manualcontrols.availableControlPanels
import com.pilerks1.hdrrecorder.ui.manualcontrols.rememberSliderPaneSizing
import com.pilerks1.hdrrecorder.ui.settingsUI.SettingsUI
import com.pilerks1.hdrrecorder.ui.viewmodels.CameraViewModel
import com.pilerks1.hdrrecorder.ui.viewmodels.CameraViewModelFactory

/** Main camera surface. UI geometry and CameraX output rotation intentionally stay separate. */
@Composable
internal fun CameraUI(
    cameraRuntime: CameraRuntime,
    onNavigateToCompatibility: () -> Unit,
    onShowDiagnostics: () -> Unit,
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
    // When the entire layout is inset, its children must not apply the cutout insets again.
    val contentCutoutInsets = if (uiState.isRespectDisplayCutoutsEnabled) EdgeInsets() else cutoutInsets

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val controlPanels = availableControlPanels(uiState.cameraCapabilities)
        val panelSizing = rememberSliderPaneSizing(controlPanels, uiState.cameraCapabilities, uiState.zoomState)
        val statsRail = rememberStatsRail(actions.stats, uiState.isRecording)
        val activePanel = uiState.activePanel
        CameraControlTouchTargets {
            CameraSlotsLayout(
                aspectRatio = uiState.selectedAspectRatio,
                secondaryButtonCount = controlPanels.size,
                cutoutInsets = contentCutoutInsets,
                panelThickness = { axis ->
                    PanelThicknessSpec(
                        activeThicknessPx = panelSizing.thicknessFor(axis, activePanel),
                        maximumThicknessPx = panelSizing.maximumThicknessFor(axis)
                    )
                },
                statsSize = statsRail::size,
                stats = { axis -> StatsUI(rail = statsRail, axis = axis) },
                preview = { layoutSpec ->
                    val expandedBounds = if (activePanel != null) {
                        layoutSpec.expandedPanel.relativeTo(layoutSpec.preview)
                    } else null
                    PreviewUI(
                        surfaceRequest = surfaceRequest,
                        placeholderLabel = uiState.previewPlaceholderLabel,
                        recordingDurationNanos = actions.recordingDurationNanos,
                        isRecording = uiState.isRecording,
                        expandedPanelBounds = expandedBounds,
                        zoomState = uiState.zoomState,
                        meteringState = uiState.meteringState,
                        onEvent = actions.onEvent,
                        modifier = Modifier.fillMaxSize()
                    )
                },
                secondaryControls = { layoutSpec ->
                    SecondaryControlsRail(
                        panels = controlPanels,
                        activePanel = activePanel,
                        axis = layoutSpec.axis,
                        railSpec = layoutSpec.actionRail,
                        onTogglePanel = { actions.onEvent(CameraUiEvent.TogglePanel(it)) },
                        modifier = Modifier.fillMaxSize()
                    )
                },
                expandedPanel = { layoutSpec ->
                    if (activePanel != null) {
                        ExpandedControlPanel(
                            activePanel = activePanel,
                            uiState = uiState,
                            actions = actions,
                            layoutSpec = layoutSpec,
                            contentInsets = contentCutoutInsets
                        )
                    }
                },
                primaryActions = { layoutSpec ->
                    PrimaryActionsUI(
                        isRecording = uiState.isRecording,
                        isPaused = uiState.isPaused,
                        recordEnabled = uiState.isCameraReady && uiState.arePresetsInitialized && !uiState.isPresetBusy,
                        onEvent = actions.onEvent,
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
                modifier = Modifier.fillMaxSize().then(
                    if (uiState.isRespectDisplayCutoutsEnabled) Modifier.windowInsetsPadding(displayCutout) else Modifier
                )
            )
        }

        if (uiState.isPresetBusy && !uiState.isSettingsSheetVisible) {
            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        if (uiState.isSettingsSheetVisible) {
            SettingsUI(
                state = uiState,
                onEvent = actions.onEvent,
                onNavigateToCompatibility = onNavigateToCompatibility,
                onShowDiagnostics = onShowDiagnostics
            )
        }

    }
}

@Composable
private fun ExpandedControlPanel(
    activePanel: CameraControlPanel,
    uiState: CameraUiState,
    actions: CameraActions,
    layoutSpec: com.pilerks1.hdrrecorder.ui.layout.CameraLayoutSpec,
    contentInsets: EdgeInsets
) {
    ActiveControlPanel(
        activePanel = activePanel,
        state = uiState.manualControlsState,
        zoomState = uiState.zoomState,
        onZoomRatioChange = { actions.onEvent(CameraUiEvent.SetZoomRatio(it)) },
        onSelectZoomLens = { actions.onEvent(CameraUiEvent.SelectZoomLens(it)) },
        caps = uiState.cameraCapabilities,
        cameraTelemetry = actions.sliderTelemetry,
        isRecording = uiState.isRecording,
        selectedResolution = uiState.selectedResolution,
        selectedAspectRatio = uiState.selectedAspectRatio,
        axis = layoutSpec.axis,
        contentInsets = contentInsets,
        onCycleResolution = { actions.onEvent(CameraUiEvent.CycleResolution) },
        onCycleAspectRatio = { actions.onEvent(CameraUiEvent.CycleAspectRatio) },
        onSetIso = { isManual, value -> actions.onManualControlsChange { it.copy(isManualIso = isManual, isoValue = value) } },
        onSetPostRawSensitivity = { isManual, value ->
            actions.onManualControlsChange {
                it.copy(
                    isManualPostRawSensitivity = isManual,
                    postRawSensitivityBoost = value
                )
            }
        },
        onSetSs = { isManual, value -> actions.onManualControlsChange { it.copy(isManualSs = isManual, ssValueNanos = value) } },
        onSetEv = { isManual, value -> actions.onManualControlsChange { it.copy(isManualEv = isManual, evValueIndex = value ?: 0) } },
        onSetLowLightBoost = { enabled -> actions.onEvent(CameraUiEvent.SetLowLightBoost(enabled)) },
        isLowLightBoostEligible = uiState.isCameraReady && uiState.isLowLightBoostEligible,
        onSetFocus = { isManual, value -> actions.onManualControlsChange { it.copy(isManualFocus = isManual, focusDistanceDiopters = value) } },
        onSetWb = { isManual, temp, tint -> actions.onManualControlsChange { it.copy(isManualWb = isManual, wbTemp = temp, wbTint = tint) } },
        onSetFps = { isManual, range ->
            actions.onManualControlsChange {
                it.copy(isManualFps = isManual, fpsRange = range)
            }
            actions.onManualControlInteractionFinished()
        },
        autoFps = uiState.selectedFps,
        onCycleFps = {
            if (uiState.manualControlsState.isManualFps) {
                actions.onManualControlsChange { it.copy(isManualFps = false) }
            } else {
                actions.onEvent(CameraUiEvent.CycleFps)
            }
        },
        controlsEnabled = !uiState.isExposureTransitioning,
        onManualControlInteractionFinished = actions.onManualControlInteractionFinished,
        modifier = Modifier.fillMaxSize()
    )
}

private fun LayoutRect.relativeTo(parent: LayoutRect): LayoutRect = LayoutRect(
    left = left - parent.left,
    top = top - parent.top,
    right = right - parent.left,
    bottom = bottom - parent.top
)
