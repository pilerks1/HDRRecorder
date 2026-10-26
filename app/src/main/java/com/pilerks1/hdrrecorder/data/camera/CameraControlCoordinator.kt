package com.pilerks1.hdrrecorder.data.camera

import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.util.Range
import com.pilerks1.hdrrecorder.data.camera.camerax.CameraXRequestBackend
import com.pilerks1.hdrrecorder.data.camera.core.CameraControlValues
import com.pilerks1.hdrrecorder.data.camera.core.CameraFrameMetadata
import com.pilerks1.hdrrecorder.data.camera.core.CameraRequestCompiler
import com.pilerks1.hdrrecorder.data.camera.core.CameraRequestBackend
import com.pilerks1.hdrrecorder.data.camera.core.CameraRequestOverrides
import com.pilerks1.hdrrecorder.data.camera.core.CameraRequestSupersededException
import com.pilerks1.hdrrecorder.data.camera.core.CameraResultReader
import com.pilerks1.hdrrecorder.data.camera.core.InteropSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import com.pilerks1.hdrrecorder.data.camera.core.FixedFrameRates
import androidx.camera.core.MeteringPoint
import com.pilerks1.hdrrecorder.data.camera.core.MeteringAnchor
import com.pilerks1.hdrrecorder.data.camera.core.MeteringMode
import com.pilerks1.hdrrecorder.data.camera.core.RegionalMeteringState
import com.pilerks1.hdrrecorder.data.camera.core.TemporaryMeteringMonitor
import com.pilerks1.hdrrecorder.data.camera.core.meteringFlags

data class DesiredCameraState(
    val controls: CameraControlValues,
    val settings: InteropSettings,
    val capabilities: CameraCapabilities?,
    val selectedFps: Int
)

enum class ExposureMode { AUTO, SHUTTER_PRIORITY, SENSITIVITY_PRIORITY, FULL_MANUAL }

data class ExposureTransitionResult(
    val controls: CameraControlValues,
    val usedUnlockedHandoff: Boolean = false
)

/** Serializes submissions, native CameraX controls and result-driven exposure handoffs. */
class CameraControlCoordinator(private val results: CameraResultReader) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val mutex = Mutex()
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private var backend: CameraRequestBackend? = null
    private var cameraXControls: CameraXRequestBackend? = null
    private var desired: DesiredCameraState? = null
    private var revision = 0L
    private var immediate = false
    private var lastSubmissionMs = 0L
    private var transitionVersion = 0L
    private var transitioning = false
    private val contributions = linkedMapOf<String, CameraRequestOverrides>()
    private val _meteringState = MutableStateFlow(RegionalMeteringState())
    val meteringState = _meteringState.asStateFlow()
    private var meteringPoint: MeteringPoint? = null
    private var meteringRevision = 0L
    private var appliedMeteringRevision = 0L
    private var nextMeteringId = 0L
    private var meteringMonitor: TemporaryMeteringMonitor? = null

    init {
        scope.launch {
            results.latest.filterNotNull().collect { frame ->
                if (!transitioning && frame.generation == backend?.generation &&
                    meteringMonitor?.shouldCancel(frame) == true
                ) cancelMetering()
            }
        }
        scope.launch {
            for (ignored in signal) {
                if (!immediate) delay((lastSubmissionMs + 33L - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
                immediate = false
                try {
                    mutex.withLock {
                        if (!transitioning) desired?.let { state -> backend?.let {
                            submit(it, state)
                            if (desired === state && !transitioning) reconcileMetering(it, state)
                        } }
                    }
                } catch (_: CameraRequestSupersededException) {
                    // The replacement attachment/update will receive the latest desired state.
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Log.w("CameraControlCoordinator", "Unable to apply camera settings", failure)
                }
            }
        }
    }

    fun attach(attachment: CameraRequestBackend, nativeControls: CameraXRequestBackend? = null) {
        detach()
        backend = attachment
        cameraXControls = nativeControls
        _meteringState.value = RegionalMeteringState(generation = attachment.generation)
        // The owner supplies a capability-adapted desired state after binding.
    }

    fun detach() {
        meteringRevision++
        appliedMeteringRevision = meteringRevision
        meteringPoint = null
        meteringMonitor = null
        _meteringState.value = RegionalMeteringState()
        transitionVersion++
        transitioning = false
        backend?.detach()
        backend = null
        cameraXControls = null
    }

    fun update(state: DesiredCameraState, coalesce: Boolean = false) {
        val previous = desired?.controls
        if (previous != null && manualModes(previous) != manualModes(state.controls)) restartMetering()
        desired = state
        revision++
        immediate = immediate || !coalesce
        signal.trySend(Unit)
    }

    private fun manualModes(controls: CameraControlValues) = listOf(
        controls.isManualIso, controls.isManualSs, controls.isManualFocus, controls.isManualWb
    )

    private fun restartMetering() {
        if (meteringPoint == null) return
        meteringMonitor = null
        meteringRevision++
    }

    /** Future features use typed snapshots; ownership conflicts fail at composition. */
    fun contribute(owner: String, overrides: CameraRequestOverrides) {
        require(owner != "controls") { "The controls owner is reserved" }
        contributions[owner] = overrides
        revision++
        signal.trySend(Unit)
    }

    suspend fun setLowLightBoost(enabled: Boolean) = mutex.withLock {
        try {
            cameraXControls?.setLowLightBoost(enabled)
        } catch (_: CameraRequestSupersededException) {
            // Rebinding reapplies the current preference.
        }
    }

    // Native zoom supersedes earlier zoom requests without waiting for exposure submissions.
    fun setZoomRatio(ratio: Float): com.google.common.util.concurrent.ListenableFuture<Void>? {
        onZoomChanged()
        return cameraXControls?.setZoomRatio(ratio)
    }

    fun setLinearZoom(progress: Float): com.google.common.util.concurrent.ListenableFuture<Void>? {
        onZoomChanged()
        return cameraXControls?.setLinearZoom(progress)
    }

    fun onZoomChanged() {
        if (_meteringState.value.mode == MeteringMode.TEMPORARY) cancelMetering()
    }

    suspend fun flush() = mutex.withLock {
        if (!transitioning) desired?.let { state -> backend?.let { submit(it, state) } }
    }

    fun startMetering(point: MeteringPoint, anchor: MeteringAnchor, mode: MeteringMode, generation: Long) {
        val attachment = backend ?: return
        if (attachment.generation != generation || cameraXControls == null) return
        val state = desired ?: return
        val flags = meteringFlags(state.controls, state.capabilities)
        if (flags == 0) {
            cancelMetering()
            return
        }
        meteringPoint = point
        meteringMonitor = null
        meteringRevision++
        _meteringState.value = RegionalMeteringState(generation, ++nextMeteringId, mode, anchor, flags)
        signal.trySend(Unit)
    }

    fun remapMetering(point: MeteringPoint, generation: Long, requestId: Long) {
        val current = _meteringState.value
        if (current.mode != MeteringMode.PERSISTENT || current.generation != generation ||
            current.requestId != requestId
        ) return
        meteringPoint = point
        meteringRevision++
        signal.trySend(Unit)
    }

    fun cancelMetering(generation: Long = _meteringState.value.generation) {
        if (_meteringState.value.generation != generation) return
        meteringPoint = null
        meteringMonitor = null
        meteringRevision++
        _meteringState.value = RegionalMeteringState(generation = generation)
        signal.trySend(Unit)
    }

    private suspend fun reconcileMetering(attachment: CameraRequestBackend, state: DesiredCameraState) {
        val native = cameraXControls ?: return
        val current = _meteringState.value
        val flags = if (current.mode == null) 0 else meteringFlags(state.controls, state.capabilities)
        if (current.mode != null && flags != current.eligibleFlags) {
            meteringMonitor = null
            meteringRevision++
            if (flags == 0) {
                meteringPoint = null
                _meteringState.value = RegionalMeteringState(generation = attachment.generation)
            } else _meteringState.value = current.copy(eligibleFlags = flags)
        }
        if (appliedMeteringRevision == meteringRevision) return
        val version = meteringRevision
        val point = meteringPoint
        meteringMonitor = null
        try {
            native.cancelMetering()
            ensureCurrent(attachment)
            if (version != meteringRevision) return
            if (point != null && flags != 0) {
                native.applyMetering(point, flags)
                ensureCurrent(attachment)
                if (version != meteringRevision) return
                if (_meteringState.value.mode == MeteringMode.TEMPORARY) {
                    meteringMonitor = TemporaryMeteringMonitor(flags, results.latest.value?.frameNumber ?: -1L)
                }
            }
            appliedMeteringRevision = version
        } catch (_: CameraRequestSupersededException) {
            // Rebind owns cleanup; an old request must never touch its replacement.
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (backend !== attachment) return
            meteringPoint = null
            _meteringState.value = RegionalMeteringState(generation = attachment.generation)
            appliedMeteringRevision = ++meteringRevision
            try {
                native.cancelMetering()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Preserve the original application error.
            }
            Log.w("CameraControlCoordinator", "Unable to apply metering region", failure)
        }
    }

    private fun snapshot(state: DesiredCameraState, lock: Boolean? = null): CameraRequestOverrides {
        val builder = CameraRequestOverrides.Builder().add(
            CameraRequestCompiler.compile(state.controls, state.settings, state.capabilities, lock), "controls"
        )
        contributions.forEach { (owner, overrides) -> builder.add(overrides, owner) }
        return builder.build()
    }

    private suspend fun submit(attachment: CameraRequestBackend, state: DesiredCameraState, lock: Boolean? = null) {
        ensureCurrent(attachment)
        attachment.apply(snapshot(state, lock))
        ensureCurrent(attachment)
        lastSubmissionMs = SystemClock.elapsedRealtime()
    }

    fun exposureMode(controls: CameraControlValues, caps: CameraCapabilities): ExposureMode = when {
        controls.isManualIso && controls.isManualSs && caps.supportsFullManualExposure -> ExposureMode.FULL_MANUAL
        controls.isManualIso && caps.supportsIsoPriorityAe -> ExposureMode.SENSITIVITY_PRIORITY
        controls.isManualSs && caps.supportsShutterPriorityAe -> ExposureMode.SHUTTER_PRIORITY
        else -> ExposureMode.AUTO
    }

    suspend fun transition(
        previous: CameraControlValues,
        target: DesiredCameraState,
        waitToLockAe: Boolean = true
    ): ExposureTransitionResult {
        restartMetering()
        val version = ++transitionVersion
        transitioning = true
        return try {
            mutex.withLock {
                val attachment = backend ?: throw CameraRequestSupersededException()
                val caps = target.capabilities ?: error("Camera capabilities unavailable")
                val startRevision = revision
                val oldMode = exposureMode(previous, caps)
                val newMode = exposureMode(target.controls, caps)
                fun checkCurrent() {
                    ensureCurrent(attachment)
                    if (version != transitionVersion) throw CameraRequestSupersededException()
                }
                try {
                    withTimeout(2_000L) {
                        checkCurrent()
                        // Reset EV only in the same snapshot that disables AE, never before locking.
                        var resolved = if (newMode == ExposureMode.FULL_MANUAL) {
                            target.controls.copy(isManualEv = false, evValueIndex = 0)
                        } else target.controls
                        var unlocked = false
                        if (newMode != ExposureMode.AUTO) {
                            val held = if (waitToLockAe && oldMode != ExposureMode.FULL_MANUAL && caps.supportsAeLock) {
                                val source = target.copy(controls = previous)
                                val lockedOverrides = snapshot(source, lock = true)
                                val before = results.latest.value?.frameNumber ?: -1L
                                submit(attachment, source, lock = true)
                                awaitFrame(attachment, before) {
                                    matchesExposure(it, lockedOverrides, oldMode, locked = true)
                                }
                            } else {
                                unlocked = oldMode != ExposureMode.FULL_MANUAL
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
                                val overrides = CameraRequestOverrides.Builder().add(
                                    snapshot(target.copy(controls = handoff), lock = false), "handoff"
                                ).apply {
                                    if (newMode == ExposureMode.FULL_MANUAL) {
                                        set(CaptureRequest.SENSOR_FRAME_DURATION, maxOf(heldDuration, heldShutter), "handoff")
                                    }
                                }.build()
                                val before = results.latest.value?.frameNumber ?: -1L
                                attachment.apply(overrides)
                                awaitFrame(attachment, before) { frame -> matchesExposure(frame, overrides, newMode) }
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
                        val finalOverrides = snapshot(finalState)
                        val before = results.latest.value?.frameNumber ?: -1L
                        submit(attachment, finalState)
                        awaitFrame(attachment, before) { matchesExposure(it, finalOverrides, newMode) }
                        checkCurrent()
                        desired = if (revision == startRevision) finalState else {
                            (desired ?: finalState).let { latest ->
                                latest.copy(controls = latest.controls.withExposure(resolved))
                            }
                        }
                        ExposureTransitionResult(resolved, usedUnlockedHandoff = unlocked)
                    }
                } catch (failure: Exception) {
                    // Rebind/cancellation must never restore old settings onto a new camera.
                    checkCurrent()
                    if (failure is CameraRequestSupersededException) throw failure
                    if (failure is CancellationException && failure !is kotlinx.coroutines.TimeoutCancellationException) throw failure
                    val auto = target.controls.copy(
                        isManualIso = false, isoValue = null, isManualSs = false, ssValueNanos = null,
                        isManualPostRawSensitivity = false, postRawSensitivityBoost = null,
                        isManualEv = previous.isManualEv, evValueIndex = previous.evValueIndex
                    )
                    val recovery = target.copy(controls = auto)
                    val recovered = try {
                        withTimeout(2_000L) {
                            val before = results.latest.value?.frameNumber ?: -1L
                            submit(attachment, recovery)
                            awaitFrame(attachment, before) { matchesExposure(it, snapshot(recovery), ExposureMode.AUTO) }
                        }
                        true
                    } catch (superseded: CameraRequestSupersededException) {
                        throw superseded
                    } catch (cancelled: CancellationException) {
                        if (cancelled !is kotlinx.coroutines.TimeoutCancellationException) throw cancelled
                        false
                    } catch (_: Exception) { false }
                    desired = (desired ?: recovery).let { latest ->
                        latest.copy(controls = latest.controls.withExposure(auto))
                    }
                    val message = if (recovered) "Exposure transition failed; Auto restored"
                        else "Exposure transition failed; unable to confirm Auto"
                    Log.w("CameraControlCoordinator", message, failure)
                    ExposureTransitionResult(auto)
                }
            }
        } finally {
            if (version == transitionVersion) transitioning = false
            signal.trySend(Unit)
        }
    }

    private suspend fun awaitFrame(
        attachment: CameraRequestBackend,
        after: Long,
        predicate: (CameraFrameMetadata) -> Boolean
    ): CameraFrameMetadata = results.latest.filterNotNull().first {
        ensureCurrent(attachment)
        it.generation == attachment.generation && it.frameNumber > after && predicate(it)
    }

    private fun ensureCurrent(attachment: CameraRequestBackend) {
        if (backend !== attachment) throw CameraRequestSupersededException()
    }

    private fun matchesExposure(
        frame: CameraFrameMetadata,
        overrides: CameraRequestOverrides,
        mode: ExposureMode,
        locked: Boolean = false
    ): Boolean {
        val aeMode = frame[CaptureResult.CONTROL_AE_MODE] ?: return false
        val ev = overrides[CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION]
        if (ev != null && frame.requested(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION) != ev) return false
        if (locked) {
            if (frame.requested(CaptureRequest.CONTROL_AE_LOCK) != true ||
                frame[CaptureResult.CONTROL_AE_LOCK] != true ||
                frame[CaptureResult.CONTROL_AE_STATE] != CameraMetadata.CONTROL_AE_STATE_LOCKED ||
                (ev != null && frame[CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION] != ev)
            ) return false
        } else if (frame.requested(CaptureRequest.CONTROL_AE_LOCK) == true ||
            frame[CaptureResult.CONTROL_AE_LOCK] == true ||
            frame[CaptureResult.CONTROL_AE_STATE] == CameraMetadata.CONTROL_AE_STATE_LOCKED
        ) return false
        if (mode == ExposureMode.AUTO) {
            return aeMode != CameraMetadata.CONTROL_AE_MODE_OFF &&
                frame.requested(CaptureRequest.CONTROL_AE_MODE) != CameraMetadata.CONTROL_AE_MODE_OFF &&
                (Build.VERSION.SDK_INT < 36 || listOf(
                    frame.requested(CaptureRequest.CONTROL_AE_PRIORITY_MODE),
                    frame[CaptureResult.CONTROL_AE_PRIORITY_MODE]
                ).all {
                    it == null || it == CameraMetadata.CONTROL_AE_PRIORITY_MODE_OFF
                })
        }
        return frame.requested(CaptureRequest.CONTROL_AE_MODE) == overrides[CaptureRequest.CONTROL_AE_MODE] &&
            aeMode == overrides[CaptureRequest.CONTROL_AE_MODE] &&
            listOf(CaptureRequest.SENSOR_SENSITIVITY, CaptureRequest.SENSOR_EXPOSURE_TIME,
                CaptureRequest.SENSOR_FRAME_DURATION, CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST).all { key ->
                overrides[key]?.let { frame.requested(key) == it } ?: true
            } && (Build.VERSION.SDK_INT < 36 || frame.requested(CaptureRequest.CONTROL_AE_PRIORITY_MODE).let { actual ->
                val expected = overrides[CaptureRequest.CONTROL_AE_PRIORITY_MODE]
                if (expected == null) actual == null || actual == CameraMetadata.CONTROL_AE_PRIORITY_MODE_OFF
                else actual == expected && frame[CaptureResult.CONTROL_AE_PRIORITY_MODE] == expected
            })
    }

    private fun CameraControlValues.withExposure(source: CameraControlValues): CameraControlValues = copy(
        isManualIso = source.isManualIso,
        isoValue = source.isoValue,
        isManualSs = source.isManualSs,
        ssValueNanos = source.ssValueNanos,
        isManualPostRawSensitivity = source.isManualPostRawSensitivity,
        postRawSensitivityBoost = source.postRawSensitivityBoost,
        isManualEv = source.isManualEv,
        evValueIndex = source.evValueIndex,
        isManualFps = source.isManualFps,
        fpsRange = source.fpsRange
    )

    private fun manualFps(controls: CameraControlValues, caps: CameraCapabilities, selectedFps: Int): Range<Int> {
        val requested = controls.fpsRange?.upper ?: selectedFps
        val fps = controls.ssValueNanos?.let { FixedFrameRates.nearestFixedFrameRateForShutter(it, caps.fpsRanges) }
            ?: FixedFrameRates.nearestFixedFrameRate(requested, caps.fpsRanges)
            ?: requested
        return Range(fps, fps)
    }

    fun close() {
        detach()
        signal.close()
        scope.cancel()
    }
}
