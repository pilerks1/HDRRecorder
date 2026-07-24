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
        val shutterNanos: Long?
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
            newState = resolveHybridOnlyConflict(oldState, newState, caps)

            // A full-manual device may pair ISO and shutter when the selected individual
            // control has no Hybrid AE priority mode.
            if (caps.supportsFullManualExposure) {
                if (returnsToAutoFromUnsupportedHybrid(oldState, newState, caps)) {
                    newState = newState.copy(isManualIso = false, isManualSs = false)
                } else if (requiresFullManualExposure(newState, caps)) {
                    newState = newState.copy(
                        isManualIso = true,
                        isManualSs = true,
                        isoValue = newState.isoValue
                            ?: exposureDefaults.isoValue
                            ?: caps.isoRange?.lower,
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

        newState = clearAutomaticValues(newState, caps)
        
        return Pair(newState, triggersRebind)
    }

    fun sanitizeForCapabilities(
        state: ManualControlsState,
        caps: CameraCapabilities
    ): ManualControlsState {
        var sanitized = clearUnsupportedControls(state, caps)
        if (!caps.supportsFullManualExposure && sanitized.isManualIso && sanitized.isManualSs) {
            sanitized = sanitized.copy(isManualIso = false, isManualSs = false)
        }
        return clearAutomaticValues(sanitized, caps)
    }

    private fun clearUnsupportedControls(
        state: ManualControlsState,
        caps: CameraCapabilities
    ): ManualControlsState = state.copy(
        activePanel = state.activePanel?.takeIf { panelIsAvailable(it, caps) },
        isManualIso = state.isManualIso && caps.hasManualIsoControl,
        isManualSs = state.isManualSs && caps.hasManualShutterControl,
        isManualEv = state.isManualEv && caps.hasExposureCompensationControl,
        evValueIndex = if (caps.hasExposureCompensationControl) state.evValueIndex else 0,
        isNightModeAeEnabled = state.isNightModeAeEnabled && caps.supportsNightMode,
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
            enabledIso && !enabledShutter -> newState.copy(isManualSs = false, ssValueNanos = null)
            enabledShutter && !enabledIso -> newState.copy(isManualIso = false, isoValue = null)
            else -> newState.copy(
                isManualIso = false,
                isoValue = null,
                isManualSs = false,
                ssValueNanos = null
            )
        }
    }

    // Automatic modes own their effective values through camera telemetry. Do not retain a
    // previous manual setting here: it would otherwise be mistaken for a live auto value.
    private fun clearAutomaticValues(
        state: ManualControlsState,
        caps: CameraCapabilities?
    ): ManualControlsState = state.copy(
        isoValue = state.isoValue
            ?.let { value ->
                caps?.isoRange?.let { range -> value.coerceIn(range.lower, range.upper) } ?: value
            }
            .takeIf { state.isManualIso },
        ssValueNanos = state.ssValueNanos
            ?.let { value ->
                caps?.ssRangeNanos?.let { range -> value.coerceIn(range.lower, range.upper) } ?: value
            }
            .takeIf { state.isManualSs },
        evValueIndex = state.evValueIndex
            .let { value ->
                caps?.evRange?.let { range -> value.coerceIn(range.lower, range.upper) } ?: value
            }
            .takeIf { state.isManualEv } ?: 0,
        focusDistanceDiopters = state.focusDistanceDiopters
            ?.coerceIn(0f, caps?.focusMinDistanceDiopters ?: Float.MAX_VALUE)
            .takeIf { state.isManualFocus },
        wbTemp = state.wbTemp
            ?.let { value ->
                caps?.cctTemperatureRange?.let { range ->
                    value.coerceIn(range.lower, range.upper)
                } ?: value
            }
            .takeIf { state.isManualWb },
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
        CameraControlPanel.EXPOSURE_COMPENSATION -> caps.hasExposureCompensationControl
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

    private fun returnsToAutoFromUnsupportedHybrid(
        oldState: ManualControlsState,
        newState: ManualControlsState,
        caps: CameraCapabilities
    ): Boolean {
        val turnedOffIso = oldState.isManualIso && !newState.isManualIso
        val turnedOffShutter = oldState.isManualSs && !newState.isManualSs
        return (turnedOffIso || turnedOffShutter) && requiresFullManualExposure(newState, caps)
    }
}
