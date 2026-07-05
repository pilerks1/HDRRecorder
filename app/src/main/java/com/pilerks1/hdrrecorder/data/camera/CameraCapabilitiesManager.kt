package com.pilerks1.hdrrecorder.data.camera

import android.hardware.camera2.CameraCharacteristics
import android.util.Range
import android.util.Rational
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraInfo

/**
 * Data class representing the physical limits of the active camera hardware.
 */
data class CameraCapabilities(
    val isoRange: Range<Int>? = null,
    val ssRangeNanos: Range<Long>? = null,
    val evRange: Range<Int>? = null,
    val evStep: Rational? = null,
    val focusMinDistanceDiopters: Float = 0f,
    val fpsRanges: List<Range<Int>> = emptyList(),
    val supportsCCT: Boolean = false,
    val hasHybridAe: Boolean = false,
    val supportsNightMode: Boolean = false
)

object CameraCapabilitiesManager {

    /**
     * Extracts deep manual control limits from CameraX's CameraInfo wrapper.
     */
    fun extractCapabilities(cameraInfo: CameraInfo): CameraCapabilities {
        val camera2Info = Camera2CameraInfo.from(cameraInfo)
        
        // 1. ISO (Sensitivity)
        val isoRange = camera2Info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        
        // 2. Shutter Speed (Exposure Time in Nanos)
        val ssRange = camera2Info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        
        // 3. Focus Distance (Diopters)
        val minFocus = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        
        // 4. EV Compensation
        val evRange = camera2Info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val evStep = camera2Info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
        
        // 5. FPS Ranges
        val fpsArray = camera2Info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        val fpsRanges = fpsArray?.toList() ?: emptyList()
        
        return CameraCapabilities(
            isoRange = isoRange,
            ssRangeNanos = ssRange,
            evRange = evRange,
            evStep = evStep,
            focusMinDistanceDiopters = minFocus,
            fpsRanges = fpsRanges,
            supportsCCT = DeviceCompatibilityChecker.supportsCct(camera2Info),
            hasHybridAe = DeviceCompatibilityChecker.supportsHybridAe(camera2Info),
            supportsNightMode = DeviceCompatibilityChecker.supportsNightMode(camera2Info)
        )
    }
}
