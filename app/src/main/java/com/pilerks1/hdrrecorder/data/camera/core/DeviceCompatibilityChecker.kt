package com.pilerks1.hdrrecorder.data.camera.core

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata as AndroidCameraMetadata
import androidx.annotation.RequiresApi

/**
 * A dedicated checker for evaluating device capability flags based on OS version and hardware characteristics.
 */
object DeviceCompatibilityChecker {

    /**
     * Checks if the device supports precise Color Correction Temperature (CCT).
     * Requires Android 16 (API 36) and hardware support for COLOR_CORRECTION_MODE_CCT.
     */
    @RequiresApi(36)
    fun supportsCct(camera2Info: CameraMetadata): Boolean {
        return try {
            val modes = camera2Info.get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_MODES)
            val temperatureRange = getCctTemperatureRange(camera2Info)
            modes?.contains(AndroidCameraMetadata.COLOR_CORRECTION_MODE_CCT) == true &&
                temperatureRange != null
        } catch (e: Exception) {
            false
        }
    }

    @RequiresApi(36)
    fun getCctTemperatureRange(camera2Info: CameraMetadata) =
        try {
            camera2Info.get(CameraCharacteristics.COLOR_CORRECTION_COLOR_TEMPERATURE_RANGE)
        } catch (e: Exception) {
            null
        }

    /** Checks support for Android 16 shutter-priority Hybrid AE. */
    @RequiresApi(36)
    fun supportsShutterPriorityAe(camera2Info: CameraMetadata): Boolean {
        return try {
            val modes = camera2Info.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_PRIORITY_MODES)
            modes?.contains(AndroidCameraMetadata.CONTROL_AE_PRIORITY_MODE_SENSOR_EXPOSURE_TIME_PRIORITY) == true
        } catch (e: Exception) {
            false
        }
    }

    /** Checks support for Android 16 ISO-priority Hybrid AE. */
    @RequiresApi(36)
    fun supportsIsoPriorityAe(camera2Info: CameraMetadata): Boolean {
        return try {
            val modes = camera2Info.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_PRIORITY_MODES)
            modes?.contains(AndroidCameraMetadata.CONTROL_AE_PRIORITY_MODE_SENSOR_SENSITIVITY_PRIORITY) == true
        } catch (e: Exception) {
            false
        }
    }

}
