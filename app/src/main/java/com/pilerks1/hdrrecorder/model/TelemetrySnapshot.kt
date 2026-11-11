package com.pilerks1.hdrrecorder.model

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

/** Produced by [com.pilerks1.hdrrecorder.data.ThermalManager] and latched without conversion. */
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
