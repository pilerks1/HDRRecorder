package com.pilerks1.hdrrecorder.camera.camerax

import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.os.Build
import android.util.Range
import com.pilerks1.hdrrecorder.camera.camera2.CameraFrameMetadata
import com.pilerks1.hdrrecorder.camera.camera2.CameraRequestOverrides
import com.pilerks1.hdrrecorder.camera.camera2.FixedFrameRates
import com.pilerks1.hdrrecorder.camera.model.CameraCapabilities
import com.pilerks1.hdrrecorder.camera.model.CameraControlValues
import com.pilerks1.hdrrecorder.camera.model.ExposureMode

internal fun matchesExposure(
    frame: CameraFrameMetadata,
    overrides: CameraRequestOverrides,
    mode: ExposureMode,
    locked: Boolean = false
): Boolean {
    val aeMode = frame[CaptureResult.CONTROL_AE_MODE] ?: return false
    val ev = overrides[CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION]
    if (ev != null && frame.requested(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION) != ev) return false
    if (locked) {
        if (frame.requested(CaptureRequest.CONTROL_AE_LOCK) != true ||
            frame[CaptureResult.CONTROL_AE_LOCK] != true ||
            frame[CaptureResult.CONTROL_AE_STATE] != CameraMetadata.CONTROL_AE_STATE_LOCKED ||
            (ev != null && frame[CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION] != ev)
        ) return false
    } else if (frame.requested(CaptureRequest.CONTROL_AE_LOCK) == true ||
        frame[CaptureResult.CONTROL_AE_LOCK] == true ||
        frame[CaptureResult.CONTROL_AE_STATE] == CameraMetadata.CONTROL_AE_STATE_LOCKED
    ) return false
    if (mode == ExposureMode.AUTO) {
        return aeMode != CameraMetadata.CONTROL_AE_MODE_OFF &&
            frame.requested(CaptureRequest.CONTROL_AE_MODE) != CameraMetadata.CONTROL_AE_MODE_OFF &&
            (Build.VERSION.SDK_INT < 36 || listOf(
                frame.requested(CaptureRequest.CONTROL_AE_PRIORITY_MODE),
                frame[CaptureResult.CONTROL_AE_PRIORITY_MODE]
            ).all {
                it == null || it == CameraMetadata.CONTROL_AE_PRIORITY_MODE_OFF
            })
    }
    return frame.requested(CaptureRequest.CONTROL_AE_MODE) == overrides[CaptureRequest.CONTROL_AE_MODE] &&
        aeMode == overrides[CaptureRequest.CONTROL_AE_MODE] &&
        listOf(CaptureRequest.SENSOR_SENSITIVITY, CaptureRequest.SENSOR_EXPOSURE_TIME,
            CaptureRequest.SENSOR_FRAME_DURATION, CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST).all { key ->
            overrides[key]?.let { frame.requested(key) == it } ?: true
        } && (Build.VERSION.SDK_INT < 36 || frame.requested(CaptureRequest.CONTROL_AE_PRIORITY_MODE).let { actual ->
            val expected = overrides[CaptureRequest.CONTROL_AE_PRIORITY_MODE]
            if (expected == null) actual == null || actual == CameraMetadata.CONTROL_AE_PRIORITY_MODE_OFF
            else actual == expected && frame[CaptureResult.CONTROL_AE_PRIORITY_MODE] == expected
        })
}

internal fun CameraControlValues.withExposure(source: CameraControlValues): CameraControlValues = copy(
    isManualIso = source.isManualIso,
    isoValue = source.isoValue,
    isManualSs = source.isManualSs,
    ssValueNanos = source.ssValueNanos,
    isManualPostRawSensitivity = source.isManualPostRawSensitivity,
    postRawSensitivityBoost = source.postRawSensitivityBoost,
    isManualEv = source.isManualEv,
    evValueIndex = source.evValueIndex,
    isManualFps = source.isManualFps,
    fpsRange = source.fpsRange
)

internal fun manualFps(controls: CameraControlValues, caps: CameraCapabilities, selectedFps: Int): Range<Int> {
    val requested = controls.fpsRange?.upper ?: selectedFps
    val fps = controls.ssValueNanos?.let { FixedFrameRates.nearestFixedFrameRateForShutter(it, caps.fpsRanges) }
        ?: FixedFrameRates.nearestFixedFrameRate(requested, caps.fpsRanges)
        ?: requested
    return Range(fps, fps)
}
