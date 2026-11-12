package com.pilerks1.hdrrecorder.camera.camerax

import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import com.pilerks1.hdrrecorder.camera.camera2.CameraFrameMetadata
import com.pilerks1.hdrrecorder.camera.camera2.CameraRequestOverrides
import com.pilerks1.hdrrecorder.camera.camera2.CameraResultReader
import com.pilerks1.hdrrecorder.camera.model.CameraCapabilities
import com.pilerks1.hdrrecorder.camera.model.CameraControlValues
import com.pilerks1.hdrrecorder.camera.model.ExposureMode
import com.pilerks1.hdrrecorder.camera.model.LowLightBoostPolicy

/** Ordered request/result handoff, invoked under the coordinator's existing lock and timeout. */
internal suspend fun performExposureHandoff(
    previous: CameraControlValues,
    target: DesiredCameraState,
    caps: CameraCapabilities,
    oldMode: ExposureMode,
    newMode: ExposureMode,
    waitToLockAe: Boolean,
    attachment: CameraXRequestBackend,
    results: CameraResultReader,
    checkCurrent: () -> Unit,
    snapshot: (DesiredCameraState, Boolean?) -> CameraRequestOverrides,
    submit: suspend (DesiredCameraState, Boolean?) -> Unit,
    awaitFrame: suspend (Long, (CameraFrameMetadata) -> Boolean) -> CameraFrameMetadata,
    disableLowLightBoost: suspend () -> Unit
): CameraControlValues {
    checkCurrent()
    // Reset EV only in the same snapshot that disables AE, never before locking.
    var resolved = if (newMode == ExposureMode.FULL_MANUAL) {
        target.controls.copy(isManualEv = false, evValueIndex = 0)
    } else target.controls
    if (newMode != ExposureMode.AUTO) {
        if (LowLightBoostPolicy.isSupported(caps.supportsLowLightBoost)) {
            disableLowLightBoost()
            checkCurrent()
        }
        val held = if (waitToLockAe && oldMode != ExposureMode.FULL_MANUAL && caps.supportsAeLock) {
            val source = target.copy(controls = previous.copy(isLowLightBoostEnabled = false))
            val lockedOverrides = snapshot(source, true)
            val before = results.latest.value?.frameNumber ?: -1L
            submit(source, true)
            awaitFrame(before) {
                matchesExposure(it, lockedOverrides, oldMode, locked = true)
            }
        } else {
            results.latest.value?.takeIf { it.generation == attachment.generation }
                ?: error("No current exposure result available")
        }
        checkCurrent()
        val heldIso = held[CaptureResult.SENSOR_SENSITIVITY]
            ?: error("No reported sensor sensitivity")
        val heldShutter = held[CaptureResult.SENSOR_EXPOSURE_TIME]
            ?: error("No reported exposure time")
        val heldBoost = held[CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST]
            // Forced Post RAW control may have no reported gain; use unity.
            ?: if (caps.supportsPostRawSensitivityBoost) {
                previous.postRawSensitivityBoost ?: 100
            } else null
        val handoff = resolved.copy(
            isoValue = if (resolved.isManualIso) heldIso else null,
            ssValueNanos = if (resolved.isManualSs) heldShutter else null,
            isManualPostRawSensitivity = newMode == ExposureMode.FULL_MANUAL && caps.supportsPostRawSensitivityBoost,
            postRawSensitivityBoost = if (newMode == ExposureMode.FULL_MANUAL) heldBoost else null
        )
        if (waitToLockAe) {
            val heldDuration = held[CaptureResult.SENSOR_FRAME_DURATION]
                ?: error("No reported frame duration")
            // Establish continuity before applying an explicit user target.
            val handoffOverrides = snapshot(target.copy(controls = handoff), false)
            val overrides = if (newMode == ExposureMode.FULL_MANUAL) {
                handoffOverrides.plus(CaptureRequest.SENSOR_FRAME_DURATION, maxOf(heldDuration, heldShutter))
            } else handoffOverrides
            val before = results.latest.value?.frameNumber ?: -1L
            attachment.apply(overrides)
            awaitFrame(before) { frame -> matchesExposure(frame, overrides, newMode) }
            checkCurrent()
        }
        // Automatic slider values are observations, not explicit new user targets.
        // Immediate transitions preserve controls that were already manual.
        resolved = resolved.copy(
            isoValue = if (resolved.isManualIso && resolved.isoValue == previous.isoValue &&
                (waitToLockAe || !previous.isManualIso)
            ) heldIso else resolved.isoValue,
            ssValueNanos = if (resolved.isManualSs && resolved.ssValueNanos == previous.ssValueNanos &&
                (waitToLockAe || !previous.isManualSs)
            ) heldShutter else resolved.ssValueNanos,
            isManualPostRawSensitivity = handoff.isManualPostRawSensitivity,
            postRawSensitivityBoost = if (handoff.isManualPostRawSensitivity) {
                resolved.postRawSensitivityBoost?.takeUnless {
                    it == previous.postRawSensitivityBoost &&
                        (waitToLockAe || !previous.isManualPostRawSensitivity)
                } ?: heldBoost
            } else null
        )
        if (newMode == ExposureMode.FULL_MANUAL && resolved.isManualFps) {
            resolved = resolved.copy(fpsRange = manualFps(resolved, caps, target.selectedFps))
        }
    }
    val finalState = target.copy(controls = resolved)
    val finalOverrides = snapshot(finalState, null)
    val before = results.latest.value?.frameNumber ?: -1L
    submit(finalState, null)
    awaitFrame(before) { matchesExposure(it, finalOverrides, newMode) }
    checkCurrent()
    return resolved
}
