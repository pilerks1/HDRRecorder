package com.pilerks1.hdrrecorder.data.camera

import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.CameraControl
import com.pilerks1.hdrrecorder.ui.ManualControlsState
import android.os.Build

object ManualControlInjector {

    /**
     * Injects manual controls into the CameraX session using both native CameraX and Camera2Interop.
     */
    fun inject(
        cameraControl: CameraControl,
        state: ManualControlsState,
        capabilities: CameraCapabilities?
    ) {
        val camera2Control = Camera2CameraControl.from(cameraControl)
        val builder = CaptureRequestOptions.Builder()

        // 1. Native CameraX EV
        // Note: EV uses ExposureState natively in CameraX, but we can set the index
        if (state.isManualEv && state.evValueIndex != 0) {
            cameraControl.setExposureCompensationIndex(state.evValueIndex)
        } else {
            cameraControl.setExposureCompensationIndex(0)
        }

        // 2. Camera2Interop for Deep Manuals
        var needsManualAe = false

        // Shutter Speed
        if (state.isManualSs && state.ssValueNanos != null) {
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, state.ssValueNanos)
            needsManualAe = true
        } else {
            builder.clearCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME)
        }

        // ISO
        if (state.isManualIso && state.isoValue != null) {
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, state.isoValue)
            needsManualAe = true
        } else {
            builder.clearCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY)
        }

        // Auto Exposure Mode Logic (Hybrid AE vs Full Manual vs Auto)
        if (needsManualAe) {
            if (capabilities?.hasHybridAe == true) { // Device supports Hybrid AE
                if (state.isManualSs && state.isManualIso) {
                    // Both manual, disable AE entirely
                    builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
                    builder.clearCaptureRequestOption(CaptureRequest.CONTROL_AE_PRIORITY_MODE)
                } else {
                    builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
                    // Determine priority based on last input
                    if (state.lastManualExposureInput == "SS" || (state.isManualSs && !state.isManualIso)) {
                        builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_PRIORITY_MODE, CameraMetadata.CONTROL_AE_PRIORITY_MODE_SENSOR_EXPOSURE_TIME_PRIORITY)
                    } else if (state.lastManualExposureInput == "ISO" || (state.isManualIso && !state.isManualSs)) {
                        builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_PRIORITY_MODE, CameraMetadata.CONTROL_AE_PRIORITY_MODE_SENSOR_SENSITIVITY_PRIORITY)
                    }
                }
            } else {
                // Fallback: If device doesn't support Hybrid AE, must turn off AE entirely
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            }
        } else {
            // Restore Auto Exposure
            if (state.isNightModeAeEnabled && Build.VERSION.SDK_INT >= 35) {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY)
            } else {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON)
            }
            if (capabilities?.hasHybridAe == true) {
                builder.clearCaptureRequestOption(CaptureRequest.CONTROL_AE_PRIORITY_MODE)
            }
        }

        // Focus Distance
        if (state.isManualFocus && state.focusDistanceDiopters != null) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, state.focusDistanceDiopters)
        } else {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            builder.clearCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE)
        }

        // White Balance (CCT)
        if (state.isManualWb && state.wbTemp != null && capabilities?.supportsCCT == true) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_CCT)
            builder.setCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_COLOR_TEMPERATURE, state.wbTemp)
            // Tint can also be injected here if supported
        } else {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO)
            builder.clearCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_MODE)
            builder.clearCaptureRequestOption(CaptureRequest.COLOR_CORRECTION_COLOR_TEMPERATURE)
        }

        // FPS Range
        if (state.isManualFps && state.fpsRange != null) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, state.fpsRange)
        } else {
            builder.clearCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE)
        }

        // Apply
        camera2Control.setCaptureRequestOptions(builder.build())
    }
}
