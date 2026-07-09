package com.pilerks1.hdrrecorder.ui.helpers

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Accelerometer-based device orientation listener. Fires with 0-359 degrees
 * regardless of whether the OS screen rotation lock is on.
 * This is the SINGLE source of rotation truth for the entire app.
 */
@Composable
fun DeviceOrientationListener(onOrientationDegrees: (Int) -> Unit) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                onOrientationDegrees(orientation)
            }
        }
        listener.enable()

        onDispose {
            listener.disable()
        }
    }
}

/**
 * Forces the Activity into the orientation that matches the current device tilt,
 * even when the user's OS rotation lock is enabled. This gives "rotate while locked"
 * behavior (like Samsung Camera).
 *
 * During recording, locks to whatever orientation was active when recording started.
 */
@Composable
fun DeviceOrientationManagement(deviceRotation: Int, isRecording: Boolean) {
    val context = LocalContext.current
    val activity = context as Activity

    LaunchedEffect(deviceRotation, isRecording) {
        if (isRecording) {
            // Freeze the Activity at its current orientation while recording.
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else {
            // Force the Activity to match the detected device tilt.
            // This overrides the OS rotation lock — the app is explicitly requesting.
            activity.requestedOrientation = when (deviceRotation) {
                Surface.ROTATION_90 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                Surface.ROTATION_270 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }
}

@Composable
fun HdrBrightnessManagement(shouldLimitBrightness: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val context = LocalContext.current
        val window = (context as? Activity)?.window
        LaunchedEffect(shouldLimitBrightness) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                window?.setDesiredHdrHeadroom(if (shouldLimitBrightness) 1.0f else 0.0f)
            }
        }
    }
}

@Composable
fun SystemUiManagement() {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        DisposableEffect(Unit) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            onDispose {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

@Composable
fun ScreenTimeoutManagement(isRecording: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        LaunchedEffect(isRecording) {
            if (isRecording) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}
