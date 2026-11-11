package com.pilerks1.hdrrecorder.data.camera.core

import android.os.Build
import android.util.Range

/** App-wide, documented AE boost restrictions; session/device acceptance remains CameraX's job. */
object LowLightBoostPolicy {
    const val MAX_FPS = 30

    fun isSupported(cameraXSupported: Boolean): Boolean =
        Build.VERSION.SDK_INT >= 35 && cameraXSupported

    fun isFpsCompatible(upperFps: Int): Boolean = upperFps <= MAX_FPS

    fun isEligible(
        controls: CameraControlValues,
        selectedFps: Int,
        previewFpsRange: Range<Int>? = null
    ): Boolean = !controls.isManualIso && !controls.isManualSs && !controls.isManualPostRawSensitivity &&
        isFpsCompatible(if (controls.isManualFps) controls.fpsRange?.upper ?: selectedFps else selectedFps) &&
        (previewFpsRange == null || isFpsCompatible(previewFpsRange.upper))

    fun normalize(
        controls: CameraControlValues,
        supported: Boolean,
        selectedFps: Int,
        previewFpsRange: Range<Int>? = null
    ): CameraControlValues = if (controls.isLowLightBoostEnabled &&
        (!isSupported(supported) || !isEligible(controls, selectedFps, previewFpsRange))
    ) controls.copy(isLowLightBoostEnabled = false) else controls
}

enum class LowLightBoostRuntimeState { OFF, INACTIVE, ACTIVE }

data class LowLightBoostObservation(
    val generation: Long? = null,
    val state: LowLightBoostRuntimeState = LowLightBoostRuntimeState.OFF
)

enum class LowLightBoostRequestStatus { IDLE, PENDING, SUCCEEDED, FAILED }

data class LowLightBoostRequestState(
    val generation: Long? = null,
    val revision: Long = 0,
    val enabled: Boolean = false,
    val status: LowLightBoostRequestStatus = LowLightBoostRequestStatus.IDLE
)
