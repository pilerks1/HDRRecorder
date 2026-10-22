package com.pilerks1.hdrrecorder.model

/**
 * Latest-known measurements from every telemetry producer.
 *
 * The root is replaced atomically. Each nested sample retains its own timestamp because the
 * sources intentionally update at different rates.
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
    val frameNumber: Long = -1L,
    val capturedAtElapsedNanos: Long = 0L,
    val sensorSensitivityIso: Int? = null,
    val postRawSensitivityBoost: Int? = null,
    val shutterNanos: Long? = null,
    val frameDurationNanos: Long? = null,
    val focusDistanceDiopters: Float? = null,
    val cctTemperatureKelvin: Int? = null,
    val cctTint: Int? = null,
    val aeState: Int? = null,
    val aeLocked: Boolean? = null
) {
    fun asCameraTelemetry(): CameraTelemetry = CameraTelemetry(
        iso = sensorSensitivityIso,
        postRawSensitivityBoost = postRawSensitivityBoost,
        shutterNanos = shutterNanos,
        frameDurationNanos = frameDurationNanos,
        focusDistanceDiopters = focusDistanceDiopters,
        cctTemperatureKelvin = cctTemperatureKelvin,
        cctTint = cctTint
    )


}

data class RecordingSample(
    val capturedAtElapsedNanos: Long = 0L,
    val isRecording: Boolean = false,
    val targetFps: Int? = null,
    val targetBitrateMbps: Int = 30,
    val recordedBytes: Long = 0L,
    val hardwareDurationNanos: Long = 0L,
    val actualBitrateMbps: Double = 0.0,
    val effectiveFps: Int = 0,
    val droppedFrames: Int = 0
)

data class ThermalPowerSample(
    val capturedAtElapsedNanos: Long = 0L,
    val thermalStatus: ThermalStatus = ThermalStatus.NONE,
    val thermalForecast: ThermalForecast =
        ThermalForecast.Status(ThermalStatus.NONE),
    val netPowerWatts: Double = 0.0
)

data class StorageSample(
    val capturedAtElapsedNanos: Long = 0L,
    val storageGeneration: Long = 0L,
    val uri: String? = null,
    val baselineAvailableBytes: Long = 0L
)
