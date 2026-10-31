package com.pilerks1.hdrrecorder.data.camera.core

import android.hardware.camera2.CameraMetadata as AndroidCameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Build
import androidx.annotation.RequiresApi
import com.pilerks1.hdrrecorder.model.TonemapCurves
import com.pilerks1.hdrrecorder.model.GammaCurve
import com.pilerks1.hdrrecorder.model.GammaMode
import com.pilerks1.hdrrecorder.model.GammaSettings

/** Processing preferences supplied independently of screen state. */
data class InteropSettings(
    val gamma: GammaSettings? = null,
    val camera2: Camera2Settings = Camera2Settings()
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
        applyFocus(builder, state, settings, capabilities)
        applyWhiteBalance(builder, state, capabilities)
        applyFps(builder, state, capabilities)
        applyCamera2Modes(builder, settings.camera2)
        applyGamma(builder, settings.gamma, capabilities)

        if (capabilities?.supportsAeLock == true && aeLockOverride != null) {
            builder.set(
                CaptureRequest.CONTROL_AE_LOCK,
                aeLockOverride
            )
        }

        return builder.build()
    }

    private fun exposureCompensationIndex(
        state: CameraControlValues,
        capabilities: CameraCapabilities?
    ): Int {
        if (!state.isManualEv || capabilities?.hasExposureCompensationControl != true) return 0
        return capabilities.evRange?.clamp(state.evValueIndex) ?: 0
    }

    // --- Exposure (ISO / SS / EV / AE mode) ---

    private fun applyExposure(
        builder: CameraRequestOverrides.Builder,
        state: CameraControlValues,
        capabilities: CameraCapabilities?
    ) {
        if (capabilities?.hasExposureCompensationControl == true) {
            builder.set(
                CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,
                exposureCompensationIndex(state, capabilities)
            )
        }
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
        settings: InteropSettings,
        capabilities: CameraCapabilities?
    ) {
        val mode = focusMode(state, settings, capabilities)
        mode?.let { builder.set(CaptureRequest.CONTROL_AF_MODE, it) }
        if (mode == AndroidCameraMetadata.CONTROL_AF_MODE_OFF && state.focusDistanceDiopters != null) {
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, state.focusDistanceDiopters)
        }
    }

    /** Null releases AF mode selection to CameraX. Manual distance always takes priority. */
    internal fun focusMode(
        state: CameraControlValues,
        settings: InteropSettings,
        capabilities: CameraCapabilities?
    ): Int? = if (state.isManualFocus && state.focusDistanceDiopters != null &&
        capabilities?.hasManualFocusControl == true
    ) AndroidCameraMetadata.CONTROL_AF_MODE_OFF
    else settings.camera2.selectedMode(Camera2Control.CONTROL_AF_MODE)

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

    // --- Independent Camera2 processing overrides ---

    private fun applyCamera2Modes(builder: CameraRequestOverrides.Builder, settings: Camera2Settings) {
        for (control in Camera2Control.entries) {
            if (control == Camera2Control.CONTROL_AF_MODE) continue // Focus policy owns this key.
            // Settings source controls the choices; do not filter against the active lens.
            settings.selectedMode(control)?.let { builder.set(control.requestKey, it) }
        }
    }

    // --- Gamma / Tonemap ---

    private fun applyGamma(
        builder: CameraRequestOverrides.Builder,
        settings: GammaSettings?,
        capabilities: CameraCapabilities?
    ) {
        settings ?: return
        val mode = settings.supportedMode(capabilities?.gammaModes.orEmpty()) ?: return
        builder.set(CaptureRequest.TONEMAP_MODE, mode.camera2Value)
        when (mode) {
            GammaMode.GAMMA_VALUE -> builder.set(CaptureRequest.TONEMAP_GAMMA, settings.value)
            GammaMode.CONTRAST_CURVE -> builder.set(CaptureRequest.TONEMAP_CURVE, when (settings.curve) {
                GammaCurve.HLG -> TonemapCurves.hlg()
                GammaCurve.PQ -> TonemapCurves.pq()
                GammaCurve.CUSTOM -> TonemapCurves.custom()
                GammaCurve.AUTO -> error("Select an explicit contrast curve")
            })
            GammaMode.PRESET_CURVE -> builder.set(CaptureRequest.TONEMAP_PRESET_CURVE, settings.preset.camera2Value)
            GammaMode.FAST, GammaMode.HIGH_QUALITY -> Unit
        }
    }

    private const val NANOS_PER_SECOND = 1_000_000_000L
}
