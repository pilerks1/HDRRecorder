package com.pilerks1.hdrrecorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.pilerks1.hdrrecorder.R
import com.pilerks1.hdrrecorder.data.diagnostics.ErrorReporter
import com.pilerks1.hdrrecorder.ui.camera.CameraRoute
import com.pilerks1.hdrrecorder.ui.camera.CameraRuntime
import com.pilerks1.hdrrecorder.ui.compatibility.CompatibilityScreen
import com.pilerks1.hdrrecorder.ui.compatibility.CompatibilityUiState
import com.pilerks1.hdrrecorder.ui.compatibility.CompatibilityViewModel
import com.pilerks1.hdrrecorder.ui.diagnostics.ErrorReportingHost

/** Navigation destinations; an enum is saveable across configuration changes without Parcelize. */
private enum class AppScreen { Camera, Compatibility }

/** Root navigation and diagnostics presentation; permissions remain activity-owned. */
@Composable
internal fun AppContent(
    cameraRuntime: CameraRuntime,
    hasPermissions: Boolean,
    compatibilityViewModel: CompatibilityViewModel,
    compatibilityState: CompatibilityUiState,
    reporter: ErrorReporter
) {
    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Camera) }
    // Diagnostics history visibility is screen state, shared by every screen that opens it.
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    val openDiagnostics = { showDiagnostics = true }
    LaunchedEffect(currentScreen, cameraRuntime) {
        if (currentScreen == AppScreen.Compatibility && cameraRuntime.usesCameraHardware) {
            compatibilityViewModel.initialize()
        }
    }
    Box(Modifier.fillMaxSize()) {
        if (hasPermissions) {
            when (currentScreen) {
                AppScreen.Camera -> CameraRoute(
                    cameraRuntime = cameraRuntime,
                    onNavigateToCompatibility = {
                        currentScreen = AppScreen.Compatibility
                    },
                    onShowDiagnostics = openDiagnostics
                )
                AppScreen.Compatibility -> CompatibilityScreen(
                    state = compatibilityState,
                    onNavigateBack = { currentScreen = AppScreen.Camera },
                    onRetry = compatibilityViewModel::retry,
                    onRefresh = compatibilityViewModel::refresh,
                    onShowDiagnostics = openDiagnostics,
                    hardwareEnabled = cameraRuntime.usesCameraHardware
                )
            }
        } else {
            PermissionRequiredScreen(onShowDiagnostics = openDiagnostics)
        }
        ErrorReportingHost(
            reporter = reporter,
            showHistory = showDiagnostics,
            onShowHistoryChange = { showDiagnostics = it }
        )
    }
}

@Composable
private fun PermissionRequiredScreen(onShowDiagnostics: () -> Unit) {
    // Show a message until permissions are granted
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Please grant camera and audio permissions to use the app.", color = Color.White)
            TextButton(onClick = onShowDiagnostics) {
                Text(stringResource(R.string.diagnostics_title))
            }
        }
    }
}
