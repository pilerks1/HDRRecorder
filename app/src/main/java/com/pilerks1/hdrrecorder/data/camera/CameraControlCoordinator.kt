package com.pilerks1.hdrrecorder.data.camera

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

data class DesiredCameraState(
    val controls: CameraControlValues,
    val settings: InteropSettings,
    val capabilities: CameraCapabilities?,
    val selectedFps: Int
)

enum class ExposureMode { AUTO, SHUTTER_PRIORITY, SENSITIVITY_PRIORITY, FULL_MANUAL }

data class ExposureTransitionResult(
    val controls: CameraControlValues,
    val message: String? = null,
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
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        scope.launch {
            for (ignored in signal) {
                if (!immediate) delay((lastSubmissionMs + 33L - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
                immediate = false
                try {
                    mutex.withLock {
                        if (!transitioning) desired?.let { state -> backend?.let { submit(it, state) } }
                    }
                } catch (_: CameraRequestSupersededException) {
                    // The replacement attachment/update will receive the latest desired state.
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    _error.value = failure.message ?: "Unable to apply camera settings"
                }
            }
        }
    }

    fun attach(attachment: CameraRequestBackend, nativeControls: CameraXRequestBackend? = null) {
        detach()
        backend = attachment
        cameraXControls = nativeControls
        // The owner supplies a capability-adapted desired state after binding.
    }

    fun detach() {
        transitionVersion++
        transitioning = false
        backend?.detach()
        backend = null
        cameraXControls = null
        _error.value = null
    }

    fun update(state: DesiredCameraState, coalesce: Boolean = false) {
        desired = state
        revision++
        immediate = immediate || !coalesce
        signal.trySend(Unit)
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
    fun setZoomRatio(ratio: Float) = cameraXControls?.setZoomRatio(ratio)

    fun setLinearZoom(progress: Float) = cameraXControls?.setLinearZoom(progress)

    suspend fun flush() = mutex.withLock {
        if (!transitioning) desired?.let { state -> backend?.let { submit(it, state) } }
    }

    suspend fun tapToMeter(point: androidx.camera.core.MeteringPoint) = mutex.withLock {
        val attachment = backend ?: return@withLock
        try {
            desired?.let { submit(attachment, it) }
            cameraXControls?.tapToMeter(point)
        } catch (_: CameraRequestSupersededException) {
            // A tap on the previous preview must not affect its replacement.
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
        if (lock == null && state.capabilities?.hasExposureCompensationControl == true) {
            cameraXControls?.setExposureCompensation(CameraRequestCompiler.exposureCompensationIndex(state.controls, state.capabilities))
        }
        attachment.apply(snapshot(state, lock))
        ensureCurrent(attachment)
        lastSubmissionMs = SystemClock.elapsedRealtime()
        _error.value = null
    }

    fun exposureMode(controls: CameraControlValues, caps: CameraCapabilities): ExposureMode = when {
        controls.isManualIso && controls.isManualSs && caps.supportsFullManualExposure -> ExposureMode.FULL_MANUAL
        controls.isManualIso && caps.supportsIsoPriorityAe -> ExposureMode.SENSITIVITY_PRIORITY
        controls.isManualSs && caps.supportsShutterPriorityAe -> ExposureMode.SHUTTER_PRIORITY
        else -> ExposureMode.AUTO
    }

    suspend fun transition(previous: CameraControlValues, target: DesiredCameraState): ExposureTransitionResult {
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
                        var resolved = target.controls
                        var unlocked = false
                        if (newMode != ExposureMode.AUTO) {
                            val held = if (oldMode != ExposureMode.FULL_MANUAL && caps.supportsAeLock) {
                                val before = results.latest.value?.frameNumber ?: -1L
                                submit(attachment, target.copy(controls = previous), lock = true)
                                awaitFrame(attachment, before) {
                                    it.requested(CaptureRequest.CONTROL_AE_LOCK) == true &&
                                        it[CaptureResult.CONTROL_AE_STATE] == CameraMetadata.CONTROL_AE_STATE_LOCKED &&
                                        it[CaptureResult.CONTROL_AE_LOCK] == true
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
                            val heldDuration = held[CaptureResult.SENSOR_FRAME_DURATION]
                                ?: error("No reported frame duration")
                            val heldBoost = held[CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST]
                            val handoff = resolved.copy(
                                isoValue = if (resolved.isManualIso) heldIso else null,
                                ssValueNanos = if (resolved.isManualSs) heldShutter else null,
                                isManualPostRawSensitivity = newMode == ExposureMode.FULL_MANUAL && caps.supportsPostRawSensitivityBoost,
                                postRawSensitivityBoost = if (newMode == ExposureMode.FULL_MANUAL) heldBoost else null
                            )
                            // Values copied from the locked result establish continuity before a user target is applied.
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
                            // Automatic slider values are observations, not explicit new user targets.
                            resolved = resolved.copy(
                                isoValue = if (resolved.isManualIso && resolved.isoValue == previous.isoValue) heldIso else resolved.isoValue,
                                ssValueNanos = if (resolved.isManualSs && resolved.ssValueNanos == previous.ssValueNanos) heldShutter else resolved.ssValueNanos,
                                isManualPostRawSensitivity = handoff.isManualPostRawSensitivity,
                                postRawSensitivityBoost = if (handoff.isManualPostRawSensitivity) {
                                    resolved.postRawSensitivityBoost?.takeUnless { it == previous.postRawSensitivityBoost } ?: heldBoost
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
                        isManualPostRawSensitivity = false, postRawSensitivityBoost = null
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
                    _error.value = message
                    ExposureTransitionResult(auto, message)
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

    private fun matchesExposure(frame: CameraFrameMetadata, overrides: CameraRequestOverrides, mode: ExposureMode): Boolean {
        val aeMode = frame[CaptureResult.CONTROL_AE_MODE] ?: return false
        if (mode == ExposureMode.AUTO) {
            return aeMode != CameraMetadata.CONTROL_AE_MODE_OFF &&
                frame.requested(CaptureRequest.CONTROL_AE_MODE) != CameraMetadata.CONTROL_AE_MODE_OFF &&
                frame.requested(CaptureRequest.CONTROL_AE_LOCK) != true && frame[CaptureResult.CONTROL_AE_LOCK] != true &&
                frame[CaptureResult.CONTROL_AE_STATE] != CameraMetadata.CONTROL_AE_STATE_LOCKED &&
                (Build.VERSION.SDK_INT < 36 || frame[CaptureResult.CONTROL_AE_PRIORITY_MODE].let {
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
            }) && frame[CaptureResult.CONTROL_AE_LOCK] != true
    }

    private fun CameraControlValues.withExposure(source: CameraControlValues): CameraControlValues = copy(
        isManualIso = source.isManualIso,
        isoValue = source.isoValue,
        isManualSs = source.isManualSs,
        ssValueNanos = source.ssValueNanos,
        isManualPostRawSensitivity = source.isManualPostRawSensitivity,
        postRawSensitivityBoost = source.postRawSensitivityBoost,
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
