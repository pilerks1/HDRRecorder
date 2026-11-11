package com.pilerks1.hdrrecorder.data.camera.core

/** Unsupported individual priority modes require paired manual controls. */
enum class ExposureMode {
    AUTO, SHUTTER_PRIORITY, SENSITIVITY_PRIORITY, FULL_MANUAL;

    companion object {
        fun resolve(controls: CameraControlValues, caps: CameraCapabilities?): ExposureMode = when {
            caps == null || (!controls.isManualIso && !controls.isManualSs) -> AUTO
            controls.isManualIso && controls.isManualSs ->
                if (caps.supportsFullManualExposure) FULL_MANUAL else AUTO
            controls.isManualIso && caps.supportsIsoPriorityAe -> SENSITIVITY_PRIORITY
            controls.isManualSs && caps.supportsShutterPriorityAe -> SHUTTER_PRIORITY
            caps.supportsFullManualExposure -> FULL_MANUAL
            else -> AUTO
        }
    }
}
