package com.pilerks1.hdrrecorder.data

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.os.StatFs
import android.os.SystemClock
import com.pilerks1.hdrrecorder.model.StatsSnapshot
import com.pilerks1.hdrrecorder.model.CameraTelemetry
import com.pilerks1.hdrrecorder.model.toSigFigs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import androidx.core.net.toUri

/**
 * Manages real-time camera statistics using a unified 500ms polling mechanism.
 * Uses @Volatile middle-men for non-blocking communication between hardware callbacks and UI.
 */
class StatsManager(private val context: Context) {

    private val _statsState = MutableStateFlow(StatsSnapshot())
    val statsState = _statsState.asStateFlow()

    private val _cameraTelemetry = MutableStateFlow(CameraTelemetry())
    val cameraTelemetry = _cameraTelemetry.asStateFlow()

    private val thermalManager = ThermalManager(context)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // --- Middle-man State (@Volatile for visibility, Atomic for counters) ---
    @Volatile private var currentIso: Int = 0
    @Volatile private var currentShutterSpeed: Double = 0.0
    @Volatile private var currentFps: Int = 0
    @Volatile private var lastTelemetryEmissionElapsedNanos = 0L
    private val totalDropped = AtomicInteger(0)

    // Hardware Recording Stats
    @Volatile private var recordedBytes: Long = 0L
    @Volatile private var hardwareDurationNanos: Long = 0L
    @Volatile private var actualBitrateMbps: Double = 0.0

    // Storage State
    @Volatile private var baselineAvailableBytes: Long = 0L
    @Volatile private var currentStorageUri: String? = null
    
    // Recording configuration
    @Volatile private var isRecording = false
    @Volatile private var targetFps: Int? = null
    @Volatile private var targetBitrateMbps = 30

    // Internal Pacing
    private var tickCount = 0
    private var pollingJob: Job? = null
    private val POLL_INTERVAL_MS = 500L

    init {
        // Initial baseline storage query
        updateBaselineStorage()
        startPolling()
    }

    fun updateStorageUri(uri: String?) {
        this.currentStorageUri = uri
        updateBaselineStorage()
    }

    fun setRecordingState(recording: Boolean, targetFps: Int?, targetBitrate: Int, storageUri: String? = null) {
        this.isRecording = recording
        this.targetFps = targetFps
        this.targetBitrateMbps = targetBitrate
        this.currentStorageUri = storageUri

        if (recording) {
            // Reset for new session
            recordedBytes = 0L
            hardwareDurationNanos = 0L
            currentFps = 0
            actualBitrateMbps = 0.0
            totalDropped.set(0)
            tickCount = 0
            // Get a fresh baseline exactly when recording starts
            updateBaselineStorage()
        } else {
            currentFps = 0
            actualBitrateMbps = 0.0
            // Refresh baseline when recording stops to account for the new file
            updateBaselineStorage()
        }
    }

    /**
     * Called by RecordingManager with hardware-accurate deltas.
     */
    fun updateHardwareStats(bytes: Long, durationNanos: Long, bitrate: Double) {
        this.recordedBytes = bytes
        this.hardwareDurationNanos = durationNanos
        this.actualBitrateMbps = bitrate
    }

    private fun startPolling() {
        pollingJob = scope.launch {
            while (isActive) {
                val snapshot = gatherSnapshot()
                _statsState.emit(snapshot)
                
                delay(POLL_INTERVAL_MS)
                tickCount++

                // Modulo Triggers for heavier tasks
                if (tickCount % 20 == 0) { // Every 10 seconds
                    launch { thermalManager.updateHeadroom() }
                }
                
                if (tickCount % 240 == 0 && !isRecording) { // Every 2 min, only if NOT recording
                    updateBaselineStorage()
                }
            }
        }
    }

    private fun gatherSnapshot(): StatsSnapshot {
        // 1. Get Instant Thermals (Fast)
        val thermalPower = thermalManager.getInstantThermalState()

        // 2. Intelligent Storage Calculation (Non-blocking)
        // Only recalculate storage formatted info every 20 seconds (40 ticks) or on state change to prevent flickering.
        val storageData = if (tickCount % 40 == 0 || !isRecording || _statsState.value.storageRemainingFormatted == "N/A") {
            val availableBytes = if (isRecording) {
                (baselineAvailableBytes - recordedBytes).coerceAtLeast(0L)
            } else {
                baselineAvailableBytes
            }
            formatStorageInfo(availableBytes, actualBitrateMbps)
        } else {
            Pair(
                _statsState.value.storageRemainingTime,
                _statsState.value.storageRemainingFormatted
            )
        }

        // 3. Paced File Size Display
        // Only update the "SIZE" stat on the UI every 20 seconds (40 ticks) to prevent flickering.
        val displayedSize = if (tickCount % 40 == 0 || !isRecording) recordedBytes else _statsState.value.displayedFileSizeWrittenBytes

        return StatsSnapshot(
            iso = currentIso,
            shutterSpeed = currentShutterSpeed,
            effectiveFps = currentFps,
            droppedFrames = totalDropped.get(),
            canMeasureDroppedFrames = targetFps != null,
            thermalStatus = thermalPower.thermalStatus,
            thermalForecast = thermalPower.thermalForecast,
            netPowerWatts = thermalPower.netPowerWatts,
            storageRemainingFormatted = storageData.second,
            storageRemainingTime = storageData.first,
            actualBitrateMbps = actualBitrateMbps,
            displayedFileSizeWrittenBytes = displayedSize,
            hardwareDurationNanos = hardwareDurationNanos
        )
    }

    private fun updateBaselineStorage() {
        scope.launch(Dispatchers.IO) {
            baselineAvailableBytes = getPhysicalAvailableBytes()
        }
    }

    private fun getPhysicalAvailableBytes(): Long {
        val uriStr = currentStorageUri ?: return getInternalAvailableBytes()
        return try {
            val uri = uriStr.toUri()
            when (uri.scheme) {
                "file" -> {
                    val stat = StatFs(uri.path)
                    stat.availableBlocksLong * stat.blockSizeLong
                }
                "content" -> {
                    val rootId = android.provider.DocumentsContract.getTreeDocumentId(uri).split(":")[0]
                    val rootUri = android.provider.DocumentsContract.buildRootUri(uri.authority, rootId)

                    context.contentResolver.query(
                        rootUri,
                        arrayOf(android.provider.DocumentsContract.Root.COLUMN_AVAILABLE_BYTES),
                        null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cursor.getLong(0)
                        } else getInternalAvailableBytes()
                    } ?: getInternalAvailableBytes()
                }
                else -> {
                    getInternalAvailableBytes()
                }
            }
        } catch (e: Exception) {
            getInternalAvailableBytes()
        }
    }

    private fun getInternalAvailableBytes(): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) { 0L }
    }

    private fun formatStorageInfo(availableBytes: Long, currentBitrate: Double): Pair<String, String> {
        val mbValue = availableBytes.toDouble() / 1_000_000.0

        val formattedStorage = if (mbValue >= 1000.0) {
            (mbValue / 1000.0).toSigFigs() + " GB"
        } else {
            mbValue.toSigFigs() + " MB"
        }

        val bitrateToUse = if (isRecording && currentBitrate > 0) currentBitrate else targetBitrateMbps.toDouble()
        val bytesPerSec = (bitrateToUse * 1_000_000.0) / 8.0
        val secondsRemaining = if (bytesPerSec > 0) (availableBytes / bytesPerSec).toLong() else 0L

        val timeStr = when {
            secondsRemaining <= 0 -> "0 Min"
            secondsRemaining < 3600 -> {
                val m = kotlin.math.round(secondsRemaining / 60.0).toInt()
                "${m} Min"
            }
            else -> {
                val h = kotlin.math.round(secondsRemaining / 3600.0).toInt()
                "${h} Hr"
            }
        }

        return Pair(timeStr, formattedStorage)
    }

    // --- Camera Callbacks ---

    val previewStatsCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            val iso = result.get(TotalCaptureResult.SENSOR_SENSITIVITY)
            currentIso = iso ?: 0
            val expTime = result.get(TotalCaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L
            if (expTime > 0) currentShutterSpeed = 1_000_000_000.0 / expTime

            val focusDistance = result.get(TotalCaptureResult.LENS_FOCUS_DISTANCE)
            val cctTemperature = if (android.os.Build.VERSION.SDK_INT >= 36) {
                result.get(TotalCaptureResult.COLOR_CORRECTION_COLOR_TEMPERATURE)
            } else {
                null
            }
            val cctTint = if (android.os.Build.VERSION.SDK_INT >= 36) {
                result.get(TotalCaptureResult.COLOR_CORRECTION_COLOR_TINT)
            } else {
                null
            }

            val nowElapsedNanos = SystemClock.elapsedRealtimeNanos()
            if (nowElapsedNanos - lastTelemetryEmissionElapsedNanos >= TELEMETRY_INTERVAL_NANOS) {
                val telemetry = CameraTelemetry(
                    iso = iso,
                    shutterNanos = expTime.takeIf { it > 0L },
                    focusDistanceDiopters = focusDistance,
                    cctTemperatureKelvin = cctTemperature,
                    cctTint = cctTint
                )
                if (_cameraTelemetry.value != telemetry) {
                    _cameraTelemetry.value = telemetry
                }
                lastTelemetryEmissionElapsedNanos = nowElapsedNanos
            }
        }
    }

    @Volatile private var videoFrameCount = 0
    @Volatile private var lastVideoTimestampNanos = -1L

    val videoStatsCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            if (lastVideoTimestampNanos < 0L) return
            val frameTimestamp = result.get(TotalCaptureResult.SENSOR_TIMESTAMP) ?: return
            
            if (lastVideoTimestampNanos == 0L) {
                lastVideoTimestampNanos = frameTimestamp
                return
            }

            videoFrameCount++
            val duration = frameTimestamp - lastVideoTimestampNanos
            
            if (duration >= 1_000_000_000L) {
                val sample = FrameRateEstimator.sample(videoFrameCount, duration, targetFps)
                currentFps = sample.effectiveFps
                sample.droppedFrames?.let(totalDropped::addAndGet)

                videoFrameCount = 0
                lastVideoTimestampNanos = frameTimestamp
            }
        }
    }

    fun startFpsCalculation(targetFps: Int?) {
        this.targetFps = targetFps
        videoFrameCount = 0
        totalDropped.set(0)
        currentFps = 0
        lastVideoTimestampNanos = 0L
    }

    fun stopFpsCalculation() {
        lastVideoTimestampNanos = -1L
        currentFps = 0
    }

    fun cleanup() {
        thermalManager.cleanup()
        pollingJob?.cancel()
        scope.cancel()
    }

    private companion object {
        const val TELEMETRY_INTERVAL_NANOS = 50_000_000L
    }
}
