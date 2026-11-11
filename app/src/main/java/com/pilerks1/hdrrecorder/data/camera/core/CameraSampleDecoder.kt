package com.pilerks1.hdrrecorder.data.camera.core

import android.hardware.camera2.CaptureResult
import android.os.Build
import com.pilerks1.hdrrecorder.model.CameraSample

fun CameraFrameMetadata.toCameraSample(): CameraSample = CameraSample(
    cameraGeneration = generation,
    sensorSensitivityIso = this[CaptureResult.SENSOR_SENSITIVITY],
    postRawSensitivityBoost = this[CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST],
    shutterNanos = this[CaptureResult.SENSOR_EXPOSURE_TIME]?.takeIf { it > 0L },
    focusDistanceDiopters = this[CaptureResult.LENS_FOCUS_DISTANCE],
    cctTemperatureKelvin = if (Build.VERSION.SDK_INT >= 36) this[CaptureResult.COLOR_CORRECTION_COLOR_TEMPERATURE] else null,
    cctTint = if (Build.VERSION.SDK_INT >= 36) this[CaptureResult.COLOR_CORRECTION_COLOR_TINT] else null
)
