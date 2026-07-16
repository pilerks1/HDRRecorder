package com.pilerks1.hdrrecorder.ui.viewmodels

import com.pilerks1.hdrrecorder.data.camera.CameraCapabilities
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
        
        // Each Android 16 Hybrid AE priority mode is independently optional. A lone manual
        // control may stay hybrid only when the matching priority mode is advertised; otherwise
        // force both sensor controls manual so the request remains complete and predictable.
        if (caps != null) {
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
        
        // Validate specific capability support and override if unsupported
        if (caps?.supportsNightMode == false && newState.isNightModeAeEnabled) {
            newState = newState.copy(isNightModeAeEnabled = false)
        }
        if (caps?.supportsCCT == false && (newState.isManualWb || newState.wbTemp != null || newState.wbTint != null)) {
            newState = newState.copy(isManualWb = false, wbTemp = null, wbTint = null)
        } else if (caps?.supportsCCT == true && newState.isManualWb) {
            val temperatureRange = caps.cctTemperatureRange
            newState = newState.copy(
                wbTemp = newState.wbTemp ?: temperatureRange?.let { (it.lower + it.upper) / 2 },
                wbTint = newState.wbTint ?: 0
            )
        }
        
        if (newState.isManualFps != oldState.isManualFps) {
            triggersRebind = true
        }
        
        return Pair(newState, triggersRebind)
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
