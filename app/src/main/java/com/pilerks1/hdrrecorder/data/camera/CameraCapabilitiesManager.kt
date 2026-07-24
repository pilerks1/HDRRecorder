package com.pilerks1.hdrrecorder.data.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
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
    val supportsManualSensor: Boolean = false,
    val supportsFullManualExposure: Boolean = false,
    val hasManualIsoControl: Boolean = false,
    val hasManualShutterControl: Boolean = false,
    val hasExposureCompensationControl: Boolean = false,
    val hasManualFocusControl: Boolean = false,
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

        val requestCapabilities = camera2Info.getCameraCharacteristic(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
        )
        val supportsManualSensor = requestCapabilities
            ?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true
        val hasValidIsoRange = isoRange?.let { it.lower < it.upper } == true
        val hasValidShutterRange = ssRange?.let { it.lower < it.upper } == true

        // API 36-only capability probes are gated here so the checker's @RequiresApi(36)
        // methods are never called on older devices.
        val isApi36 = Build.VERSION.SDK_INT >= 36
        val cctTemperatureRange = if (isApi36) {
            DeviceCompatibilityChecker.getCctTemperatureRange(camera2Info)
        } else {
            null
        }
        val supportsShutterPriorityAe =
            isApi36 && DeviceCompatibilityChecker.supportsShutterPriorityAe(camera2Info)
        val supportsIsoPriorityAe =
            isApi36 && DeviceCompatibilityChecker.supportsIsoPriorityAe(camera2Info)
        val supportsFullManualExposure =
            supportsManualSensor && hasValidIsoRange && hasValidShutterRange
        val hasExposureCompensationControl = evRange?.let { range ->
            evStep?.let { step ->
                val hasNonZeroRange = range.lower != 0 || range.upper != 0
                val hasPositiveStep = step.numerator != 0 &&
                    step.denominator != 0 &&
                    (step.numerator > 0) == (step.denominator > 0)
                hasNonZeroRange && hasPositiveStep
            }
        } == true

        return CameraCapabilities(
            isoRange = isoRange,
            ssRangeNanos = ssRange,
            evRange = evRange,
            evStep = evStep,
            focusMinDistanceDiopters = minFocus,
            fpsRanges = fpsRanges,
            supportsManualSensor = supportsManualSensor,
            supportsFullManualExposure = supportsFullManualExposure,
            hasManualIsoControl = hasValidIsoRange &&
                (supportsFullManualExposure || supportsIsoPriorityAe),
            hasManualShutterControl = hasValidShutterRange &&
                (supportsFullManualExposure || supportsShutterPriorityAe),
            hasExposureCompensationControl = hasExposureCompensationControl,
            hasManualFocusControl = supportsManualSensor && minFocus > 0f,
            supportsCCT = isApi36 && DeviceCompatibilityChecker.supportsCct(camera2Info),
            cctTemperatureRange = cctTemperatureRange,
            supportsShutterPriorityAe = supportsShutterPriorityAe,
            supportsIsoPriorityAe = supportsIsoPriorityAe,
            supportsNightMode = isApi36 && DeviceCompatibilityChecker.supportsNightMode(camera2Info)
        )
    }
}
