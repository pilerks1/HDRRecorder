package com.pilerks1.hdrrecorder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pilerks1.hdrrecorder.ui.AppContent
import com.pilerks1.hdrrecorder.ui.camera.CameraRuntimeProvider
import com.pilerks1.hdrrecorder.ui.compatibility.CompatibilityViewModel
import kotlinx.coroutines.launch

/**
 * The main and only activity of the application.
 * request necessary permissions and set up the Jetpack Compose content view.
 */
class MainActivity : ComponentActivity() {
    private var startupReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val cameraRuntime = CameraRuntimeProvider.fromIntent(intent)
        // Dismiss the launch window after the initial permission/UI composition.
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
                val compatibilityViewModel: CompatibilityViewModel = viewModel()
                val compatibilityState by compatibilityViewModel.state.collectAsStateWithLifecycle()
                SideEffect {
                    startupReady = true
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

                AppContent(
                    cameraRuntime = cameraRuntime,
                    hasPermissions = hasPermissions,
                    compatibilityViewModel = compatibilityViewModel,
                    compatibilityState = compatibilityState,
                    reporter = errorReporter
                )
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
