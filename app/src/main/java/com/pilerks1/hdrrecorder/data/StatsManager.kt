package com.pilerks1.hdrrecorder.data

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages and exposes real-time camera statistics.
 * This class is responsible for calculating FPS, tracking dropped/added frames,
 * and reporting sensor data like ISO and shutter speed.
 */
class StatsManager {

    // --- StateFlows for real-time stats ---
    private val _effectiveFps = MutableStateFlow(0)
    val effectiveFps = _effectiveFps.asStateFlow()

    private val _droppedFrames = MutableStateFlow(0)
    val droppedFrames = _droppedFrames.asStateFlow()

    private val _addedFrames = MutableStateFlow(0)
    val addedFrames = _addedFrames.asStateFlow()

    private val _iso = MutableStateFlow(0)
    val iso = _iso.asStateFlow()

    private val _shutterSpeed = MutableStateFlow(0.0)
    val shutterSpeed = _shutterSpeed.asStateFlow()

    // --- FPS Calculation Properties ---
    private var frameCount = 0
    private var lastVideoTimestampNanos = -1L
    private var targetFps = 60

    // --- Throttling Properties ---
    private var lastPreviewStatsTimestamp = 0L
    private val STATS_UPDATE_INTERVAL_MS = 500L // Update ISO/Shutter every 500ms

    val previewStatsCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            super.onCaptureCompleted(session, request, result)

            // Optimization: Throttle updates to reduce UI recomposition load
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastPreviewStatsTimestamp < STATS_UPDATE_INTERVAL_MS) {
                return
            }
            lastPreviewStatsTimestamp = currentTime

            _iso.value = result.get(TotalCaptureResult.SENSOR_SENSITIVITY) ?: 0
            val expTime = result.get(TotalCaptureResult.SENSOR_EXPOSURE_TIME) ?: 0
            _shutterSpeed.value = if (expTime > 0) 1_000_000_000.0 / expTime else 0.0
        }
    }

    val videoStatsCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            super.onCaptureCompleted(session, request, result)
            // Video stats logic is already throttled by the 1-second check below
            if (lastVideoTimestampNanos < 0L) return

            val frameTimestamp = result.get(TotalCaptureResult.SENSOR_TIMESTAMP) ?: return

            if (lastVideoTimestampNanos == 0L) {
                lastVideoTimestampNanos = frameTimestamp
                return
            }

            frameCount++
            val duration = frameTimestamp - lastVideoTimestampNanos

            if (duration >= 1_000_000_000L) {
                _effectiveFps.value = ((frameCount * 1_000_000_000L) / duration).toInt()
                val expectedFramesInInterval = (targetFps * duration / 1_000_000_000L).toInt()
                val frameDiff = frameCount - expectedFramesInInterval
                if (frameDiff < 0) {
                    _droppedFrames.value += -frameDiff
                } else if (frameDiff > 0) {
                    _addedFrames.value += frameDiff - 1
                }
                frameCount = 0
                lastVideoTimestampNanos = frameTimestamp
            }
        }
    }

    fun startFpsCalculation(targetFps: Int) {
        this.targetFps = targetFps
        frameCount = 0
        _droppedFrames.value = 0
        _addedFrames.value = 0
        lastVideoTimestampNanos = 0L
    }

    fun stopFpsCalculation() {
        lastVideoTimestampNanos = -1L
        _effectiveFps.value = 0
    }
}