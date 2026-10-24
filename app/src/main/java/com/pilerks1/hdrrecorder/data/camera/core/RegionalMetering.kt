package com.pilerks1.hdrrecorder.data.camera.core

import android.hardware.camera2.CaptureResult
import androidx.camera.core.FocusMeteringAction

enum class MeteringMode { TEMPORARY, PERSISTENT }

/** Position in the displayed preview, independent of its pixel dimensions. */
data class MeteringAnchor(val x: Float, val y: Float)

data class RegionalMeteringState(
    val generation: Long = 0L,
    val requestId: Long = 0L,
    val mode: MeteringMode? = null,
    val anchor: MeteringAnchor? = null,
    val eligibleFlags: Int = 0
)

internal fun meteringFlags(controls: CameraControlValues, caps: CameraCapabilities?): Int {
    caps ?: return 0
    var flags = 0
    if (!controls.isManualFocus && caps.maxAfRegions > 0 && caps.afModes.any {
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
    private val stableSince = mutableMapOf<Int, Long>()
    private val searchingSince = mutableMapOf<Int, Long>()
    private var armed = false

    fun shouldCancel(frame: CameraFrameMetadata): Boolean {
        if (frame.frameNumber <= afterFrame) return false
        val now = frame.receivedAtElapsedNanos
        val states = listOf(
            Triple(FocusMeteringAction.FLAG_AE, frame[CaptureResult.CONTROL_AE_STATE],
                setOf(CaptureResult.CONTROL_AE_STATE_CONVERGED, CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED)),
            Triple(FocusMeteringAction.FLAG_AWB, frame[CaptureResult.CONTROL_AWB_STATE],
                setOf(CaptureResult.CONTROL_AWB_STATE_CONVERGED)),
            Triple(FocusMeteringAction.FLAG_AF, frame[CaptureResult.CONTROL_AF_STATE],
                setOf(CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED))
        ).filter { (flag, state, _) -> flags and flag != 0 && state != null }
        if (states.isEmpty()) {
            stableSince.clear()
            searchingSince.clear()
            return false
        }
        if (!armed) {
            stableSince.keys.retainAll(states.map { it.first }.toSet())
            for ((flag, state, stable) in states) {
                if (state in stable) stableSince.putIfAbsent(flag, now) else stableSince.remove(flag)
            }
            // A component with no reporting does not prevent other components from arming.
            armed = states.all { (flag, _, _) ->
                stableSince[flag]?.let { now - it >= INTERVAL_NANOS } == true
            }
            return false
        }
        var cancel = false
        for (flag in listOf(FocusMeteringAction.FLAG_AE, FocusMeteringAction.FLAG_AWB, FocusMeteringAction.FLAG_AF)) {
            val state = states.firstOrNull { it.first == flag }?.second
            val searching = when (flag) {
                FocusMeteringAction.FLAG_AF -> state == CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN
                FocusMeteringAction.FLAG_AE -> state == CaptureResult.CONTROL_AE_STATE_SEARCHING
                else -> state == CaptureResult.CONTROL_AWB_STATE_SEARCHING
            }
            if (searching) {
                val since = searchingSince.getOrPut(flag) { now }
                cancel = cancel || now - since >= INTERVAL_NANOS
            } else searchingSince.remove(flag)
        }
        return cancel
    }

    private companion object {
        const val INTERVAL_NANOS = 250_000_000L
    }
}
