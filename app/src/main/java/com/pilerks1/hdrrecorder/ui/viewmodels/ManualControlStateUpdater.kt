package com.pilerks1.hdrrecorder.ui.viewmodels

import com.pilerks1.hdrrecorder.data.camera.CameraCapabilities
import com.pilerks1.hdrrecorder.ui.CameraControlPanel
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
        updater: (ManualControlsState) -> ManualControlsState
    ): Pair<ManualControlsState, Boolean> {
        var newState = updater(oldState)
        var triggersRebind = false

        if (caps != null) {
            newState = clearUnsupportedControls(newState, caps)
            newState = resolvePostRawExposureOwnership(oldState, newState, caps)
            newState = resolveHybridOnlyConflict(oldState, newState, caps)
            newState = resolveLowLightBoostConflict(oldState, newState)

            // A full-manual device may pair ISO and shutter when the selected individual
            // control has no Hybrid AE priority mode.
            if (caps.supportsFullManualExposure) {
                if (returnsToAutoFromUnsupportedHybrid(oldState, newState, caps)) {
                    newState = newState.copy(isManualIso = false, isManualSs = false)
                } else if (requiresFullManualExposure(newState, caps)) {
                    newState = newState.copy(
                        isManualIso = true,
                        isManualSs = true,
                        isManualPostRawSensitivity =
                            caps.supportsPostRawSensitivityBoost,
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
            }

            if (caps.supportsCCT && newState.isManualWb) {
                val temperatureRange = caps.cctTemperatureRange
                newState = newState.copy(
                    wbTemp = newState.wbTemp ?: temperatureRange?.let { (it.lower + it.upper) / 2 },
                    wbTint = newState.wbTint ?: 0
                )
            }
        }
        
        if (newState.isManualFps != oldState.isManualFps) {
            triggersRebind = true
        }

        newState = clearInactiveValues(newState)
        
        return Pair(newState, triggersRebind)
    }

    fun adaptToCapabilities(
        state: ManualControlsState,
        caps: CameraCapabilities
    ): ManualControlsState {
        var adapted = clearUnsupportedControls(state, caps)
        if (!caps.supportsFullManualExposure && adapted.isManualIso && adapted.isManualSs) {
            adapted = adapted.copy(isManualIso = false, isManualSs = false)
        }
        return clearInactiveValues(adapted)
    }

    private fun clearUnsupportedControls(
        state: ManualControlsState,
        caps: CameraCapabilities
    ): ManualControlsState = state.copy(
        activePanel = state.activePanel?.takeIf { panelIsAvailable(it, caps) },
        isManualIso = state.isManualIso && caps.hasManualIsoControl,
        isManualPostRawSensitivity = state.isManualPostRawSensitivity &&
            caps.supportsPostRawSensitivityBoost,
        postRawSensitivityBoost = state.postRawSensitivityBoost.takeIf {
            caps.supportsPostRawSensitivityBoost
        },
        isManualSs = state.isManualSs && caps.hasManualShutterControl,
        isManualEv = state.isManualEv && caps.hasExposureCompensationControl,
        evValueIndex = if (caps.hasExposureCompensationControl) state.evValueIndex else 0,
        isLowLightBoostEnabled = state.isLowLightBoostEnabled && caps.supportsLowLightBoost,
        isManualFocus = state.isManualFocus && caps.hasManualFocusControl,
        isManualWb = state.isManualWb && caps.supportsCCT,
        isManualFps = state.isManualFps && caps.fpsRanges.isNotEmpty()
    )

    private fun resolveLowLightBoostConflict(
        oldState: ManualControlsState,
        newState: ManualControlsState
    ): ManualControlsState {
        if (!oldState.isLowLightBoostEnabled || !newState.isLowLightBoostEnabled) return newState

        val enabledManualExposure =
            (!oldState.isManualIso && newState.isManualIso) ||
                (!oldState.isManualSs && newState.isManualSs) ||
                (!oldState.isManualPostRawSensitivity &&
                    newState.isManualPostRawSensitivity)
        val selectedHighFrameRate = newState.isManualFps &&
            (newState.fpsRange?.upper ?: 0) > LOW_LIGHT_BOOST_MAX_FPS
        return if (enabledManualExposure || selectedHighFrameRate) {
            newState.copy(isLowLightBoostEnabled = false)
        } else {
            newState
        }
    }

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
            else -> newState.copy(
                isManualIso = false,
                isoValue = null,
                isManualPostRawSensitivity = false,
                postRawSensitivityBoost = null,
                isManualSs = false,
                ssValueNanos = null
            )
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
        evValueIndex = state.evValueIndex.takeIf { state.isManualEv } ?: 0,
        focusDistanceDiopters = state.focusDistanceDiopters.takeIf { state.isManualFocus },
        wbTemp = state.wbTemp.takeIf { state.isManualWb },
        wbTint = state.wbTint.takeIf { state.isManualWb },
        fpsRange = state.fpsRange.takeIf { state.isManualFps }
    )

    private fun panelIsAvailable(
        panel: CameraControlPanel,
        caps: CameraCapabilities
    ): Boolean = when (panel) {
        CameraControlPanel.RESOLUTION -> true
        CameraControlPanel.ISO -> caps.hasManualIsoControl
        CameraControlPanel.SHUTTER -> caps.hasManualShutterControl
        CameraControlPanel.EXPOSURE_COMPENSATION ->
            caps.hasExposureCompensationControl || caps.supportsLowLightBoost
        CameraControlPanel.FOCUS -> caps.hasManualFocusControl
        CameraControlPanel.WHITE_BALANCE,
        CameraControlPanel.TINT -> caps.supportsCCT
    }

    private fun requiresFullManualExposure(
        state: ManualControlsState,
        caps: CameraCapabilities
    ): Boolean = when {
        !state.isManualIso && !state.isManualSs -> false
        state.isManualIso && state.isManualSs -> true
        state.isManualIso -> !caps.supportsIsoPriorityAe
        else -> !caps.supportsShutterPriorityAe
    }

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
            return newState.copy(
                isManualIso = false,
                isoValue = null,
                isManualPostRawSensitivity = false,
                postRawSensitivityBoost = null,
                isManualSs = false,
                ssValueNanos = null
            )
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
        return (turnedOffIso || turnedOffShutter) && requiresFullManualExposure(newState, caps)
    }

    private const val LOW_LIGHT_BOOST_MAX_FPS = 30
}
