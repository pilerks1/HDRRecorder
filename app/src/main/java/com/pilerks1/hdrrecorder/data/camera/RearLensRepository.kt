package com.pilerks1.hdrrecorder.data.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.cameraId
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import com.pilerks1.hdrrecorder.data.camera.core.CameraCatalog
import com.pilerks1.hdrrecorder.data.camera.core.RearLens
import com.pilerks1.hdrrecorder.data.camera.core.RearLensInventory

/** Joins public CameraX discovery with the central Camera2 catalog without opening a camera. */
class RearLensRepository(context: Context) {
    private val catalog = CameraCatalog.get(context)

    fun discover(provider: ProcessCameraProvider): RearLensInventory {
        val defaultId = provider.getCameraInfo(CameraSelector.DEFAULT_BACK_CAMERA).cameraId
        val cameras = catalog.cameras().filter { it.lensFacing == CameraCharacteristics.LENS_FACING_BACK }
        val reported = cameras.associateBy { it.id }
        val lenses = cameras.map { camera ->
            RearLens(
                cameraId = camera.id,
                zoomRatio = catalog.lensZoomRatio(camera.id, defaultId),
                logicalParentIds = camera.logicalParentIds,
                zoomRatioRange = camera.zoomRatioRange,
                maxDigitalZoom = camera.maxDigitalZoom,
                isLogical = camera.physicalCameraIds.isNotEmpty()
            )
        }.filter { lens ->
            // Depth/auxiliary sensors without ordinary image output are not zoom lenses.
            reported[lens.cameraId]?.metadata?.getOrNull()
                ?.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) == true
        }
        return RearLensInventory(defaultId, lenses)
    }

    fun defaultCameraInfo(provider: ProcessCameraProvider, inventory: RearLensInventory): CameraInfo =
        provider.availableCameraInfos.first { it.cameraId == inventory.defaultCameraId }
}
