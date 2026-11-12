package com.pilerks1.hdrrecorder.camera.camera2

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.util.Range
import com.pilerks1.hdrrecorder.NANOS_PER_SECOND
import com.pilerks1.hdrrecorder.camera.camera2.CameraMetadata as ReportedCameraMetadata
import com.pilerks1.hdrrecorder.camera.model.CameraCapabilities

/** Projects reported Camera2 metadata into application control capabilities. */
object CameraCapabilitiesReader {

    fun extractCamera2Modes(metadata: ReportedCameraMetadata): Camera2ModeCapabilities =
        Camera2ModeCapabilities(Camera2Control.entries.associateWith { control ->
            if (control.requestKey !in metadata.requestKeys) emptyList()
            else metadata[control.availableModesKey]?.filter { mode ->
                control != Camera2Control.CONTROL_AF_MODE || mode in listOf(
                    CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO,
                    CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                    CameraMetadata.CONTROL_AF_MODE_EDOF
                )
            }?.distinct()?.sorted().orEmpty()
        })

    /**
     * Projects reported Camera2 metadata and separately supplied CameraX support into app controls.
     * Absent optional metadata reads as null; it is never treated as an error.
     */
    fun extractCapabilities(
        camera2Info: ReportedCameraMetadata,
        supportsLowLightBoost: Boolean,
        extendManualRanges: Boolean = false
    ): CameraCapabilities {

        // 1. ISO (Sensitivity)
        val isoRange = camera2Info[CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE]
        val postRawSensitivityBoostRange = camera2Info[CameraCharacteristics.CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE]

        // 2. Shutter Speed (Exposure Time in Nanos)
        val ssRange = camera2Info[CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE]

        // 3. Focus Distance (Diopters)
        val minFocus = camera2Info[CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE] ?: 0f
        val afModes = camera2Info[CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES]?.toList().orEmpty()

        // 4. EV Compensation
        val evRange = camera2Info[CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE]
        val evStep = camera2Info[CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP]

        // 5. FPS Ranges
        val fpsRanges = camera2Info[CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES]?.toList().orEmpty()
        val maxFrameDurationNanos = camera2Info[CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION]
        val supportsAeLock = camera2Info[CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE] == true
        val hasVariablePostRawSensitivityBoost = postRawSensitivityBoostRange?.let { it.lower != it.upper } == true

        val supportsManualSensor = camera2Info[CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES]
            ?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true
        val hasValidIsoRange = isoRange?.let { it.lower < it.upper } == true
        val hasValidShutterRange = ssRange?.let { it.lower < it.upper } == true

        // API 36-only keys are read only on API 36.
        val cctTemperatureRange: Range<Int>?
        val supportsCct: Boolean
        val supportsShutterPriorityAe: Boolean
        val supportsIsoPriorityAe: Boolean
        if (Build.VERSION.SDK_INT >= 36) {
            cctTemperatureRange = camera2Info[CameraCharacteristics.COLOR_CORRECTION_COLOR_TEMPERATURE_RANGE]
            supportsCct = cctTemperatureRange != null &&
                camera2Info[CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_MODES]
                    ?.contains(CameraMetadata.COLOR_CORRECTION_MODE_CCT) == true
            val aePriorityModes = camera2Info[CameraCharacteristics.CONTROL_AE_AVAILABLE_PRIORITY_MODES]
            supportsShutterPriorityAe =
                aePriorityModes?.contains(CameraMetadata.CONTROL_AE_PRIORITY_MODE_SENSOR_EXPOSURE_TIME_PRIORITY) == true
            supportsIsoPriorityAe =
                aePriorityModes?.contains(CameraMetadata.CONTROL_AE_PRIORITY_MODE_SENSOR_SENSITIVITY_PRIORITY) == true
        } else {
            cctTemperatureRange = null
            supportsCct = false
            supportsShutterPriorityAe = false
            supportsIsoPriorityAe = false
        }
        val supportsFullManualExposure =
            supportsManualSensor && hasValidIsoRange && hasValidShutterRange
        val supportsPostRawSensitivityBoost =
            supportsFullManualExposure && (extendManualRanges || hasVariablePostRawSensitivityBoost)
        val hasExposureCompensationControl = evRange?.let { range ->
            evStep?.let { step ->
                val hasNonZeroRange = range.lower != 0 || range.upper != 0
                val hasPositiveStep = step.numerator != 0 &&
                    step.denominator != 0 &&
                    (step.numerator > 0) == (step.denominator > 0)
                hasNonZeroRange && hasPositiveStep
            }
        } == true
        // Manual focus needs the focus-distance request key, AF OFF, and an adjustable lens.
        val hasManualFocusControl = CaptureRequest.LENS_FOCUS_DISTANCE in camera2Info.requestKeys &&
            CameraMetadata.CONTROL_AF_MODE_OFF in afModes && minFocus > 0f

        val tonemapModes = camera2Info[CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES]
        return CameraCapabilities(
            isoRange = if (extendManualRanges) Range(16, 25600) else isoRange,
            postRawSensitivityBoostRange = if (extendManualRanges) Range(10, 6400) else postRawSensitivityBoostRange,
            // 1/32768 second rounded to the nearest nanosecond, through 1 second.
            ssRangeNanos = if (extendManualRanges) Range(30_518L, NANOS_PER_SECOND) else ssRange,
            evRange = evRange,
            evStep = evStep,
            focusMinDistanceDiopters = minFocus,
            fpsRanges = fpsRanges,
            maxFrameDurationNanos = maxFrameDurationNanos,
            supportsFullManualExposure = supportsFullManualExposure,
            hasManualIsoControl = hasValidIsoRange &&
                (supportsFullManualExposure || supportsIsoPriorityAe),
            hasManualShutterControl = hasValidShutterRange &&
                (supportsFullManualExposure || supportsShutterPriorityAe),
            hasExposureCompensationControl = hasExposureCompensationControl,
            hasManualFocusControl = hasManualFocusControl,
            supportsCCT = supportsCct,
            cctTemperatureRange = cctTemperatureRange,
            supportsShutterPriorityAe = supportsShutterPriorityAe,
            supportsIsoPriorityAe = supportsIsoPriorityAe,
            supportsAeLock = supportsAeLock,
            supportsPostRawSensitivityBoost = supportsPostRawSensitivityBoost,
            supportsLowLightBoost = supportsLowLightBoost,
            maxAfRegions = camera2Info[CameraCharacteristics.CONTROL_MAX_REGIONS_AF] ?: 0,
            maxAeRegions = camera2Info[CameraCharacteristics.CONTROL_MAX_REGIONS_AE] ?: 0,
            maxAwbRegions = camera2Info[CameraCharacteristics.CONTROL_MAX_REGIONS_AWB] ?: 0,
            afModes = afModes,
            gammaModes = GammaMode.entries.filter { tonemapModes?.contains(it.camera2Value) == true }
        )
    }
}
