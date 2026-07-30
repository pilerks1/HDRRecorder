package com.pilerks1.hdrrecorder.data

import com.pilerks1.hdrrecorder.model.CameraSample
import com.pilerks1.hdrrecorder.model.RecordingSample
import com.pilerks1.hdrrecorder.model.StorageSample
import com.pilerks1.hdrrecorder.model.TelemetrySnapshot
import com.pilerks1.hdrrecorder.model.ThermalPowerSample
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

    fun beginCameraGeneration(): Long {
        while (true) {
            val current = bank.get()
            val generation = current.cameraGeneration + 1L
            val updated = current.copy(
                camera = CameraSample(cameraGeneration = generation),
                cameraGeneration = generation
            )
            if (bank.compareAndSet(current, updated)) return generation
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
