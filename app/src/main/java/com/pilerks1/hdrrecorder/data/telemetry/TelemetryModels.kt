package com.pilerks1.hdrrecorder.data.telemetry

import android.os.PowerManager

/**
 * The latest effective values reported by the active camera repeating request.
 *
 * These values are deliberately separate from persisted manual overrides: automatic controls
 * follow telemetry, while enabling manual mode snapshots the latest valid value as a hold.
 */
data class CameraTelemetry(
    val iso: Int? = null,
    val postRawSensitivityBoost: Int? = null,
    val shutterNanos: Long? = null,
    val focusDistanceDiopters: Float? = null,
    val cctTemperatureKelvin: Int? = null,
    val cctTint: Int? = null
)

/**
 * Represents a snapshot of all camera and hardware statistics at a single point in time.
 * Only fields actually rendered by the UI are kept.
 */
data class StatsSnapshot(
    val iso: Int = 0,
    val shutterSpeed: Double = 0.0,
    val effectiveFps: Int = 0,
    val droppedFrames: Int = 0,
    val canMeasureDroppedFrames: Boolean = false,

    // Thermal Stats
    val thermalStatus: ThermalStatus = ThermalStatus.NONE,
    val thermalForecast: ThermalForecast = ThermalForecast.Status(ThermalStatus.NONE),

    // Power Stats
    val netPowerWatts: Double = 0.0,

    // Storage & Recording Stats
    val storageRemainingFormatted: String = "N/A",
    val storageRemainingTime: String = "N/A",
    val actualBitrateMbps: Double = 0.0,
    val displayedFileSizeWrittenBytes: Long = 0L
)

/**
 * Latest-known measurements from every telemetry producer.
 *
 * The root is replaced atomically; each producer owns exactly one nested sample.
 */
data class TelemetrySnapshot(
    val camera: CameraSample = CameraSample(),
    val recording: RecordingSample = RecordingSample(),
    val thermalPower: ThermalPowerSample = ThermalPowerSample(),
    val storage: StorageSample = StorageSample(),
    val cameraGeneration: Long = 0L
)

data class CameraSample(
    val cameraGeneration: Long = 0L,
    val sensorSensitivityIso: Int? = null,
    val postRawSensitivityBoost: Int? = null,
    val shutterNanos: Long? = null,
    val focusDistanceDiopters: Float? = null,
    val cctTemperatureKelvin: Int? = null,
    val cctTint: Int? = null
) {
    fun asCameraTelemetry(): CameraTelemetry = CameraTelemetry(
        iso = sensorSensitivityIso,
        postRawSensitivityBoost = postRawSensitivityBoost,
        shutterNanos = shutterNanos,
        focusDistanceDiopters = focusDistanceDiopters,
        cctTemperatureKelvin = cctTemperatureKelvin,
        cctTint = cctTint
    )
}

data class RecordingSample(
    val isRecording: Boolean = false,
    val targetFps: Int? = null,
    val targetBitrateMbps: Int = 30,
    val recordedBytes: Long = 0L,
    val hardwareDurationNanos: Long = 0L,
    val actualBitrateMbps: Double = 0.0,
    val effectiveFps: Int = 0,
    val droppedFrames: Int = 0
)

/** Produced by [ThermalManager] and latched without conversion. */
data class ThermalPowerSample(
    val thermalStatus: ThermalStatus = ThermalStatus.NONE,
    val thermalForecast: ThermalForecast =
        ThermalForecast.Status(ThermalStatus.NONE),
    val netPowerWatts: Double = 0.0
)

data class StorageSample(
    val storageGeneration: Long = 0L,
    val uri: String? = null,
    val baselineAvailableBytes: Long? = null
)

/**
 * Single source of truth for thermal status vocabulary.
 * Maps PowerManager.THERMAL_STATUS_* ints to a stable enum so the string labels
 * and their UI colors can't silently drift apart across files.
 */
enum class ThermalStatus(val label: String) {
    NONE("NONE"),
    LIGHT("LIGHT"),
    MODERATE("MOD"),
    SEVERE("SEVERE"),
    CRITICAL("CRITICAL"),
    EMERGENCY("EMERGENCY"),
    SHUTDOWN("SHUTDOWN"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromInt(status: Int): ThermalStatus = when (status) {
            PowerManager.THERMAL_STATUS_NONE -> NONE
            PowerManager.THERMAL_STATUS_LIGHT -> LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL -> CRITICAL
            PowerManager.THERMAL_STATUS_EMERGENCY -> EMERGENCY
            PowerManager.THERMAL_STATUS_SHUTDOWN -> SHUTDOWN
            else -> UNKNOWN
        }
    }
}

/** A forecast is either an Android thermal status or the numeric headroom supplied by the device. */
sealed interface ThermalForecast {
    data class Status(val status: ThermalStatus) : ThermalForecast
    data class Headroom(val value: Float) : ThermalForecast
}
