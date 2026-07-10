package com.pilerks1.hdrrecorder.ui.helpers

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.display.DisplayManager
import android.view.WindowManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Lets Android drive the camera Activity through all four sensor orientations,
 * even when the system-wide rotation lock is enabled.
 *
 * During recording, locks to whatever orientation was active when recording started.
 */
@Composable
fun ActivityOrientationManagement(isRecording: Boolean) {
    val context = LocalContext.current
    val activity = context as Activity

    LaunchedEffect(isRecording) {
        activity.requestedOrientation = if (isRecording) {
            ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }
    }

    DisposableEffect(activity) {
        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

/**
 * Observes the rotation Android actually applied to this Activity's display.
 * This updates CameraX metadata only; it never changes Activity orientation or
 * requests a camera rebind.
 */
@Composable
fun DisplayRotationListener(onDisplayRotationChanged: (Int) -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val latestCallback = rememberUpdatedState(onDisplayRotationChanged)

    DisposableEffect(context, view) {
        val display = view.display ?: return@DisposableEffect onDispose { }
        val displayId = display.displayId
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit

            override fun onDisplayRemoved(displayId: Int) = Unit

            override fun onDisplayChanged(changedDisplayId: Int) {
                if (changedDisplayId == displayId) {
                    latestCallback.value(view.display?.rotation ?: display.rotation)
                }
            }
        }

        latestCallback.value(display.rotation)
        displayManager.registerDisplayListener(listener, null)

        onDispose {
            displayManager.unregisterDisplayListener(listener)
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
        DisposableEffect(window) {
            onDispose {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    window?.setDesiredHdrHeadroom(0.0f)
                }
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
