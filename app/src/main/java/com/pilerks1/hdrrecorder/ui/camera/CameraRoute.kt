package com.pilerks1.hdrrecorder.ui.camera

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pilerks1.hdrrecorder.ui.camera.state.CameraActions
import com.pilerks1.hdrrecorder.ui.camera.state.CameraViewModel
import com.pilerks1.hdrrecorder.ui.camera.state.CameraViewModelFactory

/** Main camera surface. UI geometry and CameraX output rotation intentionally stay separate. */
@Composable
internal fun CameraRoute(
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

    CameraScreen(
        uiState = uiState,
        surfaceRequest = surfaceRequest,
        actions = actions,
        onNavigateToCompatibility = onNavigateToCompatibility,
        onShowDiagnostics = onShowDiagnostics
    )
}
