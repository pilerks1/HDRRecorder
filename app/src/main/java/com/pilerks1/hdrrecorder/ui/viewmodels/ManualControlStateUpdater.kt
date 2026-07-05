package com.pilerks1.hdrrecorder.ui.viewmodels

import com.pilerks1.hdrrecorder.data.camera.CameraCapabilities
import com.pilerks1.hdrrecorder.ui.ManualControlsState

/**
 * Pure function to calculate the next ManualControlsState.
 * Evaluates hardware capabilities and enforces AE fallbacks natively.
 */
object ManualControlStateUpdater {
    fun calculateNextState(
        oldState: ManualControlsState,
        caps: CameraCapabilities?,
        updater: (ManualControlsState) -> ManualControlsState
    ): Pair<ManualControlsState, Boolean> {
        var newState = updater(oldState)
        var triggersRebind = false
        
        // Track Hybrid AE priority based on what was just modified
        if (newState.isManualIso != oldState.isManualIso || newState.isoValue != oldState.isoValue) {
            newState = newState.copy(lastManualExposureInput = "ISO")
        } else if (newState.isManualSs != oldState.isManualSs || newState.ssValueNanos != oldState.ssValueNanos) {
            newState = newState.copy(lastManualExposureInput = "SS")
        }
        
        // If device lacks Hybrid AE, manual ISO or SS forces AE off, which locks exposure controls.
        if (caps?.hasHybridAe == false) {
            // If either ISO or SS changed from auto to manual, force both to manual
            if ((newState.isManualIso && !oldState.isManualIso) ||
                (newState.isManualSs && !oldState.isManualSs)) {
                newState = newState.copy(isManualIso = true, isManualSs = true)
            }
            // If either ISO or SS changed from manual to auto, force both to auto
            else if ((!newState.isManualIso && oldState.isManualIso) ||
                     (!newState.isManualSs && oldState.isManualSs)) {
                newState = newState.copy(isManualIso = false, isManualSs = false)
            }
        }
        
        // Validate specific capability support and override if unsupported
        if (caps?.supportsNightMode == false && newState.isNightModeAeEnabled) {
            newState = newState.copy(isNightModeAeEnabled = false)
        }
        if (caps?.supportsCCT == false && (newState.isManualWb || newState.wbTemp != null || newState.wbTint != null)) {
            newState = newState.copy(isManualWb = false, wbTemp = null, wbTint = null)
        }
        
        if (newState.isManualFps != oldState.isManualFps) {
            triggersRebind = true
        }
        
        return Pair(newState, triggersRebind)
    }
}
