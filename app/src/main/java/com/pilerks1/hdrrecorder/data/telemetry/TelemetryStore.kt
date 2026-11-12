package com.pilerks1.hdrrecorder.data.telemetry

import java.util.concurrent.atomic.AtomicReference

/**
 * One process-local bank for the latest telemetry values.
 *
 * Producers must update through the CAS helpers below. A get/copy/set sequence could overwrite a
 * concurrent producer, while these helpers preserve every successfully latched domain update.
 */
class TelemetryStore {
    private val bank = AtomicReference(TelemetrySnapshot())

    fun snapshot(): TelemetrySnapshot = bank.get()

    /** Generations are issued by [com.pilerks1.hdrrecorder.camera.camera2.CameraResultReader]. */
    fun beginCameraGeneration(generation: Long) {
        update { current ->
            current.copy(camera = CameraSample(cameraGeneration = generation), cameraGeneration = generation)
        }
    }

    fun clearCamera(generation: Long) {
        update { current ->
            if (current.cameraGeneration != generation) current
            else current.copy(camera = CameraSample(cameraGeneration = generation))
        }
    }

    fun updateCamera(sample: CameraSample) {
        update { current ->
            if (sample.cameraGeneration != current.cameraGeneration) current
            else current.copy(camera = sample)
        }
    }

    fun updateRecording(transform: (RecordingSample) -> RecordingSample) {
        update { current -> current.copy(recording = transform(current.recording)) }
    }

    fun updateThermalPower(sample: ThermalPowerSample) {
        update { current -> current.copy(thermalPower = sample) }
    }

    fun updateStorage(transform: (StorageSample) -> StorageSample) {
        update { current -> current.copy(storage = transform(current.storage)) }
    }

    private inline fun update(transform: (TelemetrySnapshot) -> TelemetrySnapshot) {
        while (true) {
            val current = bank.get()
            val updated = transform(current)
            if (updated === current || updated == current || bank.compareAndSet(current, updated)) {
                return
            }
        }
    }
}
