package com.pilerks1.hdrrecorder.data

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Represents a snapshot of all camera statistics at a single point in time.
 */
data class StatsSnapshot(
    val iso: Int = 0,
    val shutterSpeed: Double = 0.0,
    val effectiveFps: Int = 0,
    val droppedFrames: Int = 0,
    val addedFrames: Int = 0,
    // Future extensibility:
    val storageRemainingGb: Double = 0.0,
    val batteryLevel: Int = 0,
    val deviceTemp: Float = 0f
)

/**
 * Manages real-time camera statistics using a polling mechanism.
 * Instead of pushing updates on every frame (which is wasteful), it aggregates
 * data and emits a 'StatsSnapshot' at a fixed interval (e.g., 500ms).
 */
class StatsManager {

    // --- The Single Source of Truth for UI ---
    private val _statsState = MutableStateFlow(StatsSnapshot())
    val statsState = _statsState.asStateFlow()

    // --- Raw Data Holders (Thread-Safe) ---
    // We use Atomic types because these are written from Camera/Binder threads
    // and read by our polling coroutine.
    private val _currentIso = AtomicInteger(0)
    private val _currentShutterSpeed = AtomicReference(0.0)

    // FPS Calculation State
    private var frameCount = 0
    private var lastVideoTimestampNanos = -1L
    private var targetFps = 30

    // Accumulated Drop/Add counts (reset only on start)
    private val _totalDropped = AtomicInteger(0)
    private val _totalAdded = AtomicInteger(0)
    private val _currentFps = AtomicInteger(0)

    // --- Polling Control ---
    private var pollingJob: Job? = null
    private val POLL_INTERVAL_MS = 500L // 2Hz Update Rate (Good balance of responsiveness vs power)

    init {
        startPolling()
    }

    private fun startPolling() {
        // This coroutine runs on a background thread and updates the UI state periodically
        pollingJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                val snapshot = StatsSnapshot(
                    iso = _currentIso.get(),
                    shutterSpeed = _currentShutterSpeed.get(),
                    effectiveFps = _currentFps.get(),
                    droppedFrames = _totalDropped.get(),
                    addedFrames = _totalAdded.get()
                    // TODO: Add Battery/Storage reads here later
                )
                _statsState.emit(snapshot)
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    // --- Camera Callbacks (High Frequency - Keep Lightweight) ---

    val previewStatsCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            // Just update the atomic variables. No UI logic here. Very cheap.
            val iso = result.get(TotalCaptureResult.SENSOR_SENSITIVITY) ?: 0
            val expTime = result.get(TotalCaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L

            _currentIso.set(iso)
            if (expTime > 0) {
                _currentShutterSpeed.set(1_000_000_000.0 / expTime)
            }
        }
    }

    val videoStatsCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            // FPS Logic (Still needs to run per-frame to count correctly)
            if (lastVideoTimestampNanos < 0L) return

            val frameTimestamp = result.get(TotalCaptureResult.SENSOR_TIMESTAMP) ?: return

            if (lastVideoTimestampNanos == 0L) {
                lastVideoTimestampNanos = frameTimestamp
                return
            }

            frameCount++
            val duration = frameTimestamp - lastVideoTimestampNanos

            // Calculate FPS window every 1 second (independent of UI poll rate)
            if (duration >= 1_000_000_000L) {
                val fps = ((frameCount * 1_000_000_000L) / duration).toInt()
                _currentFps.set(fps)

                val expectedFramesInInterval = (targetFps * duration / 1_000_000_000L).toInt()
                val frameDiff = frameCount - expectedFramesInInterval

                if (frameDiff < 0) {
                    _totalDropped.addAndGet(-frameDiff)
                } else if (frameDiff > 0) {
                    _totalAdded.addAndGet(frameDiff - 1)
                }

                frameCount = 0
                lastVideoTimestampNanos = frameTimestamp
            }
        }
    }

    fun startFpsCalculation(targetFps: Int) {
        this.targetFps = targetFps
        frameCount = 0
        _totalDropped.set(0)
        _totalAdded.set(0)
        _currentFps.set(0)
        lastVideoTimestampNanos = 0L
    }

    fun stopFpsCalculation() {
        lastVideoTimestampNanos = -1L
        _currentFps.set(0)
    }

    fun cleanup() {
        pollingJob?.cancel()
    }
}