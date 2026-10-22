package com.pilerks1.hdrrecorder.data.camera

import android.os.SystemClock
import com.pilerks1.hdrrecorder.data.FrameRateEstimator
import com.pilerks1.hdrrecorder.data.StatsManager
import com.pilerks1.hdrrecorder.data.TelemetryStore
import com.pilerks1.hdrrecorder.model.CameraTelemetry
import com.pilerks1.hdrrecorder.model.StatsSnapshot
import com.pilerks1.hdrrecorder.data.camera.core.CameraFrameMetadata
import com.pilerks1.hdrrecorder.data.camera.core.CameraResultReader
import com.pilerks1.hdrrecorder.data.camera.core.toCameraSample
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Consumes shared Camera2 frames and presents the telemetry bank to UI at fixed rates.
 * Acquisition only latches values; presentation updates Compose-facing state separately.
 */
class TelemetryCoordinator(
    private val store: TelemetryStore,
    private val statsManager: StatsManager,
    val results: CameraResultReader
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val cameraActive = AtomicBoolean(false)
    private val _statsState = MutableStateFlow(StatsSnapshot())
    val statsState = _statsState.asStateFlow()
    private val _recordingDurationNanos = MutableStateFlow(0L)
    val recordingDurationNanos = _recordingDurationNanos.asStateFlow()
    private val _sliderTelemetry = MutableStateFlow(CameraTelemetry())
    val sliderTelemetry = _sliderTelemetry.asStateFlow()

    private val fpsLock = Any()
    private var fpsEnabled = false
    private var videoFrameCount = 0
    private var lastVideoTimestampNanos = 0L
    private var publicationJob: Job? = null

    init {
        startPresentation()
    }

    @Synchronized
    fun beginCameraGeneration(): Long {
        val generation = results.begin()
        check(store.beginCameraGeneration() == generation)
        synchronized(fpsLock) {
            videoFrameCount = 0
            lastVideoTimestampNanos = 0L
        }
        cameraActive.set(true)
        statsManager.setTelemetryActive(true)
        _sliderTelemetry.value = CameraTelemetry()
        return generation
    }

    @Synchronized
    fun endCameraGeneration(generation: Long) {
        results.end(generation)
        store.clearCamera(generation)
        if (store.snapshot().cameraGeneration == generation) {
            cameraActive.set(false)
            statsManager.setTelemetryActive(false)
            _sliderTelemetry.value = CameraTelemetry()
            _statsState.value = _statsState.value.copy(iso = 0, shutterSpeed = 0.0)
        }
    }

    fun currentCameraTelemetry(): CameraTelemetry =
        store.snapshot().camera.asCameraTelemetry()

    /** Called once for each accepted repeating capture; publication remains independently throttled. */
    @Synchronized
    fun acceptFrame(frame: CameraFrameMetadata) {
        if (!results.accept(frame)) return
        val sample = frame.toCameraSample()
        store.updateCamera(sample)
        val frameTimestamp = frame.sensorTimestampNanos ?: return
        synchronized(fpsLock) {
            if (!fpsEnabled) return
            if (lastVideoTimestampNanos == 0L) {
                lastVideoTimestampNanos = frameTimestamp
                return
            }
            if (frameTimestamp <= lastVideoTimestampNanos) return
            videoFrameCount++
            val duration = frameTimestamp - lastVideoTimestampNanos
            if (duration < NANOS_PER_SECOND) return
            val measured = FrameRateEstimator.sample(videoFrameCount, duration, store.snapshot().recording.targetFps)
            store.updateRecording { recording ->
                recording.copy(
                    capturedAtElapsedNanos = SystemClock.elapsedRealtimeNanos(),
                    effectiveFps = measured.effectiveFps,
                    droppedFrames = recording.droppedFrames + (measured.droppedFrames ?: 0)
                )
            }
            videoFrameCount = 0
            lastVideoTimestampNanos = frameTimestamp
        }
    }

    fun startFpsCalculation() {
        synchronized(fpsLock) {
            fpsEnabled = true
            videoFrameCount = 0
            lastVideoTimestampNanos = 0L
        }
        store.updateRecording {
            it.copy(effectiveFps = 0, droppedFrames = 0)
        }
    }

    fun stopFpsCalculation() {
        synchronized(fpsLock) {
            fpsEnabled = false
            videoFrameCount = 0
            lastVideoTimestampNanos = 0L
        }
        store.updateRecording { it.copy(effectiveFps = 0) }
    }

    private fun startPresentation() {
        publicationJob = scope.launch {
            var tick = 0L
            while (isActive) {
                if (!cameraActive.get()) {
                    delay(PRESENTATION_INTERVAL_MS)
                    continue
                }
                synchronized(this@TelemetryCoordinator) {
                    if (cameraActive.get()) {
                        val snapshot = store.snapshot()

                        val telemetry = snapshot.camera.asCameraTelemetry()
                        if (_sliderTelemetry.value != telemetry) {
                            _sliderTelemetry.value = telemetry
                        }

                        if (tick % STATS_DIVISOR == 0L) {
                            val durationNanos = snapshot.recording.hardwareDurationNanos
                            if (_recordingDurationNanos.value != durationNanos) {
                                _recordingDurationNanos.value = durationNanos
                            }
                            val stats = statsManager.buildSnapshot(
                                telemetry = snapshot,
                                previous = _statsState.value,
                                statsTick = tick / STATS_DIVISOR
                            )
                            if (_statsState.value != stats) {
                                _statsState.value = stats
                            }
                        }
                    }
                }

                tick++
                delay(PRESENTATION_INTERVAL_MS)
            }
        }
    }

    fun cleanup() {
        publicationJob?.cancel()
        scope.cancel()
    }

    private companion object {
        const val PRESENTATION_INTERVAL_MS = 250L
        const val STATS_DIVISOR = 2L
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
