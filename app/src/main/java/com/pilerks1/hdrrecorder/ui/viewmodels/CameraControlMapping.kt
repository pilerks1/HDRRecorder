package com.pilerks1.hdrrecorder.ui.viewmodels

import com.pilerks1.hdrrecorder.data.camera.core.CameraControlValues
import com.pilerks1.hdrrecorder.ui.ManualControlsState

internal fun ManualControlsState.toCameraValues(): CameraControlValues = CameraControlValues(
    isManualIso = isManualIso,
    isoValue = isoValue,
    isManualPostRawSensitivity = isManualPostRawSensitivity,
    postRawSensitivityBoost = postRawSensitivityBoost,
    isManualSs = isManualSs,
    ssValueNanos = ssValueNanos,
    isManualEv = isManualEv,
    evValueIndex = evValueIndex,
    isLowLightBoostEnabled = isLowLightBoostEnabled,
    isManualFocus = isManualFocus,
    focusDistanceDiopters = focusDistanceDiopters,
    isManualWb = isManualWb,
    wbTemp = wbTemp,
    wbTint = wbTint,
    isManualFps = isManualFps,
    fpsRange = fpsRange
)

internal fun ManualControlsState.withCameraValues(values: CameraControlValues): ManualControlsState = copy(
    isManualIso = values.isManualIso,
    isoValue = values.isoValue,
    isManualPostRawSensitivity = values.isManualPostRawSensitivity,
    postRawSensitivityBoost = values.postRawSensitivityBoost,
    isManualSs = values.isManualSs,
    ssValueNanos = values.ssValueNanos,
    isManualEv = values.isManualEv,
    evValueIndex = values.evValueIndex,
    isLowLightBoostEnabled = values.isLowLightBoostEnabled,
    isManualFocus = values.isManualFocus,
    focusDistanceDiopters = values.focusDistanceDiopters,
    isManualWb = values.isManualWb,
    wbTemp = values.wbTemp,
    wbTint = values.wbTint,
    isManualFps = values.isManualFps,
    fpsRange = values.fpsRange
)
