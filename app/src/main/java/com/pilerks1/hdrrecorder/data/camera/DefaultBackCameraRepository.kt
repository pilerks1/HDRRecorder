package com.pilerks1.hdrrecorder.data.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import androidx.camera.camera2.interop.cameraId
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import com.pilerks1.hdrrecorder.data.camera.core.CameraCatalog
import com.pilerks1.hdrrecorder.data.camera.core.DefaultCameraInventory
import com.pilerks1.hdrrecorder.data.camera.core.ZoomShortcut

/** The default camera owns capabilities; its physical children supply only zoom shortcuts. */
class DefaultBackCameraRepository(context: Context) {
    private val catalog = CameraCatalog.get(context)

    fun cameraInfo(provider: ProcessCameraProvider): CameraInfo =
        provider.getCameraInfo(CameraSelector.DEFAULT_BACK_CAMERA)

    fun zoomInventory(cameraInfo: CameraInfo): DefaultCameraInventory {
        val defaultId = cameraInfo.cameraId
        val physicalIds = catalog.camera(defaultId).physicalCameraIds
        val shortcuts = physicalIds.sorted().mapNotNull { id ->
            runCatching {
                // Depth/auxiliary sensors without ordinary image output are not zoom lenses.
                val isImageCamera = catalog.camera(id).metadata.getOrNull()
                    ?.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                    ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) == true
                if (!isImageCamera) return@runCatching null
                catalog.lensZoomRatio(id, defaultId)?.let { ZoomShortcut(id, it) }
            }.getOrElse { error ->
                Log.w("DefaultBackCamera", "Unable to derive zoom shortcut for physical camera $id", error)
                null
            }
        }
        return DefaultCameraInventory(defaultId, physicalIds, shortcuts)
    }
}
