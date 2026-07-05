package com.pilerks1.hdrrecorder.ui.helpers

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
fun DisplayRotationListener(onRotationChanged: (Int) -> Unit) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                val display = displayManager.getDisplay(displayId) ?: return
                if (display.displayId == Display.DEFAULT_DISPLAY) {
                    @Suppress("DEPRECATION")
                    onRotationChanged(display.rotation)
                }
            }
        }

        displayManager.registerDisplayListener(listener, null)

        val initialDisplay = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        if (initialDisplay != null) {
            @Suppress("DEPRECATION")
            onRotationChanged(initialDisplay.rotation)
        }

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

@Composable
fun ScreenOrientationManagement(isRecording: Boolean) {
    val context = LocalContext.current
    val activity = context as Activity
    LaunchedEffect(isRecording) {
        if (isRecording) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
