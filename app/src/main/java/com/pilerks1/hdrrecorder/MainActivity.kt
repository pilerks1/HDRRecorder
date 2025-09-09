package com.pilerks1.hdrrecorder

import android.Manifest
import android.os.Bundle
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.pilerks1.hdrrecorder.compatibility.CompatibilityScreen
import com.pilerks1.hdrrecorder.compatibility.Screen
import com.pilerks1.hdrrecorder.ui.CameraUI

/**
 * The main and only activity of the application.
 * request necessary permissions and set up the Jetpack Compose content view.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                var hasPermissions by remember { mutableStateOf(false) }
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Camera) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    if (permissions.values.all { it }) {
                        hasPermissions = true
                    }
                }

                // Request permissions only once when the app starts
                LaunchedEffect(Unit) {
                    permissionLauncher.launch(REQUIRED_PERMISSIONS)
                }

                if (hasPermissions) {
                    when (currentScreen) {
                        is Screen.Camera -> CameraUI(
                            onNavigateToCompatibility = { currentScreen = Screen.Compatibility }
                        )
                        is Screen.Compatibility -> CompatibilityScreen(
                            onNavigateBack = { currentScreen = Screen.Camera }
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

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
}
