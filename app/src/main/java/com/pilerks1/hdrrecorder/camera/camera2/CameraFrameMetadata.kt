package com.pilerks1.hdrrecorder.camera.camera2

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
    val frameNumber: Long get() = result.frameNumber
    val sensorTimestampNanos: Long? get() = result[CaptureResult.SENSOR_TIMESTAMP]
    operator fun <T> get(key: CaptureResult.Key<T>): T? = result[key]
    fun <T> requested(key: CaptureRequest.Key<T>): T? = result.request[key]
}

/** Latest repeating-request frame with lifecycle invalidation and frame-order deduplication. */
class CameraResultReader {
    private var generation = 0L
    private var active = false
    private var lastFrameNumber = -1L
    private val _latest = MutableStateFlow<CameraFrameMetadata?>(null)
    val latest = _latest.asStateFlow()

    @Synchronized fun begin(): Long {
        generation++
        active = true
        lastFrameNumber = -1L
        _latest.value = null
        return generation
    }

    @Synchronized fun end(expectedGeneration: Long) {
        if (generation != expectedGeneration) return
        active = false
        _latest.value = null
    }

    @Synchronized fun accept(frame: CameraFrameMetadata): Boolean {
        if (!active || frame.generation != generation || frame.frameNumber <= lastFrameNumber) return false
        lastFrameNumber = frame.frameNumber
        _latest.value = frame
        return true
    }
}
