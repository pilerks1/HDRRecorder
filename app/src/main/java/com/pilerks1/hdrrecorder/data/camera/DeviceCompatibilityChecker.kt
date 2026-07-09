package com.pilerks1.hdrrecorder.data.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.os.Build
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
    @OptIn(ExperimentalCamera2Interop::class)
    fun supportsCct(camera2Info: Camera2CameraInfo): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false

        return try {
            val caps = camera2Info.getCameraCharacteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            caps?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING) == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the device supports Hybrid AE (Sensor Exposure Time Priority / Sensor Sensitivity Priority).
     * Requires Android 16 (API 36) and the priority modes to be explicitly listed.
     */
    @RequiresApi(36)
    @OptIn(ExperimentalCamera2Interop::class)
    fun supportsHybridAe(camera2Info: Camera2CameraInfo): Boolean {
        return try {
            val modes = camera2Info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_PRIORITY_MODES)
            modes?.contains(CameraMetadata.CONTROL_AE_PRIORITY_MODE_SENSOR_EXPOSURE_TIME_PRIORITY) == true
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
