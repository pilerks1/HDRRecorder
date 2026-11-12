package com.pilerks1.hdrrecorder.camera.camerax

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.cameraId
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import com.pilerks1.hdrrecorder.camera.camera2.CameraCatalog
import com.pilerks1.hdrrecorder.camera.model.DefaultCameraInventory
import com.pilerks1.hdrrecorder.camera.model.ZoomShortcut

/** The default camera owns capabilities; its physical children supply only zoom shortcuts. */
class DefaultBackCameraRepository(context: Context) {
    private val catalog = CameraCatalog.get(context)

    /** Throws IllegalArgumentException when the device has no back camera. */
    fun cameraInfo(provider: ProcessCameraProvider): CameraInfo =
        provider.getCameraInfo(CameraSelector.DEFAULT_BACK_CAMERA)

    /** Catalog metadata is a Result, so unreadable child cameras simply contribute no shortcut. */
    fun zoomInventory(cameraInfo: CameraInfo): DefaultCameraInventory {
        val defaultId = cameraInfo.cameraId
        val shortcuts = catalog.camera(defaultId).physicalCameraIds.sorted().mapNotNull { id ->
            // Depth/auxiliary sensors without ordinary image output are not zoom lenses.
            val isImageCamera = catalog.camera(id).metadata.getOrNull()
                ?.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) == true
            if (!isImageCamera) return@mapNotNull null
            catalog.lensZoomRatio(id, defaultId)?.let { ZoomShortcut(id, it) }
        }
        return DefaultCameraInventory(defaultId, shortcuts)
    }
}
