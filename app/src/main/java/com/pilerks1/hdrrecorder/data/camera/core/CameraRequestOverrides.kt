package com.pilerks1.hdrrecorder.data.camera.core

import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.TonemapCurve

/** Complete application-owned overrides, not a complete camera request. */
class CameraRequestOverrides private constructor(
    private val values: Map<CaptureRequest.Key<*>, Any>
) {
    val keys: Set<CaptureRequest.Key<*>> get() = values.keys.toSet()

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: CaptureRequest.Key<T>): T? = copyValue(values[key]) as T?

    fun changedKeys(previous: CameraRequestOverrides?): Set<CaptureRequest.Key<*>> =
        keys.filterTo(linkedSetOf()) { previous == null || !sameValue(values[it], previous.values[it]) }

    fun forEach(action: (CaptureRequest.Key<Any>, Any) -> Unit) {
        values.forEach { (key, value) ->
            @Suppress("UNCHECKED_CAST")
            action(key as CaptureRequest.Key<Any>, checkNotNull(copyValue(value)))
        }
    }

    class Builder {
        private val values = linkedMapOf<CaptureRequest.Key<*>, Any>()
        private val owners = mutableMapOf<CaptureRequest.Key<*>, String>()

        fun <T : Any> set(key: CaptureRequest.Key<T>, value: T, owner: String = "application"): Builder {
            require(owners[key] == null || owners[key] == owner) { "Conflicting owners for ${key.name}" }
            owners[key] = owner
            values[key] = checkNotNull(copyValue(value))
            return this
        }

        fun remove(key: CaptureRequest.Key<*>): Builder {
            values.remove(key)
            owners.remove(key)
            return this
        }

        fun add(overrides: CameraRequestOverrides, owner: String): Builder {
            overrides.forEach { key, value -> set(key, value, owner) }
            return this
        }

        fun build(): CameraRequestOverrides = CameraRequestOverrides(values.mapValues { checkNotNull(copyValue(it.value)) })
    }

    companion object {
        val EMPTY = Builder().build()

        private fun sameValue(left: Any?, right: Any?): Boolean = when {
            left is TonemapCurve && right is TonemapCurve -> (0..2).all { channel ->
                val count = left.getPointCount(channel)
                count == right.getPointCount(channel) && (0 until count).all { point ->
                    left.getPoint(channel, point) == right.getPoint(channel, point)
                }
            }
            else -> arrayOf(left).contentDeepEquals(arrayOf(right))
        }

        private fun copyValue(value: Any?): Any? = when (value) {
            is IntArray -> value.copyOf()
            is LongArray -> value.copyOf()
            is FloatArray -> value.copyOf()
            is DoubleArray -> value.copyOf()
            is ByteArray -> value.copyOf()
            is ShortArray -> value.copyOf()
            is BooleanArray -> value.copyOf()
            is CharArray -> value.copyOf()
            is Array<*> -> {
                @Suppress("UNCHECKED_CAST")
                val copy = value.copyOf() as Array<Any?>
                copy.indices.forEach { copy[it] = copyValue(copy[it]) }
                copy
            }
            is android.graphics.Rect -> android.graphics.Rect(value)
            is android.location.Location -> android.location.Location(value)
            else -> value // Camera2 parameter value objects (Range, TonemapCurve, etc.) are immutable.
        }
    }
}
