package com.pilerks1.hdrrecorder.data.camera.core

import android.Manifest
import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import java.util.concurrent.Executor
import kotlin.math.roundToInt

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
    val zoomRatioRange get() = metadata.getOrNull()?.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
    val maxDigitalZoom get() = metadata.getOrNull()?.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
    val physicalCameraIds get() = metadata.getOrNull()?.physicalCameraIds.orEmpty()

    /** Focal length per active sensor length: magnification, rather than a ratio of FoV angles. */
    internal fun normalizedFocalLength(): Float? {
        val info = metadata.getOrNull() ?: return null
        val focalLength = focalLengths?.firstOrNull()?.takeIf { it > 0f } ?: return null
        val sensor = info[CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE] ?: return null
        val active = info[CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE] ?: return null
        val pixels = info[CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE] ?: return null
        val orientation = info[CameraCharacteristics.SENSOR_ORIENTATION] ?: return null
        val rotated = orientation == 90 || orientation == 270
        val length = if (rotated) sensor.height * active.height() / pixels.height
            else sensor.width * active.width() / pixels.width
        return (focalLength / length).takeIf { it.isFinite() && it > 0f }
    }
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

    fun lensZoomRatio(id: String, referenceId: String): Float? {
        if (id == referenceId) return 1f
        val reference = camera(referenceId).normalizedFocalLength() ?: return null
        val focalLength = camera(id).normalizedFocalLength() ?: return null
        return ((focalLength / reference * 100f).roundToInt() / 100f).takeIf { it > 0f }
    }

    companion object {
        @Volatile private var instance: CameraCatalog? = null
        fun get(context: Context): CameraCatalog = instance ?: synchronized(this) {
            instance ?: CameraCatalog(context.applicationContext as Application).also { instance = it }
        }
    }
}
