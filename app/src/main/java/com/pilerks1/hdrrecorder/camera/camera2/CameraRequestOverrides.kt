package com.pilerks1.hdrrecorder.camera.camera2

import android.hardware.camera2.CaptureRequest

/**
 * Complete application-owned overrides, not a complete camera request.
 *
 * Submitted values are immutable Camera2 parameter types (boxed scalars, Range, and cached
 * TonemapCurve instances, whose equals compares curve points), so no copying is required.
 */
class CameraRequestOverrides private constructor(
    private val values: Map<CaptureRequest.Key<*>, Any>
) {
    val keys: Set<CaptureRequest.Key<*>> get() = values.keys

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: CaptureRequest.Key<T>): T? = values[key] as T?

    fun changedKeys(previous: CameraRequestOverrides?): Set<CaptureRequest.Key<*>> =
        keys.filterTo(linkedSetOf()) { previous == null || values[it] != previous.values[it] }

    fun forEach(action: (CaptureRequest.Key<Any>, Any) -> Unit) {
        values.forEach { (key, value) ->
            @Suppress("UNCHECKED_CAST")
            action(key as CaptureRequest.Key<Any>, value)
        }
    }

    /** Returns a copy with one additional or replaced value. */
    fun <T : Any> plus(key: CaptureRequest.Key<T>, value: T): CameraRequestOverrides =
        CameraRequestOverrides(values + (key to value))

    class Builder {
        private val values = linkedMapOf<CaptureRequest.Key<*>, Any>()

        fun <T : Any> set(key: CaptureRequest.Key<T>, value: T): Builder {
            values[key] = value
            return this
        }

        fun build(): CameraRequestOverrides = CameraRequestOverrides(values.toMap())
    }
}

/** Expected when a camera closes, rebinds, or replaces an outstanding operation. */
class CameraRequestSupersededException(cause: Throwable? = null) : Exception(null, cause)
