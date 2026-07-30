package com.pilerks1.hdrrecorder.data.camera

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.os.Build
import android.os.SystemClock
import com.pilerks1.hdrrecorder.data.FrameRateEstimator
import com.pilerks1.hdrrecorder.data.StatsManager
import com.pilerks1.hdrrecorder.data.TelemetryStore
import com.pilerks1.hdrrecorder.model.CameraSample
import com.pilerks1.hdrrecorder.model.CameraTelemetry
import com.pilerks1.hdrrecorder.model.ExposureCaptureResult
import com.pilerks1.hdrrecorder.model.StatsSnapshot
import java.util.concurrent.atomic.AtomicReference
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

enum class SliderTelemetryField {
    ISO,
    SHUTTER,
    FOCUS,
    WHITE_BALANCE
}

data class TelemetryDemand(
    val statsVisible: Boolean = true,
    val automaticSliderField: SliderTelemetryField? = null,
    val exposureAcknowledgement: Boolean = false
)

/**
 * Owns Camera2 telemetry callbacks and presents the unified telemetry bank to UI at fixed rates.
 * Capture callbacks only latch values; they never publish Compose-facing state directly.
 */
class TelemetryCoordinator(
    private val store: TelemetryStore,
    private val statsManager: StatsManager
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val demand = AtomicReference(TelemetryDemand())
    private val cameraActive = AtomicBoolean(false)
    private val _statsState = MutableStateFlow(StatsSnapshot())
    val statsState = _statsState.asStateFlow()
    private val _recordingDurationNanos = MutableStateFlow(0L)
    val recordingDurationNanos = _recordingDurationNanos.asStateFlow()
    private val _sliderTelemetry = MutableStateFlow(CameraTelemetry())
    val sliderTelemetry = _sliderTelemetry.asStateFlow()
    private val _exposureAcknowledgements =
        MutableStateFlow<ExposureCaptureResult?>(null)
    val exposureAcknowledgements = _exposureAcknowledgements.asStateFlow()

    private val fpsLock = Any()
    private var fpsEnabled = false
    private var videoFrameCount = 0
    private var lastVideoTimestampNanos = 0L
    private var publicationJob: Job? = null

    init {
        startPresentation()
    }

    fun updateDemand(newDemand: TelemetryDemand) {
        demand.updateAndGet { current ->
            newDemand.copy(exposureAcknowledgement = current.exposureAcknowledgement)
        }
    }

    fun setExposureAcknowledgementDemand(enabled: Boolean) {
        demand.updateAndGet { it.copy(exposureAcknowledgement = enabled) }
    }

    fun beginCameraGeneration(): Long {
        val generation = store.beginCameraGeneration()
        cameraActive.set(true)
        statsManager.setTelemetryActive(true)
        _sliderTelemetry.value = CameraTelemetry()
        _exposureAcknowledgements.value = null
        return generation
    }

    fun endCameraGeneration(generation: Long) {
        store.clearCamera(generation)
        if (store.snapshot().cameraGeneration == generation) {
            cameraActive.set(false)
            statsManager.setTelemetryActive(false)
            _sliderTelemetry.value = CameraTelemetry()
            _exposureAcknowledgements.value = null
            _statsState.value = _statsState.value.copy(iso = 0, shutterSpeed = 0.0)
        }
    }

    fun currentExposureResult(): ExposureCaptureResult? =
        store.snapshot().camera.asExposureCaptureResult()

    fun currentCameraTelemetry(): CameraTelemetry =
        store.snapshot().camera.asCameraTelemetry()

    fun previewCaptureCallback(generation: Long) =
        object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                if (store.snapshot().cameraGeneration != generation) return
                val currentDemand = demand.get()
                val needsExposure = currentDemand.statsVisible ||
                    currentDemand.automaticSliderField == SliderTelemetryField.ISO ||
                    currentDemand.automaticSliderField == SliderTelemetryField.SHUTTER ||
                    currentDemand.exposureAcknowledgement
                val needsFocus =
                    currentDemand.automaticSliderField == SliderTelemetryField.FOCUS
                val needsWhiteBalance =
                    currentDemand.automaticSliderField == SliderTelemetryField.WHITE_BALANCE

                val sample = CameraSample(
                    cameraGeneration = generation,
                    frameNumber = result.frameNumber,
                    capturedAtElapsedNanos = SystemClock.elapsedRealtimeNanos(),
                    sensorSensitivityIso = if (needsExposure) {
                        result.get(TotalCaptureResult.SENSOR_SENSITIVITY)
                    } else null,
                    postRawSensitivityBoost = if (needsExposure) {
                        result.get(TotalCaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST)
                    } else null,
                    shutterNanos = if (needsExposure) {
                        result.get(TotalCaptureResult.SENSOR_EXPOSURE_TIME)?.takeIf { it > 0L }
                    } else null,
                    frameDurationNanos = if (needsExposure) {
                        result.get(TotalCaptureResult.SENSOR_FRAME_DURATION)
                    } else null,
                    focusDistanceDiopters = if (needsFocus) {
                        result.get(TotalCaptureResult.LENS_FOCUS_DISTANCE)
                    } else null,
                    cctTemperatureKelvin = if (needsWhiteBalance && Build.VERSION.SDK_INT >= 36) {
                        result.get(TotalCaptureResult.COLOR_CORRECTION_COLOR_TEMPERATURE)
                    } else null,
                    cctTint = if (needsWhiteBalance && Build.VERSION.SDK_INT >= 36) {
                        result.get(TotalCaptureResult.COLOR_CORRECTION_COLOR_TINT)
                    } else null,
                    aeState = if (currentDemand.exposureAcknowledgement) {
                        result.get(TotalCaptureResult.CONTROL_AE_STATE)
                    } else null,
                    aeLocked = if (currentDemand.exposureAcknowledgement) {
                        result.get(TotalCaptureResult.CONTROL_AE_LOCK)
                    } else null
                )
                store.updateCamera(sample)
                if (currentDemand.exposureAcknowledgement) {
                    _exposureAcknowledgements.value = sample.asExposureCaptureResult()
                }
            }
        }

    fun videoCaptureCallback(generation: Long) =
        object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                if (store.snapshot().cameraGeneration != generation) return
                val frameTimestamp =
                    result.get(TotalCaptureResult.SENSOR_TIMESTAMP) ?: return
                synchronized(fpsLock) {
                    if (!fpsEnabled) return
                    if (lastVideoTimestampNanos == 0L) {
                        lastVideoTimestampNanos = frameTimestamp
                        return
                    }
                    videoFrameCount++
                    val duration = frameTimestamp - lastVideoTimestampNanos
                    if (duration < NANOS_PER_SECOND) return

                    val targetFps = store.snapshot().recording.targetFps
                    val measured = FrameRateEstimator.sample(
                        videoFrameCount,
                        duration,
                        targetFps
                    )
                    store.updateRecording { recording ->
                        recording.copy(
                            capturedAtElapsedNanos = SystemClock.elapsedRealtimeNanos(),
                            effectiveFps = measured.effectiveFps,
                            droppedFrames = recording.droppedFrames +
                                (measured.droppedFrames ?: 0)
                        )
                    }
                    videoFrameCount = 0
                    lastVideoTimestampNanos = frameTimestamp
                }
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
                val snapshot = store.snapshot()
                val currentDemand = demand.get()

                currentDemand.automaticSliderField?.let { field ->
                    val telemetry = sliderProjection(snapshot.camera, field)
                    if (_sliderTelemetry.value != telemetry) {
                        _sliderTelemetry.value = telemetry
                    }
                }

                if (tick % STATS_DIVISOR == 0L && currentDemand.statsVisible) {
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

                tick++
                delay(PRESENTATION_INTERVAL_MS)
            }
        }
    }

    fun cleanup() {
        publicationJob?.cancel()
        scope.cancel()
    }

    private fun sliderProjection(
        camera: CameraSample,
        field: SliderTelemetryField
    ): CameraTelemetry = when (field) {
        SliderTelemetryField.ISO -> CameraTelemetry(
            iso = camera.sensorSensitivityIso,
            postRawSensitivityBoost = camera.postRawSensitivityBoost
        )
        SliderTelemetryField.SHUTTER -> CameraTelemetry(
            shutterNanos = camera.shutterNanos,
            frameDurationNanos = camera.frameDurationNanos
        )
        SliderTelemetryField.FOCUS -> CameraTelemetry(
            focusDistanceDiopters = camera.focusDistanceDiopters
        )
        SliderTelemetryField.WHITE_BALANCE -> CameraTelemetry(
            cctTemperatureKelvin = camera.cctTemperatureKelvin,
            cctTint = camera.cctTint
        )
    }

    private companion object {
        const val PRESENTATION_INTERVAL_MS = 250L
        const val STATS_DIVISOR = 2L
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
