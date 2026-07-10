package com.pilerks1.hdrrecorder.data.camera

import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.util.Range
import android.util.Rational
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
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
    val cctTemperatureRange: Range<Int>? = null,
    val supportsShutterPriorityAe: Boolean = false,
    val supportsIsoPriorityAe: Boolean = false,
    val supportsNightMode: Boolean = false
)

object CameraCapabilitiesManager {

    /**
     * Extracts deep manual control limits from CameraX's CameraInfo wrapper.
     */
    @OptIn(ExperimentalCamera2Interop::class)
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
        
        // API 36-only capability probes are gated here so the checker's @RequiresApi(36)
        // methods are never called on older devices.
        val isApi36 = Build.VERSION.SDK_INT >= 36
        val cctTemperatureRange = if (isApi36) {
            DeviceCompatibilityChecker.getCctTemperatureRange(camera2Info)
        } else {
            null
        }

        return CameraCapabilities(
            isoRange = isoRange,
            ssRangeNanos = ssRange,
            evRange = evRange,
            evStep = evStep,
            focusMinDistanceDiopters = minFocus,
            fpsRanges = fpsRanges,
            supportsCCT = isApi36 && DeviceCompatibilityChecker.supportsCct(camera2Info),
            cctTemperatureRange = cctTemperatureRange,
            supportsShutterPriorityAe = isApi36 && DeviceCompatibilityChecker.supportsShutterPriorityAe(camera2Info),
            supportsIsoPriorityAe = isApi36 && DeviceCompatibilityChecker.supportsIsoPriorityAe(camera2Info),
            supportsNightMode = isApi36 && DeviceCompatibilityChecker.supportsNightMode(camera2Info)
        )
    }
}
