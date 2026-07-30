package com.pilerks1.hdrrecorder.data.camera

import android.content.Intent

/** Release builds never inspect or enable debug camera runtimes. */
internal object CameraRuntimeProvider {
    @Suppress("UNUSED_PARAMETER")
    fun fromIntent(intent: Intent): CameraRuntime = RealCameraRuntime
}
