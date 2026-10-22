package com.pilerks1.hdrrecorder.data.camera.core

/** A backend owns submission; the shared layer never manipulates its device or surfaces. */
interface CameraRequestBackend {
    val cameraId: String
    val generation: Long
    suspend fun apply(overrides: CameraRequestOverrides)
    fun detach()
}
