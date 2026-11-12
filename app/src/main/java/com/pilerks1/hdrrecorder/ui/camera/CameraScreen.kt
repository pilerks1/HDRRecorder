package com.pilerks1.hdrrecorder.ui.camera

import androidx.camera.core.SurfaceRequest
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import com.pilerks1.hdrrecorder.ui.camera.controls.CameraControlPanelContent
import com.pilerks1.hdrrecorder.ui.camera.controls.CameraControlTouchTargets
import com.pilerks1.hdrrecorder.ui.camera.controls.ManualResetButton
import com.pilerks1.hdrrecorder.ui.camera.controls.PrimaryCameraActions
import com.pilerks1.hdrrecorder.ui.camera.controls.SecondaryControlsRail
import com.pilerks1.hdrrecorder.ui.camera.controls.availableControlPanels
import com.pilerks1.hdrrecorder.ui.camera.controls.slider.rememberSliderPaneSizing
import com.pilerks1.hdrrecorder.ui.camera.layout.CameraSlotsLayout
import com.pilerks1.hdrrecorder.ui.camera.layout.EdgeInsets
import com.pilerks1.hdrrecorder.ui.camera.layout.LayoutRect
import com.pilerks1.hdrrecorder.ui.camera.layout.PanelThicknessSpec
import com.pilerks1.hdrrecorder.ui.camera.preview.CameraPreview
import com.pilerks1.hdrrecorder.ui.camera.settings.CameraSettingsScreen
import com.pilerks1.hdrrecorder.ui.camera.state.CameraActions
import com.pilerks1.hdrrecorder.ui.camera.state.CameraUiEvent
import com.pilerks1.hdrrecorder.ui.camera.state.CameraUiState
import com.pilerks1.hdrrecorder.ui.camera.state.ManualControlsState
import com.pilerks1.hdrrecorder.ui.camera.state.hasManualSliderOverride
import com.pilerks1.hdrrecorder.ui.camera.state.resetManualSlidersToAuto
import com.pilerks1.hdrrecorder.ui.camera.stats.CameraStatsRail
import com.pilerks1.hdrrecorder.ui.camera.stats.rememberStatsRail
import com.pilerks1.hdrrecorder.ui.style.CameraDimensions

/** Renders the camera state using the existing responsive layout. */
@Composable
internal fun CameraScreen(
    uiState: CameraUiState,
    surfaceRequest: androidx.camera.core.SurfaceRequest?,
    actions: CameraActions,
    onNavigateToCompatibility: () -> Unit,
    onShowDiagnostics: () -> Unit
) {
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
                stats = { axis -> CameraStatsRail(rail = statsRail, axis = axis) },
                preview = { layoutSpec ->
                    val expandedBounds = if (activePanel != null) {
                        layoutSpec.expandedPanel.relativeTo(layoutSpec.preview)
                    } else null
                    CameraPreview(
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
                        CameraControlPanelContent(
                            activePanel = activePanel,
                            uiState = uiState,
                            actions = actions,
                            layoutSpec = layoutSpec,
                            contentInsets = contentCutoutInsets
                        )
                    }
                },
                primaryActions = { layoutSpec ->
                    PrimaryCameraActions(
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
                                    shape = RoundedCornerShape(CameraDimensions.indicatorCornerRadius)
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
            CameraSettingsScreen(
                state = uiState,
                onEvent = actions.onEvent,
                onNavigateToCompatibility = onNavigateToCompatibility,
                onShowDiagnostics = onShowDiagnostics
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
