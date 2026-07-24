package com.pilerks1.hdrrecorder.data.camera

import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.Build
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import com.pilerks1.hdrrecorder.model.TonemapCurves
import com.pilerks1.hdrrecorder.model.GammaCurve
import com.pilerks1.hdrrecorder.ui.ManualControlsState

/**
 * Non-manual interop settings that come from CameraUiState rather than the manual sliders.
 * Kept as a small holder so the data layer doesn't depend on the whole UI state class.
 */
data class InteropSettings(
    val gammaCurve: GammaCurve = GammaCurve.AUTO,
    val isNoiseReductionEnabled: Boolean = true
)

/**
 * Single owner of ALL Camera2 interop writes. Builds one CaptureRequestOptions from the
 * manual control state plus the non-manual interop settings (noise reduction, gamma) and
 * applies it in a single setCaptureRequestOptions call, so nothing can overwrite anything else.
 */
@OptIn(ExperimentalCamera2Interop::class)
object CameraInteropApplier {

    fun apply(
        cameraControl: CameraControl,
        state: ManualControlsState,
        settings: InteropSettings,
        capabilities: CameraCapabilities?,
        applyExposureCompensation: Boolean
    ) {
        val camera2Control = Camera2CameraControl.from(cameraControl)
        val builder = CaptureRequestOptions.Builder()

        applyExposure(cameraControl, builder, state, capabilities, applyExposureCompensation)
        applyFocus(builder, state, capabilities)
        applyWhiteBalance(builder, state, capabilities)
        applyFps(builder, state)
        applyNoiseReduction(builder, settings.isNoiseReductionEnabled)
        applyGamma(builder, settings.gammaCurve)

        camera2Control.setCaptureRequestOptions(builder.build())
    }

    fun exposureCompensationIndex(
        state: ManualControlsState,
        capabilities: CameraCapabilities?
    ): Int = state.evValueIndex.takeIf {
        state.isManualEv && capabilities?.hasExposureCompensationControl == true
    } ?: 0

    // --- Exposure (ISO / SS / EV / AE mode) ---

    private fun applyExposure(
        cameraControl: CameraControl,
        builder: CaptureRequestOptions.Builder,
        state: ManualControlsState,
        capabilities: CameraCapabilities?,
        applyExposureCompensation: Boolean
    ) {
        // Native CameraX EV
        if (applyExposureCompensation && capabilities?.hasExposureCompensationControl == true) {
            cameraControl.setExposureCompensationIndex(
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
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, state.ssValueNanos)
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, state.isoValue)
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CameraMetadata.CONTROL_AE_MODE_OFF
                )
            }
            manualShutter && !manualIso &&
                capabilities.supportsShutterPriorityAe &&
                Build.VERSION.SDK_INT >= 36 -> {
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, state.ssValueNanos)
                applyHybridAe(builder, usesShutterPriority = true)
            }
            manualIso && !manualShutter &&
                capabilities.supportsIsoPriorityAe &&
                Build.VERSION.SDK_INT >= 36 -> {
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, state.isoValue)
                applyHybridAe(builder, usesShutterPriority = false)
            }
            else -> applyAutoExposure(builder, state, capabilities)
        }
    }

    /** Hybrid AE priority modes are Android 16 (API 36) only. */
    @RequiresApi(36)
    private fun applyHybridAe(
        builder: CaptureRequestOptions.Builder,
        usesShutterPriority: Boolean
    ) {
        builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
        val priorityMode = if (usesShutterPriority) {
            CameraMetadata.CONTROL_AE_PRIORITY_MODE_SENSOR_EXPOSURE_TIME_PRIORITY
        } else {
            CameraMetadata.CONTROL_AE_PRIORITY_MODE_SENSOR_SENSITIVITY_PRIORITY
        }
        builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_PRIORITY_MODE, priorityMode)
    }

    private fun applyAutoExposure(
        builder: CaptureRequestOptions.Builder,
        state: ManualControlsState,
        capabilities: CameraCapabilities?
    ) {
        // Night Mode (AE Low Light Boost) is API 36 only.
        if (
            state.isNightModeAeEnabled &&
            capabilities?.supportsNightMode == true &&
            Build.VERSION.SDK_INT >= 36
        ) {
            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CameraMetadata.CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY
            )
        } else {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
        }
    }

    // --- Focus ---

    private fun applyFocus(
        builder: CaptureRequestOptions.Builder,
        state: ManualControlsState,
        capabilities: CameraCapabilities?
    ) {
        if (
            state.isManualFocus &&
            state.focusDistanceDiopters != null &&
            capabilities?.hasManualFocusControl == true
        ) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, state.focusDistanceDiopters)
        } else if ((capabilities?.focusMinDistanceDiopters ?: 0f) > 0f) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
        }
    }

    // --- White Balance (CCT) ---

    private fun applyWhiteBalance(
        builder: CaptureRequestOptions.Builder,
        state: ManualControlsState,
        capabilities: CameraCapabilities?
    ) {
        // COLOR_CORRECTION_MODE_CCT is API 36 only; supportsCCT already gates on that.
        if (state.isManualWb && state.wbTemp != null && capabilities?.supportsCCT == true &&
            Build.VERSION.SDK_INT >= 36
        ) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_CCT)
            builder.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_COLOR_TEMPERATURE, state.wbTemp)
            builder.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_COLOR_TINT, state.wbTint ?: 0)
        } else {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO)
        }
    }

    // --- FPS ---

    private fun applyFps(builder: CaptureRequestOptions.Builder, state: ManualControlsState) {
        if (state.isManualFps && state.fpsRange != null) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, state.fpsRange)
        }
    }

    // --- Noise Reduction ---

    private fun applyNoiseReduction(builder: CaptureRequestOptions.Builder, enabled: Boolean) {
        if (enabled) {
            builder.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
            builder.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
        } else {
            builder.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
        }
    }

    // --- Gamma / Tonemap ---

    private fun applyGamma(builder: CaptureRequestOptions.Builder, gammaCurve: GammaCurve) {
        when (gammaCurve) {
            GammaCurve.AUTO -> builder.setCaptureRequestOption(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY)
            GammaCurve.HLG -> {
                builder.setCaptureRequestOption(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
                builder.setCaptureRequestOption(CaptureRequest.TONEMAP_CURVE, TonemapCurves.hlg())
            }
            GammaCurve.PQ -> {
                builder.setCaptureRequestOption(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
                builder.setCaptureRequestOption(CaptureRequest.TONEMAP_CURVE, TonemapCurves.pq())
            }
            GammaCurve.CUSTOM -> {
                builder.setCaptureRequestOption(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
                builder.setCaptureRequestOption(CaptureRequest.TONEMAP_CURVE, TonemapCurves.custom())
            }
        }
    }
}
