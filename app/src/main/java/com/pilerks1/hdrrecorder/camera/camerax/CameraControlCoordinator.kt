package com.pilerks1.hdrrecorder.camera.camerax

import android.os.SystemClock
import android.util.Range
import androidx.camera.core.MeteringPoint
import com.pilerks1.hdrrecorder.camera.camera2.CameraFrameMetadata
import com.pilerks1.hdrrecorder.camera.camera2.CameraRequestCompiler
import com.pilerks1.hdrrecorder.camera.camera2.CameraRequestOverrides
import com.pilerks1.hdrrecorder.camera.camera2.CameraRequestSupersededException
import com.pilerks1.hdrrecorder.camera.camera2.CameraResultReader
import com.pilerks1.hdrrecorder.camera.camera2.InteropSettings
import com.pilerks1.hdrrecorder.camera.model.CameraCapabilities
import com.pilerks1.hdrrecorder.camera.model.CameraControlValues
import com.pilerks1.hdrrecorder.camera.model.ExposureMode
import com.pilerks1.hdrrecorder.camera.model.LowLightBoostPolicy
import com.pilerks1.hdrrecorder.camera.model.LowLightBoostRequestState
import com.pilerks1.hdrrecorder.camera.model.LowLightBoostRequestStatus
import com.pilerks1.hdrrecorder.camera.model.MeteringAnchor
import com.pilerks1.hdrrecorder.camera.model.MeteringMode
import com.pilerks1.hdrrecorder.camera.model.RegionalMeteringState
import com.pilerks1.hdrrecorder.data.diagnostics.ErrorReporter
import com.pilerks1.hdrrecorder.data.diagnostics.IssueCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

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
                        val resolved = performExposureHandoff(
                            previous = previous,
                            target = target,
                            caps = caps,
                            oldMode = oldMode,
                            newMode = newMode,
                            waitToLockAe = waitToLockAe,
                            attachment = attachment,
                            results = results,
                            checkCurrent = ::checkCurrent,
                            snapshot = ::snapshot,
                            submit = { state, lock -> submit(attachment, state, lock) },
                            awaitFrame = { after, predicate -> awaitFrame(attachment, after, predicate) },
                            disableLowLightBoost = { reconcileLowLightBoost(attachment, false) }
                        )
                        val finalState = target.copy(controls = resolved)
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

    fun close() {
        detach()
        signal.close()
        scope.cancel()
    }
}
