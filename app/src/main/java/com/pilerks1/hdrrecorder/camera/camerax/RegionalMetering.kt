package com.pilerks1.hdrrecorder.camera.camerax

import android.hardware.camera2.CaptureResult
import androidx.camera.core.FocusMeteringAction
import com.pilerks1.hdrrecorder.camera.camera2.CameraFrameMetadata
import com.pilerks1.hdrrecorder.camera.model.CameraCapabilities
import com.pilerks1.hdrrecorder.camera.model.CameraControlValues

internal fun meteringFlags(controls: CameraControlValues, caps: CameraCapabilities?, afMode: Int?): Int {
    caps ?: return 0
    var flags = 0
    // Without an override, retain CameraX's automatic regional AF when supported.
    val afModes = afMode?.let { listOf(it) } ?: caps.afModes
    if (!controls.isManualFocus && caps.maxAfRegions > 0 && afModes.any {
            it == CaptureResult.CONTROL_AF_MODE_CONTINUOUS_VIDEO ||
                it == CaptureResult.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        }) flags = flags or FocusMeteringAction.FLAG_AF
    if (!controls.isManualWb && caps.maxAwbRegions > 0) flags = flags or FocusMeteringAction.FLAG_AWB
    if (!(controls.isManualIso && controls.isManualSs) && caps.maxAeRegions > 0) {
        flags = flags or FocusMeteringAction.FLAG_AE
    }
    return flags
}

/** Only post-application frames participate; absent state reporting never becomes a timeout. */
internal class TemporaryMeteringMonitor(private val flags: Int, private val afterFrame: Long) {
    private class Component(
        val flag: Int,
        val key: CaptureResult.Key<Int>,
        val stableStates: Set<Int>,
        val searchingState: Int
    )

    // Per-component timestamps, indexed like COMPONENTS; NONE means not currently in that state.
    private val stableSince = LongArray(COMPONENTS.size) { NONE }
    private val searchingSince = LongArray(COMPONENTS.size) { NONE }
    private var armed = false

    fun shouldCancel(frame: CameraFrameMetadata): Boolean {
        if (frame.frameNumber <= afterFrame) return false
        val now = frame.receivedAtElapsedNanos
        var anyReported = false
        var allStable = true
        var cancel = false
        COMPONENTS.forEachIndexed { index, component ->
            val state = if (flags and component.flag != 0) frame[component.key] else null
            if (state == null) {
                stableSince[index] = NONE
                searchingSince[index] = NONE
                return@forEachIndexed
            }
            anyReported = true
            if (!armed) {
                if (state in component.stableStates) {
                    if (stableSince[index] == NONE) stableSince[index] = now
                } else stableSince[index] = NONE
                // A component with no reporting does not prevent other components from arming.
                allStable = allStable && stableSince[index] != NONE && now - stableSince[index] >= INTERVAL_NANOS
            } else if (state == component.searchingState) {
                if (searchingSince[index] == NONE) searchingSince[index] = now
                cancel = cancel || now - searchingSince[index] >= INTERVAL_NANOS
            } else searchingSince[index] = NONE
        }
        if (!anyReported) return false
        if (!armed) {
            armed = allStable
            return false
        }
        return cancel
    }

    private companion object {
        const val INTERVAL_NANOS = 250_000_000L
        const val NONE = -1L
        val COMPONENTS = listOf(
            Component(FocusMeteringAction.FLAG_AE, CaptureResult.CONTROL_AE_STATE,
                setOf(CaptureResult.CONTROL_AE_STATE_CONVERGED, CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED),
                CaptureResult.CONTROL_AE_STATE_SEARCHING),
            Component(FocusMeteringAction.FLAG_AWB, CaptureResult.CONTROL_AWB_STATE,
                setOf(CaptureResult.CONTROL_AWB_STATE_CONVERGED), CaptureResult.CONTROL_AWB_STATE_SEARCHING),
            Component(FocusMeteringAction.FLAG_AF, CaptureResult.CONTROL_AF_STATE,
                setOf(CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED), CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN)
        )
    }
}
