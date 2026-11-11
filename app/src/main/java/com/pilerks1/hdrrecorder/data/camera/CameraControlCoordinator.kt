package com.pilerks1.hdrrecorder.data.camera

import com.pilerks1.hdrrecorder.data.diagnostics.ErrorReporter
import com.pilerks1.hdrrecorder.data.diagnostics.IssueCode

import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.os.Build
import android.os.SystemClock
import android.util.Range
import com.pilerks1.hdrrecorder.data.camera.camerax.CameraXRequestBackend
import com.pilerks1.hdrrecorder.data.camera.core.CameraControlValues
import com.pilerks1.hdrrecorder.data.camera.core.CameraFrameMetadata
import com.pilerks1.hdrrecorder.data.camera.core.CameraRequestCompiler
import com.pilerks1.hdrrecorder.data.camera.core.CameraRequestOverrides
import com.pilerks1.hdrrecorder.data.camera.core.CameraRequestSupersededException
import com.pilerks1.hdrrecorder.data.camera.core.CameraResultReader
import com.pilerks1.hdrrecorder.data.camera.core.InteropSettings
import com.pilerks1.hdrrecorder.data.camera.core.LowLightBoostPolicy
import com.pilerks1.hdrrecorder.data.camera.core.LowLightBoostRequestState
import com.pilerks1.hdrrecorder.data.camera.core.LowLightBoostRequestStatus
import com.pilerks1.hdrrecorder.data.camera.core.ExposureMode
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    val selectedFps: Int,
    val previewFpsRange: Range<Int>? = null
)

/** Serializes submissions, native CameraX controls and result-driven exposure handoffs. */
class CameraControlCoordinator(private val results: CameraResultReader, private val reporter: ErrorReporter) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val mutex = Mutex()
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private var backend: CameraXRequestBackend? = null
    private var desired: DesiredCameraState? = null
    private var revision = 0L
    private var immediate = false
    private var lastSubmissionMs = 0L
    private var transitionVersion = 0L
    private var transitioning = false
    private var boostRequested = false
    private val boostChanges = MutableStateFlow(0L)
    private var boostRevision = 0L
        set(value) {
            field = value
            boostChanges.value = value
        }
    private var attemptedBoostRevision: Long? = null
    private val _lowLightBoostRequest = MutableStateFlow(LowLightBoostRequestState())
    val lowLightBoostRequest = _lowLightBoostRequest.asStateFlow()
    private val _meteringState = MutableStateFlow(RegionalMeteringState())
    val meteringState = _meteringState.asStateFlow()
    private var meteringPoint: MeteringPoint? = null
    private var meteringRevision = 0L
    private var appliedMeteringRevision = 0L
    private var nextMeteringId = 0L
    private var meteringMonitor: TemporaryMeteringMonitor? = null
        set(value) {
            field = value
            monitorJob?.cancel()
            // Frames are only examined on Main while a temporary metering monitor is active.
            monitorJob = value?.let { monitor ->
                scope.launch {
                    results.latest.filterNotNull().collect { frame ->
                        if (!transitioning && frame.generation == backend?.generation && monitor.shouldCancel(frame)) {
                            cancelMetering()
                        }
                    }
                }
            }
        }
    private var monitorJob: Job? = null

    init {
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
                    reporter.report(IssueCode.CAMERA_CONTROL, "applyCameraSettings", failure)
                }
            }
        }
    }

    fun attach(attachment: CameraXRequestBackend) {
        detach()
        backend = attachment
        _lowLightBoostRequest.value = LowLightBoostRequestState(generation = attachment.generation)
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
        desired = null
        boostRevision++
        attemptedBoostRevision = null
        _lowLightBoostRequest.value = LowLightBoostRequestState()
    }

    fun update(state: DesiredCameraState, coalesce: Boolean = false) {
        val previous = desired
        if (previous != null && (manualModes(previous.controls) != manualModes(state.controls) ||
            focusMode(previous) != focusMode(state))
        ) restartMetering()
        updateBoostIntent(state)
        desired = state
        revision++
        immediate = immediate || !coalesce
        signal.trySend(Unit)
    }

    private fun manualModes(controls: CameraControlValues) = listOf(
        controls.isManualIso, controls.isManualSs, controls.isManualFocus, controls.isManualWb
    )

    private fun focusMode(state: DesiredCameraState): Int? =
        CameraRequestCompiler.focusMode(state.controls, state.settings, state.capabilities)

    private fun restartMetering() {
        if (meteringPoint == null) return
        meteringMonitor = null
        meteringRevision++
    }

    private fun updateBoostIntent(state: DesiredCameraState) {
        val requested = state.boostEnabled()
        if (requested != boostRequested) {
            boostRequested = requested
            boostRevision++
            _lowLightBoostRequest.value = LowLightBoostRequestState(
                generation = backend?.generation, revision = boostRevision, enabled = requested
            )
        }
    }

    private fun DesiredCameraState.boostEnabled(): Boolean =
        controls.isLowLightBoostEnabled && LowLightBoostPolicy.isSupported(capabilities?.supportsLowLightBoost == true) &&
            LowLightBoostPolicy.isEligible(controls, selectedFps, previewFpsRange)

    /** A loaded preset supersedes pending completions; its new bind owns the next attempt. */
    fun invalidateLowLightBoostRequest() {
        desired = null
        boostRevision++
        attemptedBoostRevision = boostRevision
        _lowLightBoostRequest.value = LowLightBoostRequestState(generation = backend?.generation)
    }

    private suspend fun reconcileLowLightBoost(attachment: CameraXRequestBackend, enabled: Boolean) {
        ensureCurrent(attachment)
        if (enabled != boostRequested) throw CameraRequestSupersededException()
        if (attemptedBoostRevision == boostRevision) return
        val version = boostRevision
        attemptedBoostRevision = version
        val pending = LowLightBoostRequestState(
            attachment.generation, version, enabled, LowLightBoostRequestStatus.PENDING
        )
        _lowLightBoostRequest.value = pending
        try {
            coroutineScope {
                val request = async(start = CoroutineStart.UNDISPATCHED) { attachment.setLowLightBoost(enabled) }
                val superseded = async { boostChanges.first { it != version } }
                try {
                    select {
                        request.onAwait { }
                        superseded.onAwait { throw CameraRequestSupersededException() }
                    }
                } finally {
                    request.cancel()
                    superseded.cancel()
                }
            }
            ensureCurrent(attachment)
            if (version == boostRevision) {
                _lowLightBoostRequest.value = pending.copy(status = LowLightBoostRequestStatus.SUCCEEDED)
            }
        } catch (superseded: CameraRequestSupersededException) {
            if (backend === attachment && version == boostRevision) {
                _lowLightBoostRequest.value = pending.copy(status = LowLightBoostRequestStatus.IDLE)
            }
            throw superseded
        } catch (cancelled: CancellationException) {
            // Cancelling the await does not undo a CameraX request. Reconcile the latest intent.
            if (backend === attachment && version == boostRevision) {
                attemptedBoostRevision = null
                _lowLightBoostRequest.value = pending.copy(status = LowLightBoostRequestStatus.IDLE)
            }
            signal.trySend(Unit)
            throw cancelled
        } catch (failure: Exception) {
            if (backend === attachment && version == boostRevision) {
                _lowLightBoostRequest.value = pending.copy(status = LowLightBoostRequestStatus.FAILED)
                reporter.report(IssueCode.CAMERA_CONTROL, "lowLightBoost", failure)
            }
        }
        ensureCurrent(attachment)
        if (version != boostRevision) throw CameraRequestSupersededException()
    }

    // Native zoom supersedes earlier zoom requests without waiting for exposure submissions.
    fun setZoomRatio(ratio: Float): com.google.common.util.concurrent.ListenableFuture<Void>? {
        onZoomChanged()
        return backend?.setZoomRatio(ratio)
    }

    private fun onZoomChanged() {
        if (_meteringState.value.mode == MeteringMode.TEMPORARY) cancelMetering()
    }

    fun startMetering(point: MeteringPoint, anchor: MeteringAnchor, mode: MeteringMode, generation: Long) {
        val attachment = backend ?: return
        if (attachment.generation != generation) return
        val state = desired ?: return
        val flags = meteringFlags(state.controls, state.capabilities, focusMode(state))
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

    private suspend fun reconcileMetering(attachment: CameraXRequestBackend, state: DesiredCameraState) {
        val native = attachment
        val current = _meteringState.value
        val flags = if (current.mode == null) 0 else meteringFlags(state.controls, state.capabilities, focusMode(state))
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
            } catch (cleanupError: Exception) {
                failure.addSuppressed(cleanupError)
                // Preserve the original application error.
            }
            reporter.report(IssueCode.CAMERA_CONTROL, "applyMetering", failure)
        }
    }

    private fun snapshot(state: DesiredCameraState, lock: Boolean? = null): CameraRequestOverrides =
        CameraRequestCompiler.compile(state.controls, state.settings, state.capabilities, lock)

    private suspend fun submit(attachment: CameraXRequestBackend, state: DesiredCameraState, lock: Boolean? = null) {
        ensureCurrent(attachment)
        val supportsBoost = LowLightBoostPolicy.isSupported(state.capabilities?.supportsLowLightBoost == true)
        val enableBoost = state.boostEnabled()
        if (supportsBoost && !enableBoost) reconcileLowLightBoost(attachment, false)
        attachment.apply(snapshot(state, lock))
        ensureCurrent(attachment)
        if (supportsBoost && enableBoost) reconcileLowLightBoost(attachment, true)
        lastSubmissionMs = SystemClock.elapsedRealtime()
    }

    suspend fun transition(
        previous: CameraControlValues,
        target: DesiredCameraState,
        waitToLockAe: Boolean
    ): CameraControlValues {
        restartMetering()
        val version = ++transitionVersion
        transitioning = true
        // Withdraw boost immediately, but commit exposure targets only after the handoff succeeds.
        desired = desired?.let { state ->
            state.copy(controls = state.controls.copy(isLowLightBoostEnabled = target.controls.isLowLightBoostEnabled))
        }
        updateBoostIntent(target)
        return try {
            mutex.withLock {
                val attachment = backend ?: throw CameraRequestSupersededException()
                val caps = target.capabilities ?: error("Camera capabilities unavailable")
                val startRevision = revision
                val oldMode = ExposureMode.resolve(previous, caps)
                val newMode = ExposureMode.resolve(target.controls, caps)
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
                        if (newMode != ExposureMode.AUTO) {
                            if (LowLightBoostPolicy.isSupported(caps.supportsLowLightBoost)) {
                                reconcileLowLightBoost(attachment, false)
                                checkCurrent()
                            }
                            val held = if (waitToLockAe && oldMode != ExposureMode.FULL_MANUAL && caps.supportsAeLock) {
                                val source = target.copy(controls = previous.copy(isLowLightBoostEnabled = false))
                                val lockedOverrides = snapshot(source, lock = true)
                                val before = results.latest.value?.frameNumber ?: -1L
                                submit(attachment, source, lock = true)
                                awaitFrame(attachment, before) {
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
                                val handoffOverrides = snapshot(target.copy(controls = handoff), lock = false)
                                val overrides = if (newMode == ExposureMode.FULL_MANUAL) {
                                    handoffOverrides.plus(CaptureRequest.SENSOR_FRAME_DURATION, maxOf(heldDuration, heldShutter))
                                } else handoffOverrides
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
                        resolved
                    }
                } catch (failure: Exception) {
                    // Rebind/cancellation must never restore old settings onto a new camera.
                    checkCurrent()
                    if (failure is CameraRequestSupersededException) throw failure
                    if (failure is CancellationException && failure !is kotlinx.coroutines.TimeoutCancellationException) throw failure
                    val auto = target.controls.withAutoExposure()
                        .copy(isManualEv = previous.isManualEv, evValueIndex = previous.evValueIndex)
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
                    } catch (recoveryError: Exception) {
                        failure.addSuppressed(recoveryError)
                        false
                    }
                    desired = (desired ?: recovery).let { latest ->
                        latest.copy(controls = latest.controls.withExposure(auto))
                    }
                    reporter.report(IssueCode.CAMERA_CONTROL, "exposureHandoff", failure,
                        mapOf("recovery" to if (recovered) "Auto restored" else "Auto recovery unconfirmed"))
                    auto
                }
            }
        } finally {
            if (version == transitionVersion) transitioning = false
            signal.trySend(Unit)
        }
    }

    private suspend fun awaitFrame(
        attachment: CameraXRequestBackend,
        after: Long,
        predicate: (CameraFrameMetadata) -> Boolean
    ): CameraFrameMetadata = results.latest.filterNotNull().first {
        ensureCurrent(attachment)
        it.generation == attachment.generation && it.frameNumber > after && predicate(it)
    }

    private fun ensureCurrent(attachment: CameraXRequestBackend) {
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
