package com.pilerks1.hdrrecorder.data.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.os.Build
import com.pilerks1.hdrrecorder.data.camera.core.CameraMetadata as ReportedCameraMetadata
import com.pilerks1.hdrrecorder.data.camera.core.DeviceCompatibilityChecker

/**
 * Data class representing the physical limits of the active camera hardware.
 */
typealias CameraCapabilities = com.pilerks1.hdrrecorder.data.camera.core.CameraCapabilities

object CameraCapabilitiesManager {

    /**
     * Projects reported Camera2 metadata and separately supplied CameraX support into app controls.
     */
    fun extractCapabilities(camera2Info: ReportedCameraMetadata, supportsLowLightBoost: Boolean): CameraCapabilities {
        
        // 1. ISO (Sensitivity)
        val isoRange = camera2Info.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val postRawSensitivityBoostRange = camera2Info.get(
            CameraCharacteristics.CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE
        )
        
        // 2. Shutter Speed (Exposure Time in Nanos)
        val ssRange = camera2Info.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        
        // 3. Focus Distance (Diopters)
        val minFocus = camera2Info.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        
        // 4. EV Compensation
        val evRange = camera2Info.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val evStep = camera2Info.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
        
        // 5. FPS Ranges
        val fpsArray = camera2Info.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        val fpsRanges = fpsArray?.toList() ?: emptyList()
        val maxFrameDurationNanos = camera2Info.get(
            CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION
        )
        val supportsAeLock = camera2Info.get(
            CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE
        ) == true
        val hasVariablePostRawSensitivityBoost = postRawSensitivityBoostRange?.let { range ->
            range.lower != range.upper && !(range.lower == 100 && range.upper == 100)
        } == true

        val requestCapabilities = camera2Info.get(
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
        val supportsPostRawSensitivityBoost =
            supportsFullManualExposure && hasVariablePostRawSensitivityBoost
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
            postRawSensitivityBoostRange = postRawSensitivityBoostRange,
            ssRangeNanos = ssRange,
            evRange = evRange,
            evStep = evStep,
            focusMinDistanceDiopters = minFocus,
            fpsRanges = fpsRanges,
            maxFrameDurationNanos = maxFrameDurationNanos,
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
            supportsAeLock = supportsAeLock,
            supportsPostRawSensitivityBoost = supportsPostRawSensitivityBoost,
            supportsLowLightBoost = supportsLowLightBoost
        )
    }
}
