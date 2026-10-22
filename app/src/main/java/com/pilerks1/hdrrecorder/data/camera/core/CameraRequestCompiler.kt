package com.pilerks1.hdrrecorder.data.camera.core

import android.hardware.camera2.CameraMetadata as AndroidCameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Build
import androidx.annotation.RequiresApi
import com.pilerks1.hdrrecorder.model.TonemapCurves
import com.pilerks1.hdrrecorder.model.GammaCurve

/** Processing preferences supplied independently of screen state. */
data class InteropSettings(
    val gammaCurve: GammaCurve = GammaCurve.AUTO,
    val isNoiseReductionEnabled: Boolean = true
)

/** Existing control policies contribute to the generic request snapshot. */
object CameraRequestCompiler {

    fun compile(
        state: CameraControlValues,
        settings: InteropSettings,
        capabilities: CameraCapabilities?,
        aeLockOverride: Boolean? = null
    ): CameraRequestOverrides {
        val builder = CameraRequestOverrides.Builder()
        applyExposure(builder, state, capabilities)
        applyFocus(builder, state, capabilities)
        applyWhiteBalance(builder, state, capabilities)
        applyFps(builder, state, capabilities)
        applyNoiseReduction(builder, settings.isNoiseReductionEnabled)
        applyGamma(builder, settings.gammaCurve)

        if (capabilities?.supportsAeLock == true && aeLockOverride != null) {
            builder.set(
                CaptureRequest.CONTROL_AE_LOCK,
                aeLockOverride
            )
        }

        return builder.build()
    }

    fun exposureCompensationIndex(
        state: CameraControlValues,
        capabilities: CameraCapabilities?
    ): Int = state.evValueIndex.takeIf {
        state.isManualEv && capabilities?.hasExposureCompensationControl == true
    } ?: 0

    // --- Exposure (ISO / SS / EV / AE mode) ---

    private fun applyExposure(
        builder: CameraRequestOverrides.Builder,
        state: CameraControlValues,
        capabilities: CameraCapabilities?
    ) {
        val manualShutter = state.isManualSs &&
            state.ssValueNanos != null &&
            capabilities?.hasManualShutterControl == true
        val manualIso = state.isManualIso &&
            state.isoValue != null &&
            capabilities?.hasManualIsoControl == true

        when {
            manualShutter && manualIso && capabilities.supportsFullManualExposure -> {
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, state.ssValueNanos)
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, state.isoValue)
                if (
                    state.postRawSensitivityBoost != null &&
                    state.isManualPostRawSensitivity &&
                    capabilities.supportsPostRawSensitivityBoost
                ) {
                    builder.set(
                        CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST,
                        state.postRawSensitivityBoost
                    )
                }
                builder.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    AndroidCameraMetadata.CONTROL_AE_MODE_OFF
                )
            }
            manualShutter && !manualIso &&
                capabilities.supportsShutterPriorityAe &&
                Build.VERSION.SDK_INT >= 36 -> {
                builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, state.ssValueNanos)
                applyHybridAe(builder, usesShutterPriority = true)
            }
            manualIso && !manualShutter &&
                capabilities.supportsIsoPriorityAe &&
                Build.VERSION.SDK_INT >= 36 -> {
                builder.set(CaptureRequest.SENSOR_SENSITIVITY, state.isoValue)
                applyHybridAe(builder, usesShutterPriority = false)
            }
            // CameraX owns automatic AE, including low-light boost. Omitting the Camera2
            // override here also clears a previous manual/hybrid AE override.
            else -> Unit
        }
    }

    /** Hybrid AE priority modes are Android 16 (API 36) only. */
    @RequiresApi(36)
    private fun applyHybridAe(
        builder: CameraRequestOverrides.Builder,
        usesShutterPriority: Boolean
    ) {
        builder.set(CaptureRequest.CONTROL_AE_MODE, AndroidCameraMetadata.CONTROL_AE_MODE_ON)
        val priorityMode = if (usesShutterPriority) {
            AndroidCameraMetadata.CONTROL_AE_PRIORITY_MODE_SENSOR_EXPOSURE_TIME_PRIORITY
        } else {
            AndroidCameraMetadata.CONTROL_AE_PRIORITY_MODE_SENSOR_SENSITIVITY_PRIORITY
        }
        builder.set(CaptureRequest.CONTROL_AE_PRIORITY_MODE, priorityMode)
    }

    // --- Focus ---

    private fun applyFocus(
        builder: CameraRequestOverrides.Builder,
        state: CameraControlValues,
        capabilities: CameraCapabilities?
    ) {
        if (
            state.isManualFocus &&
            state.focusDistanceDiopters != null &&
            capabilities?.hasManualFocusControl == true
        ) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, AndroidCameraMetadata.CONTROL_AF_MODE_OFF)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, state.focusDistanceDiopters)
        } else if ((capabilities?.focusMinDistanceDiopters ?: 0f) > 0f) {
            builder.set(CaptureRequest.CONTROL_AF_MODE, AndroidCameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
        }
    }

    // --- White Balance (CCT) ---

    private fun applyWhiteBalance(
        builder: CameraRequestOverrides.Builder,
        state: CameraControlValues,
        capabilities: CameraCapabilities?
    ) {
        // COLOR_CORRECTION_MODE_CCT is API 36 only; supportsCCT already gates on that.
        if (state.isManualWb && state.wbTemp != null && capabilities?.supportsCCT == true &&
            Build.VERSION.SDK_INT >= 36
        ) {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, AndroidCameraMetadata.CONTROL_AWB_MODE_OFF)
            builder.set(CaptureRequest.COLOR_CORRECTION_MODE, AndroidCameraMetadata.COLOR_CORRECTION_MODE_CCT)
            builder.set(CaptureRequest.COLOR_CORRECTION_COLOR_TEMPERATURE, state.wbTemp)
            builder.set(CaptureRequest.COLOR_CORRECTION_COLOR_TINT, state.wbTint ?: 0)
        } else {
            builder.set(CaptureRequest.CONTROL_AWB_MODE, AndroidCameraMetadata.CONTROL_AWB_MODE_AUTO)
        }
    }

    // --- FPS ---

    private fun applyFps(
        builder: CameraRequestOverrides.Builder,
        state: CameraControlValues,
        capabilities: CameraCapabilities?
    ) {
        if (state.isManualFps && state.fpsRange != null) {
            val fullManual = state.isManualIso &&
                state.isManualSs &&
                capabilities?.supportsFullManualExposure == true
            if (fullManual) {
                val requestedDuration = NANOS_PER_SECOND / state.fpsRange.upper.coerceAtLeast(1)
                val frameDuration = capabilities.maxFrameDurationNanos
                    ?.let(requestedDuration::coerceAtMost)
                    ?: requestedDuration
                builder.set(
                    CaptureRequest.SENSOR_FRAME_DURATION,
                    frameDuration
                )
            } else {
                builder.set(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    state.fpsRange
                )
            }
        }
    }

    // --- Noise Reduction ---

    private fun applyNoiseReduction(builder: CameraRequestOverrides.Builder, enabled: Boolean) {
        if (enabled) {
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
            builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
        } else {
            builder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
            builder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
        }
    }

    // --- Gamma / Tonemap ---

    private fun applyGamma(builder: CameraRequestOverrides.Builder, gammaCurve: GammaCurve) {
        when (gammaCurve) {
            GammaCurve.AUTO -> builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY)
            GammaCurve.HLG -> {
                builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
                builder.set(CaptureRequest.TONEMAP_CURVE, TonemapCurves.hlg())
            }
            GammaCurve.PQ -> {
                builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
                builder.set(CaptureRequest.TONEMAP_CURVE, TonemapCurves.pq())
            }
            GammaCurve.CUSTOM -> {
                builder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
                builder.set(CaptureRequest.TONEMAP_CURVE, TonemapCurves.custom())
            }
        }
    }

    private const val NANOS_PER_SECOND = 1_000_000_000L
}
