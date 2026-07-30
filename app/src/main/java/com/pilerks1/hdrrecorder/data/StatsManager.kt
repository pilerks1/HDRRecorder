package com.pilerks1.hdrrecorder.data

import android.content.Context
import android.os.StatFs
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.pilerks1.hdrrecorder.model.StatsSnapshot
import com.pilerks1.hdrrecorder.model.StorageSample
import com.pilerks1.hdrrecorder.model.TelemetrySnapshot
import com.pilerks1.hdrrecorder.model.ThermalPowerSample
import com.pilerks1.hdrrecorder.model.formatDataSize
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
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
    private val context: Context,
    private val telemetryStore: TelemetryStore
) {
    private val thermalManager = ThermalManager(context)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val storageGeneration = AtomicLong(0L)
    private val telemetryActive = AtomicBoolean(false)
    private var powerPollingJob: Job? = null
    private var headroomPollingJob: Job? = null
    private var idleStoragePollingJob: Job? = null
    private var storageRefreshJob: Job? = null

    init {
        startPowerPolling()
        startHeadroomPolling()
        startIdleStoragePolling()
        requestStorageRefresh()
    }

    fun updateStorageUri(uri: String?) {
        val generation = storageGeneration.incrementAndGet()
        telemetryStore.updateStorage {
            it.copy(storageGeneration = generation, uri = uri)
        }
        requestStorageRefresh(generation, uri)
    }

    fun setTelemetryActive(active: Boolean) {
        telemetryActive.set(active)
    }

    fun setRecordingState(
        recording: Boolean,
        targetFps: Int?,
        targetBitrate: Int,
        storageUri: String? = null
    ) {
        telemetryStore.updateRecording { current ->
            current.copy(
                capturedAtElapsedNanos = SystemClock.elapsedRealtimeNanos(),
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

        val currentStorage = telemetryStore.snapshot().storage
        if (storageUri != currentStorage.uri) {
            updateStorageUri(storageUri)
        } else {
            requestStorageRefresh()
        }
    }

    /** Called directly from CameraX's recording-status callback. */
    fun updateHardwareStats(bytes: Long, durationNanos: Long, bitrate: Double) {
        telemetryStore.updateRecording {
            it.copy(
                capturedAtElapsedNanos = SystemClock.elapsedRealtimeNanos(),
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
            previous.storageRemainingFormatted == "N/A"
        val availableBytes = if (recording.isRecording) {
            (telemetry.storage.baselineAvailableBytes - recording.recordedBytes)
                .coerceAtLeast(0L)
        } else {
            telemetry.storage.baselineAvailableBytes
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

    private fun startPowerPolling() {
        powerPollingJob = scope.launch {
            while (isActive) {
                if (telemetryActive.get()) {
                    val state = thermalManager.getInstantThermalState()
                    telemetryStore.updateThermalPower(
                        ThermalPowerSample(
                            capturedAtElapsedNanos = SystemClock.elapsedRealtimeNanos(),
                            thermalStatus = state.thermalStatus,
                            thermalForecast = state.thermalForecast,
                            netPowerWatts = state.netPowerWatts
                        )
                    )
                }
                delay(POWER_INTERVAL_MS)
            }
        }
    }

    private fun startHeadroomPolling() {
        headroomPollingJob = scope.launch {
            while (isActive) {
                if (telemetryActive.get()) {
                    thermalManager.updateHeadroom()
                }
                delay(HEADROOM_INTERVAL_MS)
            }
        }
    }

    private fun startIdleStoragePolling() {
        idleStoragePollingJob = scope.launch {
            while (isActive) {
                delay(IDLE_STORAGE_INTERVAL_MS)
                if (!telemetryStore.snapshot().recording.isRecording) {
                    requestStorageRefresh()
                }
            }
        }
    }

    private fun requestStorageRefresh() {
        val current = telemetryStore.snapshot().storage
        val generation = storageGeneration.incrementAndGet()
        telemetryStore.updateStorage {
            it.copy(storageGeneration = generation, uri = current.uri)
        }
        requestStorageRefresh(generation, current.uri)
    }

    private fun requestStorageRefresh(generation: Long, uri: String?) {
        storageRefreshJob?.cancel()
        storageRefreshJob = scope.launch(Dispatchers.IO) {
            val availableBytes = getPhysicalAvailableBytes(uri)
            if (storageGeneration.get() != generation) return@launch
            telemetryStore.updateStorage { current ->
                if (current.storageGeneration != generation || current.uri != uri) {
                    current
                } else {
                    StorageSample(
                        capturedAtElapsedNanos = SystemClock.elapsedRealtimeNanos(),
                        storageGeneration = generation,
                        uri = uri,
                        baselineAvailableBytes = availableBytes
                    )
                }
            }
        }
    }

    private fun getPhysicalAvailableBytes(uriString: String?): Long {
        if (uriString == null) return getInternalAvailableBytes()
        return try {
            val uri = uriString.toUri()
            when (uri.scheme) {
                "file" -> {
                    val stat = StatFs(uri.path)
                    stat.availableBlocksLong * stat.blockSizeLong
                }
                "content" -> {
                    val rootId = DocumentsContract.getTreeDocumentId(uri).split(":")[0]
                    val rootUri = DocumentsContract.buildRootUri(uri.authority, rootId)
                    context.contentResolver.query(
                        rootUri,
                        arrayOf(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getLong(0)
                        else getInternalAvailableBytes()
                    } ?: getInternalAvailableBytes()
                }
                else -> getInternalAvailableBytes()
            }
        } catch (_: Exception) {
            getInternalAvailableBytes()
        }
    }

    private fun getInternalAvailableBytes(): Long = try {
        val stat = StatFs(context.filesDir.absolutePath)
        stat.availableBlocksLong * stat.blockSizeLong
    } catch (_: Exception) {
        0L
    }

    private fun formatStorageInfo(
        availableBytes: Long,
        currentBitrate: Double,
        targetBitrate: Int,
        isRecording: Boolean
    ): Pair<String, String> {
        val bitrateToUse =
            if (isRecording && currentBitrate > 0.0) currentBitrate else targetBitrate.toDouble()
        val bytesPerSecond = bitrateToUse * 1_000_000.0 / 8.0
        val secondsRemaining =
            if (bytesPerSecond > 0.0) (availableBytes / bytesPerSecond).toLong() else 0L
        val time = when {
            secondsRemaining <= 0L -> "0 Min"
            secondsRemaining < 3_600L ->
                "${round(secondsRemaining / 60.0).toInt()} Min"
            else -> "${round(secondsRemaining / 3_600.0).toInt()} Hr"
        }
        return time to formatDataSize(availableBytes)
    }

    fun cleanup() {
        powerPollingJob?.cancel()
        headroomPollingJob?.cancel()
        idleStoragePollingJob?.cancel()
        storageRefreshJob?.cancel()
        thermalManager.cleanup()
        scope.cancel()
    }

    private fun normalizePower(value: Double): Double = round(value * 100.0) / 100.0

    private companion object {
        const val POWER_INTERVAL_MS = 500L
        const val HEADROOM_INTERVAL_MS = 10_000L
        const val IDLE_STORAGE_INTERVAL_MS = 120_000L
        const val SLOW_DISPLAY_TICKS = 40L
        const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
