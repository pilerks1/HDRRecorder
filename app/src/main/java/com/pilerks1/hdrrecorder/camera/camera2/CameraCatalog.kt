package com.pilerks1.hdrrecorder.camera.camera2

import android.Manifest
import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import kotlin.math.roundToInt

/** Raw reported metadata. App control availability is a separate projection. */
class CameraMetadata(private val characteristics: CameraCharacteristics) {
    operator fun <T> get(key: CameraCharacteristics.Key<T>): T? = characteristics.get(key)
    val requestKeys: List<CaptureRequest.Key<*>> get() = characteristics.availableCaptureRequestKeys.orEmpty().toList()
    val physicalCameraIds: Set<String> get() = characteristics.physicalCameraIds.toSet()
}

data class CameraDescriptor(
    val id: String,
    val metadata: Result<CameraMetadata>
) {
    val physicalCameraIds get() = metadata.getOrNull()?.physicalCameraIds.orEmpty()

    /** Focal length per active sensor length: magnification, rather than a ratio of FoV angles. */
    internal fun normalizedFocalLength(): Float? {
        val info = metadata.getOrNull() ?: return null
        val focalLength = info[CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS]
            ?.firstOrNull()?.takeIf { it > 0f } ?: return null
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

/**
 * Process-local catalog of built-in cameras. Their characteristics are immutable, so opening
 * or closing a camera never invalidates the cache; only a camera-permission change does,
 * because some keys are withheld without that permission. Does not open cameras.
 */
class CameraCatalog private constructor(private val application: Application) {
    private val manager = application.getSystemService(CameraManager::class.java)
    private val cached = mutableMapOf<String, CameraDescriptor>()
    private var permission: Int? = null

    @Synchronized
    fun camera(id: String): CameraDescriptor {
        val currentPermission = application.checkSelfPermission(Manifest.permission.CAMERA)
        if (permission != currentPermission) {
            cached.clear()
            permission = currentPermission
        }
        return cached.getOrPut(id) {
            CameraDescriptor(id, runCatching { CameraMetadata(manager.getCameraCharacteristics(id)) })
        }
    }

    fun lensZoomRatio(id: String, referenceId: String): Float? {
        if (id == referenceId) return 1f
        val reference = camera(referenceId).normalizedFocalLength() ?: return null
        val focalLength = camera(id).normalizedFocalLength() ?: return null
        return ((focalLength / reference * 10f).roundToInt() / 10f).takeIf { it > 0f }
    }

    companion object {
        @Volatile private var instance: CameraCatalog? = null
        fun get(context: Context): CameraCatalog = instance ?: synchronized(this) {
            instance ?: CameraCatalog(context.applicationContext as Application).also { instance = it }
        }
    }
}
