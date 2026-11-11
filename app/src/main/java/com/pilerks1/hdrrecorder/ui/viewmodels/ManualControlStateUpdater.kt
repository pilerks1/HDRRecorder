package com.pilerks1.hdrrecorder.ui.viewmodels

import com.pilerks1.hdrrecorder.data.camera.CameraCapabilities
import android.util.Range
import com.pilerks1.hdrrecorder.data.camera.core.LowLightBoostPolicy
import com.pilerks1.hdrrecorder.data.camera.core.ExposureMode
import com.pilerks1.hdrrecorder.ui.ManualControlsState

/**
 * Pure function to calculate the next ManualControlsState.
 * Evaluates hardware capabilities and enforces AE fallbacks natively.
 */
object ManualControlStateUpdater {
    data class ExposureDefaults(
        val isoValue: Int?,
        val shutterNanos: Long?,
        val postRawSensitivityBoost: Int? = null
    )

    fun calculateNextState(
        oldState: ManualControlsState,
        caps: CameraCapabilities?,
        exposureDefaults: ExposureDefaults,
        selectedFps: Int,
        previewFpsRange: Range<Int>?,
        updater: (ManualControlsState) -> ManualControlsState
    ): Pair<ManualControlsState, Boolean> {
        var newState = updater(oldState)

        if (caps != null) {
            newState = clearUnsupportedControls(newState, caps)
            newState = resolvePostRawExposureOwnership(oldState, newState, caps)
            newState = resolveHybridOnlyConflict(oldState, newState, caps)

            // A full-manual device may pair ISO and shutter when the selected individual
            // control has no Hybrid AE priority mode.
            if (caps.supportsFullManualExposure && returnsToAutoFromUnsupportedHybrid(oldState, newState, caps)) {
                newState = newState.copy(isManualIso = false, isManualSs = false)
            }
            newState = completeRequiredValues(newState, caps, exposureDefaults)
        }

        val triggersRebind = newState.isManualFps != oldState.isManualFps
        return Pair(LowLightBoostPolicy.normalize(
            clearInactiveValues(newState), caps?.supportsLowLightBoost == true, selectedFps, previewFpsRange
        ), triggersRebind)
    }

    /**
     * Normalizes a complete state (such as a loaded preset) against capabilities, without a
     * previous state to diff against. Low-light boost is dropped when manual exposure or an
     * effective frame rate above [LowLightBoostPolicy.MAX_FPS] makes it unavailable.
     */
    fun normalize(
        state: ManualControlsState,
        caps: CameraCapabilities,
        selectedFps: Int,
        previewFpsRange: Range<Int>? = null
    ): ManualControlsState {
        var normalized = clearUnsupportedControls(state, caps)
        if (!caps.supportsFullManualExposure && normalized.isManualIso && normalized.isManualSs) {
            normalized = normalized.withAutoExposure()
        }
        // Post-RAW boost is owned by full manual exposure, in both directions.
        val fullManual = normalized.isManualPostRawSensitivity || (normalized.isManualIso && normalized.isManualSs)
        normalized = if (caps.supportsPostRawSensitivityBoost && fullManual) {
            normalized.copy(isManualIso = true, isManualSs = true, isManualPostRawSensitivity = true)
        } else {
            normalized.copy(isManualPostRawSensitivity = false, postRawSensitivityBoost = null)
        }
        normalized = clearInactiveValues(
            completeRequiredValues(normalized, caps, ExposureDefaults(null, null, postRawSensitivityBoost = 100))
        )
        return LowLightBoostPolicy.normalize(normalized, caps.supportsLowLightBoost, selectedFps, previewFpsRange)
    }

    /** Fills values required by full manual exposure and by manual white balance. */
    private fun completeRequiredValues(
        state: ManualControlsState,
        caps: CameraCapabilities,
        exposureDefaults: ExposureDefaults
    ): ManualControlsState {
        var newState = state
        if (ExposureMode.resolve(newState, caps) == ExposureMode.FULL_MANUAL) {
            newState = newState.copy(
                isManualIso = true,
                isManualSs = true,
                isManualPostRawSensitivity = caps.supportsPostRawSensitivityBoost,
                isoValue = newState.isoValue
                    ?: exposureDefaults.isoValue
                    ?: caps.isoRange?.lower,
                postRawSensitivityBoost = newState.postRawSensitivityBoost
                    ?: exposureDefaults.postRawSensitivityBoost,
                ssValueNanos = newState.ssValueNanos
                    ?: exposureDefaults.shutterNanos
                    ?: caps.ssRangeNanos?.lower
            )
        }
        if (caps.supportsCCT && newState.isManualWb) {
            val temperatureRange = caps.cctTemperatureRange
            newState = newState.copy(
                wbTemp = newState.wbTemp ?: temperatureRange?.let { (it.lower + it.upper) / 2 },
                wbTint = newState.wbTint ?: 0
            )
        }
        return newState
    }

    private fun clearUnsupportedControls(
        state: ManualControlsState,
        caps: CameraCapabilities
    ): ManualControlsState = state.copy(
        isManualIso = state.isManualIso && caps.hasManualIsoControl,
        isManualPostRawSensitivity = state.isManualPostRawSensitivity &&
            caps.supportsPostRawSensitivityBoost,
        postRawSensitivityBoost = state.postRawSensitivityBoost.takeIf {
            caps.supportsPostRawSensitivityBoost
        },
        isManualSs = state.isManualSs && caps.hasManualShutterControl,
        isManualEv = state.isManualEv && caps.hasExposureCompensationControl,
        evValueIndex = if (caps.hasExposureCompensationControl) state.evValueIndex else 0,
        isManualFocus = state.isManualFocus && caps.hasManualFocusControl,
        isManualWb = state.isManualWb && caps.supportsCCT,
        isManualFps = state.isManualFps && caps.fpsRanges.isNotEmpty()
    )

    private fun resolveHybridOnlyConflict(
        oldState: ManualControlsState,
        newState: ManualControlsState,
        caps: CameraCapabilities
    ): ManualControlsState {
        if (caps.supportsFullManualExposure || !newState.isManualIso || !newState.isManualSs) {
            return newState
        }

        val enabledIso = !oldState.isManualIso && newState.isManualIso
        val enabledShutter = !oldState.isManualSs && newState.isManualSs
        return when {
            enabledIso && !enabledShutter -> newState.copy(
                isManualSs = false,
                ssValueNanos = null,
                isManualPostRawSensitivity = false,
                postRawSensitivityBoost = null
            )
            enabledShutter && !enabledIso -> newState.copy(
                isManualIso = false,
                isoValue = null,
                isManualPostRawSensitivity = false,
                postRawSensitivityBoost = null
            )
            else -> newState.withAutoExposure()
        }
    }

    // Sliders constrain user edits themselves. Values captured from camera telemetry must remain
    // verbatim so switching from auto to manual is a true hold, even when the HAL reports a value
    // outside its advertised manual range. Only discard values for controls that remain automatic.
    private fun clearInactiveValues(
        state: ManualControlsState
    ): ManualControlsState = state.copy(
        isoValue = state.isoValue.takeIf { state.isManualIso },
        isManualPostRawSensitivity = state.isManualPostRawSensitivity &&
            state.isManualIso &&
            state.isManualSs,
        postRawSensitivityBoost = state.postRawSensitivityBoost.takeIf {
            state.isManualPostRawSensitivity && state.isManualIso && state.isManualSs
        },
        ssValueNanos = state.ssValueNanos.takeIf { state.isManualSs },
        isManualEv = state.isManualEv && state.evValueIndex != 0,
        evValueIndex = state.evValueIndex.takeIf { state.isManualEv } ?: 0,
        focusDistanceDiopters = state.focusDistanceDiopters.takeIf { state.isManualFocus },
        wbTemp = state.wbTemp.takeIf { state.isManualWb },
        wbTint = state.wbTint.takeIf { state.isManualWb },
        fpsRange = state.fpsRange.takeIf { state.isManualFps }
    )

    private fun resolvePostRawExposureOwnership(
        oldState: ManualControlsState,
        newState: ManualControlsState,
        caps: CameraCapabilities
    ): ManualControlsState {
        if (!caps.supportsPostRawSensitivityBoost) {
            return newState.copy(
                isManualPostRawSensitivity = false,
                postRawSensitivityBoost = null
            )
        }

        val leftFullManualThroughSensorControl =
            (oldState.isManualIso && !newState.isManualIso) ||
                (oldState.isManualSs && !newState.isManualSs)
        if (leftFullManualThroughSensorControl) {
            return newState.copy(
                isManualPostRawSensitivity = false,
                postRawSensitivityBoost = null
            )
        }

        val returnedPostRawToAuto =
            oldState.isManualPostRawSensitivity && !newState.isManualPostRawSensitivity
        if (returnedPostRawToAuto) {
            return newState.withAutoExposure()
        }

        if (newState.isManualPostRawSensitivity) {
            return newState.copy(
                isManualIso = true,
                isManualSs = true
            )
        }

        val remainsFullManual = newState.isManualIso && newState.isManualSs
        return if (remainsFullManual) {
            newState.copy(isManualPostRawSensitivity = true)
        } else {
            newState.copy(
                isManualPostRawSensitivity = false,
                postRawSensitivityBoost = null
            )
        }
    }

    private fun returnsToAutoFromUnsupportedHybrid(
        oldState: ManualControlsState,
        newState: ManualControlsState,
        caps: CameraCapabilities
    ): Boolean {
        val turnedOffIso = oldState.isManualIso && !newState.isManualIso
        val turnedOffShutter = oldState.isManualSs && !newState.isManualSs
        return (turnedOffIso || turnedOffShutter) && ExposureMode.resolve(newState, caps) == ExposureMode.FULL_MANUAL
    }
}
