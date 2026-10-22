package com.pilerks1.hdrrecorder.data.camera.core

import android.Manifest
import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import java.util.concurrent.Executor

/** Raw reported metadata. App control availability is a separate projection. */
class CameraMetadata(private val characteristics: CameraCharacteristics) {
    operator fun <T> get(key: CameraCharacteristics.Key<T>): T? = characteristics.get(key)
    val characteristicKeys: List<CameraCharacteristics.Key<*>> get() = characteristics.keys.toList()
    val requestKeys: List<CaptureRequest.Key<*>> get() = characteristics.availableCaptureRequestKeys.orEmpty().toList()
    val resultKeys: List<CaptureResult.Key<*>> get() = characteristics.availableCaptureResultKeys.orEmpty().toList()
    val sessionKeys: List<CaptureRequest.Key<*>> get() = characteristics.availableSessionKeys.orEmpty().toList()
    val physicalRequestKeys: List<CaptureRequest.Key<*>> get() = characteristics.availablePhysicalCameraRequestKeys.orEmpty().toList()
    val physicalCameraIds: Set<String> get() = characteristics.physicalCameraIds.toSet()
}

data class CameraDescriptor(
    val id: String,
    val isDirectlyOpenable: Boolean,
    val logicalParentIds: Set<String>,
    val metadata: Result<CameraMetadata>
) {
    val lensFacing get() = metadata.getOrNull()?.get(CameraCharacteristics.LENS_FACING)
    val focalLengths get() = metadata.getOrNull()?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList()
}

/** Process-local catalog. Does not open cameras or change active sessions. */
class CameraCatalog private constructor(private val application: Application) {
    private val manager = application.getSystemService(CameraManager::class.java)
    private var cached: List<CameraDescriptor>? = null
    private var permission: Int? = null

    init {
        manager.registerAvailabilityCallback(Executor { it.run() }, object : CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(cameraId: String) = invalidate()
            override fun onCameraUnavailable(cameraId: String) = invalidate()
            override fun onPhysicalCameraAvailable(cameraId: String, physicalCameraId: String) = invalidate()
            override fun onPhysicalCameraUnavailable(cameraId: String, physicalCameraId: String) = invalidate()
        })
    }

    @Synchronized
    fun invalidate() { cached = null }

    @Synchronized
    fun cameras(): List<CameraDescriptor> {
        val currentPermission = application.checkSelfPermission(Manifest.permission.CAMERA)
        if (permission != currentPermission) cached = null
        permission = currentPermission
        cached?.let { return it }
        val topLevel = manager.cameraIdList.toSet()
        val metadata = linkedMapOf<String, Result<CameraMetadata>>()
        val parents = mutableMapOf<String, MutableSet<String>>()
        fun read(id: String) = metadata.getOrPut(id) {
            runCatching { CameraMetadata(manager.getCameraCharacteristics(id)) }
        }
        topLevel.forEach { id ->
            read(id).getOrNull()?.physicalCameraIds.orEmpty().forEach { physicalId ->
                parents.getOrPut(physicalId) { linkedSetOf() }.add(id)
                read(physicalId)
            }
        }
        return metadata.map { (id, result) ->
            CameraDescriptor(id, id in topLevel, parents[id].orEmpty().toSet(), result)
        }.also { cached = it }
    }

    fun camera(id: String): CameraDescriptor = cameras().firstOrNull { it.id == id }
        ?: error("Camera $id is not reported by Android")

    companion object {
        @Volatile private var instance: CameraCatalog? = null
        fun get(context: Context): CameraCatalog = instance ?: synchronized(this) {
            instance ?: CameraCatalog(context.applicationContext as Application).also { instance = it }
        }
    }
}
