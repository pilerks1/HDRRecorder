package com.pilerks1.hdrrecorder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.pilerks1.hdrrecorder.compatibility.CompatibilityScreen
import com.pilerks1.hdrrecorder.compatibility.CcViewModel
import com.pilerks1.hdrrecorder.compatibility.Screen
import com.pilerks1.hdrrecorder.data.camera.CameraRuntimeProvider
import com.pilerks1.hdrrecorder.ui.CameraUI

/**
 * The main and only activity of the application.
 * request necessary permissions and set up the Jetpack Compose content view.
 */
class MainActivity : ComponentActivity() {
    private var startupReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cameraRuntime = CameraRuntimeProvider.fromIntent(intent)
        // Keep the launch window until saved-report loading has resolved the startup route.
        window.decorView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!startupReady) return false
                window.decorView.viewTreeObserver.removeOnPreDrawListener(this)
                return true
            }
        })

        setContent {
            MaterialTheme {
                var hasPermissions by remember {
                    mutableStateOf(!cameraRuntime.usesCameraHardware || permissionsGranted())
                }
                var currentScreen by rememberSaveable { mutableStateOf<Screen>(Screen.Camera) }
                val compatibilityViewModel: CcViewModel = viewModel()
                val compatibilityState by compatibilityViewModel.state.collectAsStateWithLifecycle()
                SideEffect {
                    startupReady = !cameraRuntime.usesCameraHardware || !hasPermissions || !compatibilityState.loading
                }

                LaunchedEffect(hasPermissions) {
                    if (hasPermissions && cameraRuntime.usesCameraHardware) compatibilityViewModel.initialize()
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    hasPermissions = permissionsGranted()
                }

                // Request permissions only once when the app starts
                LaunchedEffect(cameraRuntime) {
                    if (cameraRuntime.usesCameraHardware && !hasPermissions) {
                        permissionLauncher.launch(REQUIRED_PERMISSIONS)
                    }
                }

                if (hasPermissions) {
                    if (cameraRuntime.usesCameraHardware && !compatibilityState.ready) {
                        CompatibilityScreen(
                            state = compatibilityState,
                            onNavigateBack = {},
                            onRetry = compatibilityViewModel::retry,
                            onRefresh = compatibilityViewModel::refresh,
                            mandatory = true
                        )
                    } else when (currentScreen) {
                        is Screen.Camera -> CameraUI(
                            cameraRuntime = cameraRuntime,
                            onNavigateToCompatibility = { currentScreen = Screen.Compatibility }
                        )
                        is Screen.Compatibility -> CompatibilityScreen(
                            state = compatibilityState,
                            onNavigateBack = { currentScreen = Screen.Camera },
                            onRetry = compatibilityViewModel::retry,
                            onRefresh = compatibilityViewModel::refresh,
                            hardwareEnabled = cameraRuntime.usesCameraHardware
                        )
                    }
                } else {
                    // Show a message until permissions are granted
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Please grant camera and audio permissions to use the app.", color = Color.White)
                    }
                }
            }
        }
    }

    private fun permissionsGranted(): Boolean = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
}
