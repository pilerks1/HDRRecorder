package com.pilerks1.hdrrecorder.data.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop

/**
 * A dedicated checker for evaluating device capability flags based on OS version and hardware characteristics.
 */
object DeviceCompatibilityChecker {

    /**
     * Checks if the device supports precise Color Correction Temperature (CCT).
     * Requires Android 16 (API 36) and hardware support for COLOR_CORRECTION_MODE_CCT.
     */
    @RequiresApi(36)
    @OptIn(ExperimentalCamera2Interop::class)
    fun supportsCct(camera2Info: Camera2CameraInfo): Boolean {
        return try {
            val modes = camera2Info.getCameraCharacteristic(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_MODES)
            val temperatureRange = getCctTemperatureRange(camera2Info)
            modes?.contains(CameraMetadata.COLOR_CORRECTION_MODE_CCT) == true &&
                temperatureRange != null
        } catch (e: Exception) {
            false
        }
    }

    @RequiresApi(36)
    @OptIn(ExperimentalCamera2Interop::class)
    fun getCctTemperatureRange(camera2Info: Camera2CameraInfo) =
        try {
            camera2Info.getCameraCharacteristic(CameraCharacteristics.COLOR_CORRECTION_COLOR_TEMPERATURE_RANGE)
        } catch (e: Exception) {
            null
        }

    /** Checks support for Android 16 shutter-priority Hybrid AE. */
    @RequiresApi(36)
    @OptIn(ExperimentalCamera2Interop::class)
    fun supportsShutterPriorityAe(camera2Info: Camera2CameraInfo): Boolean {
        return try {
            val modes = camera2Info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_PRIORITY_MODES)
            modes?.contains(CameraMetadata.CONTROL_AE_PRIORITY_MODE_SENSOR_EXPOSURE_TIME_PRIORITY) == true
        } catch (e: Exception) {
            false
        }
    }

    /** Checks support for Android 16 ISO-priority Hybrid AE. */
    @RequiresApi(36)
    @OptIn(ExperimentalCamera2Interop::class)
    fun supportsIsoPriorityAe(camera2Info: Camera2CameraInfo): Boolean {
        return try {
            val modes = camera2Info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_PRIORITY_MODES)
            modes?.contains(CameraMetadata.CONTROL_AE_PRIORITY_MODE_SENSOR_SENSITIVITY_PRIORITY) == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the device supports AE Low Light Boost (Night Mode).
     * Requires Android 16 (API 36).
     */
    @RequiresApi(36)
    @OptIn(ExperimentalCamera2Interop::class)
    fun supportsNightMode(camera2Info: Camera2CameraInfo): Boolean {
        return try {
            val modes = camera2Info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
            modes?.contains(CameraMetadata.CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY) == true
        } catch (e: Exception) {
            false
        }
    }
}
