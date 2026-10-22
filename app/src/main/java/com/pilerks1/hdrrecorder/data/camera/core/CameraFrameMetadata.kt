package com.pilerks1.hdrrecorder.data.camera.core

import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CameraFrameMetadata(
    val generation: Long,
    private val result: TotalCaptureResult,
    val receivedAtElapsedNanos: Long = SystemClock.elapsedRealtimeNanos()
) {
    val cameraId: String get() = result.cameraId
    val frameNumber: Long get() = result.frameNumber
    val sensorTimestampNanos: Long? get() = result[CaptureResult.SENSOR_TIMESTAMP]
    val resultKeys: List<CaptureResult.Key<*>> get() = result.keys.toList()
    val requestKeys: List<CaptureRequest.Key<*>> get() = result.request.keys.toList()
    operator fun <T> get(key: CaptureResult.Key<T>): T? = result[key]
    fun <T> requested(key: CaptureRequest.Key<T>): T? = result.request[key]
    val physicalResults: Map<String, CameraFrameMetadata> get() =
        result.physicalCameraTotalResults.mapValues { CameraFrameMetadata(generation, it.value, receivedAtElapsedNanos) }
}

/** Bounded latest-frame bank with lifecycle invalidation and per-source deduplication. */
class CameraResultReader {
    private var generation = 0L
    private var active = false
    private val frames = linkedMapOf<String, CameraFrameMetadata>()
    private val _latest = MutableStateFlow<CameraFrameMetadata?>(null)
    val latest = _latest.asStateFlow()

    @Synchronized fun begin(): Long {
        generation++
        active = true
        frames.clear()
        _latest.value = null
        return generation
    }

    @Synchronized fun end(expectedGeneration: Long) {
        if (generation != expectedGeneration) return
        active = false
        frames.clear()
        _latest.value = null
    }

    @Synchronized fun accept(frame: CameraFrameMetadata): Boolean {
        if (!active || frame.generation != generation) return false
        if ((frames[frame.cameraId]?.frameNumber ?: -1L) >= frame.frameNumber) return false
        frames[frame.cameraId] = frame
        frame.physicalResults.forEach { (id, physical) -> frames[id] = physical }
        _latest.value = frame
        return true
    }

    @Synchronized fun snapshot(): Map<String, CameraFrameMetadata> = frames.toMap()
}
