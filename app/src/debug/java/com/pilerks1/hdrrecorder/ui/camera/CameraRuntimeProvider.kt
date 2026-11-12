package com.pilerks1.hdrrecorder.ui.camera

import android.content.Intent

/** Selects the optional UI testbed for debuggable builds. */
internal object CameraRuntimeProvider {
    private const val UI_TESTBED_EXTRA = "ui_testbed"

    fun fromIntent(intent: Intent): CameraRuntime =
        if (intent.getBooleanExtra(UI_TESTBED_EXTRA, false)) {
            UiTestbedCameraRuntime()
        } else {
            RealCameraRuntime
        }
}
