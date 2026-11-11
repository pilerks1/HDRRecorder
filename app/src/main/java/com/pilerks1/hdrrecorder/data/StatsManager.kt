package com.pilerks1.hdrrecorder.data

import android.content.Context
import com.pilerks1.hdrrecorder.data.diagnostics.ErrorReporter
import com.pilerks1.hdrrecorder.data.diagnostics.IssueCode
import kotlinx.coroutines.CancellationException
import com.pilerks1.hdrrecorder.data.storage.RecordingDestination
import com.pilerks1.hdrrecorder.data.storage.RecordingStorageManager
import com.pilerks1.hdrrecorder.model.NANOS_PER_SECOND
import com.pilerks1.hdrrecorder.model.StatsSnapshot
import com.pilerks1.hdrrecorder.model.StorageSample
import com.pilerks1.hdrrecorder.model.TelemetrySnapshot
import com.pilerks1.hdrrecorder.model.formatDataSize
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.round
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Acquires slow non-camera telemetry and projects cached values into StatsUI snapshots.
 *
 * The presentation coordinator calls [buildSnapshot] from its 2 Hz cadence. This class never
 * performs storage, thermal-headroom, or battery acquisition on that presentation path.
 */
class StatsManager(
    context: Context,
    private val telemetryStore: TelemetryStore,
    private val recordingStorage: RecordingStorageManager,
    private val reporter: ErrorReporter
) {
    private val thermalManager = ThermalManager(context, reporter)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val storageGeneration = AtomicLong(0L)
    // Thermal and power polling only run while a camera generation is active.
    private var thermalPollingJob: Job? = null

    init {
        scope.launch {
            while (isActive) {
                delay(IDLE_STORAGE_INTERVAL_MS)
                if (!telemetryStore.snapshot().recording.isRecording) {
                    refreshStorage(telemetryStore.snapshot().storage.uri)
                }
            }
        }
        refreshStorage(null)
    }

    fun updateStorageUri(uri: String?) = refreshStorage(uri)

    @Synchronized
    fun setTelemetryActive(active: Boolean) {
        thermalPollingJob?.cancel()
        thermalPollingJob = if (active) startThermalPolling() else null
    }

    fun setRecordingState(
        recording: Boolean,
        targetFps: Int?,
        targetBitrate: Int,
        storageUri: String? = null
    ) {
        telemetryStore.updateRecording { current ->
            current.copy(
                isRecording = recording,
                targetFps = targetFps,
                targetBitrateMbps = targetBitrate,
                recordedBytes = if (recording) 0L else current.recordedBytes,
                hardwareDurationNanos = if (recording) 0L else current.hardwareDurationNanos,
                actualBitrateMbps = 0.0,
                effectiveFps = 0,
                droppedFrames = if (recording) 0 else current.droppedFrames
            )
        }
        refreshStorage(storageUri)
    }

    /** Called directly from CameraX's recording-status callback. */
    fun updateHardwareStats(bytes: Long, durationNanos: Long, bitrate: Double) {
        telemetryStore.updateRecording {
            it.copy(
                recordedBytes = bytes,
                hardwareDurationNanos = durationNanos,
                actualBitrateMbps = bitrate
            )
        }
    }

    fun buildSnapshot(
        telemetry: TelemetrySnapshot,
        previous: StatsSnapshot,
        statsTick: Long
    ): StatsSnapshot {
        val camera = telemetry.camera
        val recording = telemetry.recording
        val thermalPower = telemetry.thermalPower
        val effectiveIso = camera.sensorSensitivityIso?.let { sensorIso ->
            camera.postRawSensitivityBoost?.let { boost ->
                ((sensorIso.toLong() * boost.toLong() + 50L) / 100L)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
            } ?: sensorIso
        } ?: 0
        val shutterSpeed = camera.shutterNanos
            ?.takeIf { it > 0L }
            ?.let { NANOS_PER_SECOND.toDouble() / it }
            ?: 0.0

        val refreshSlowDisplay = !recording.isRecording ||
            statsTick % SLOW_DISPLAY_TICKS == 0L ||
            previous.storageRemainingFormatted == "N/A" || telemetry.storage.baselineAvailableBytes == null
        val availableBytes = telemetry.storage.baselineAvailableBytes?.let { baseline ->
            if (recording.isRecording) (baseline - recording.recordedBytes).coerceAtLeast(0L)
            else baseline
        }
        val storageData = if (refreshSlowDisplay) {
            formatStorageInfo(
                availableBytes = availableBytes,
                currentBitrate = recording.actualBitrateMbps,
                targetBitrate = recording.targetBitrateMbps,
                isRecording = recording.isRecording
            )
        } else {
            previous.storageRemainingTime to previous.storageRemainingFormatted
        }
        val displayedSize = if (refreshSlowDisplay) {
            recording.recordedBytes
        } else {
            previous.displayedFileSizeWrittenBytes
        }

        return StatsSnapshot(
            iso = effectiveIso,
            shutterSpeed = shutterSpeed,
            effectiveFps = recording.effectiveFps,
            droppedFrames = recording.droppedFrames,
            canMeasureDroppedFrames = recording.targetFps != null,
            thermalStatus = thermalPower.thermalStatus,
            thermalForecast = thermalPower.thermalForecast,
            netPowerWatts = normalizePower(thermalPower.netPowerWatts),
            storageRemainingFormatted = storageData.second,
            storageRemainingTime = storageData.first,
            actualBitrateMbps = recording.actualBitrateMbps,
            displayedFileSizeWrittenBytes = displayedSize
        )
    }

    private fun startThermalPolling(): Job = scope.launch {
        var sinceHeadroom = HEADROOM_INTERVAL_MS
        while (isActive) {
            if (sinceHeadroom >= HEADROOM_INTERVAL_MS) {
                thermalManager.updateHeadroom()
                sinceHeadroom = 0L
            }
            try {
                telemetryStore.updateThermalPower(thermalManager.getInstantThermalState())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Keep the last measured sample; a transient read must not kill polling.
                reporter.report(IssueCode.TELEMETRY, "powerSample", error, notify = false)
            }
            delay(POWER_INTERVAL_MS)
            sinceHeadroom += POWER_INTERVAL_MS
        }
    }

    /**
     * Provider queries cannot be cancelled, so a late result is rejected by generation alone:
     * only the most recent request may replace the baseline.
     */
    private fun refreshStorage(uri: String?) {
        val generation = storageGeneration.incrementAndGet()
        telemetryStore.updateStorage { StorageSample(storageGeneration = generation, uri = uri) }
        scope.launch(Dispatchers.IO) {
            val availableBytes = recordingStorage.availableBytes(RecordingDestination.fromStorageUri(uri))
            telemetryStore.updateStorage { current ->
                if (current.storageGeneration != generation) current
                else current.copy(baselineAvailableBytes = availableBytes)
            }
        }
    }

    private fun formatStorageInfo(
        availableBytes: Long?,
        currentBitrate: Double,
        targetBitrate: Int,
        isRecording: Boolean
    ): Pair<String, String> {
        if (availableBytes == null) return "N/A" to "N/A"
        val bitrateToUse =
            if (isRecording && currentBitrate > 0.0) currentBitrate else targetBitrate.toDouble()
        val bytesPerSecond = bitrateToUse * 1_000_000.0 / 8.0
        val secondsRemaining =
            if (bytesPerSecond > 0.0) (availableBytes / bytesPerSecond).toLong() else 0L
        val time = when {
            bitrateToUse <= 0.0 -> "N/A"
            secondsRemaining <= 0L -> "0 Min"
            secondsRemaining < 3_600L ->
                "${round(secondsRemaining / 60.0).toInt()} Min"
            else -> "${round(secondsRemaining / 3_600.0).toInt()} Hr"
        }
        return time to formatDataSize(availableBytes)
    }

    fun cleanup() {
        try {
            thermalManager.cleanup()
        } catch (error: Exception) {
            reporter.report(IssueCode.TELEMETRY, "thermalCleanup", error, notify = false)
        } finally {
            scope.cancel()
        }
    }

    private fun normalizePower(value: Double): Double = round(value * 100.0) / 100.0

    private companion object {
        const val POWER_INTERVAL_MS = 500L
        const val HEADROOM_INTERVAL_MS = 10_000L
        const val IDLE_STORAGE_INTERVAL_MS = 120_000L
        const val SLOW_DISPLAY_TICKS = 40L
    }
}
