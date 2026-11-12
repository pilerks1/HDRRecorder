package com.pilerks1.hdrrecorder.camera.camerax

import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.applyCamera2Interop
import androidx.camera.core.CameraControl
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPoint
import com.pilerks1.hdrrecorder.camera.camera2.CameraRequestOverrides
import com.pilerks1.hdrrecorder.camera.camera2.CameraRequestSupersededException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.guava.await

/** Only this adapter writes CameraX interop. Its caller serializes all operations. */
class CameraXRequestBackend(
    val generation: Long,
    private val control: CameraControl
) {
    private val active = AtomicBoolean(true)
    private var successful: CameraRequestOverrides? = null
    private val ownedKeys = mutableSetOf<CaptureRequest.Key<*>>()

    suspend fun apply(overrides: CameraRequestOverrides) {
        ensureActive()
        val changed = overrides.changedKeys(successful)
        val removed = ownedKeys - overrides.keys
        if (changed.isEmpty() && removed.isEmpty()) return
        ownedKeys.addAll(overrides.keys)
        try {
            control.applyCamera2Interop {
                removed.forEach { clearCaptureRequestOption(it) }
                overrides.forEach { key, value ->
                    if (key in changed) setCaptureRequestOption(key, value)
                }
            }
            ensureActive()
            successful = overrides
            ownedKeys.retainAll(overrides.keys)
        } catch (error: Exception) {
            successful = null // Reconcile all possibly submitted keys on the next explicit operation.
            if (error is CameraControl.OperationCanceledException) throw CameraRequestSupersededException(error)
            throw error
        }
    }

    suspend fun setLowLightBoost(enabled: Boolean) {
        ensureActive()
        try {
            control.enableLowLightBoostAsync(enabled).await()
            ensureActive()
        } catch (error: Exception) {
            if (error is CameraControl.OperationCanceledException) throw CameraRequestSupersededException(error)
            throw error
        }
    }

    suspend fun applyMetering(point: MeteringPoint, flags: Int) {
        ensureActive()
        try {
            control.startFocusAndMetering(FocusMeteringAction.Builder(point, flags)
                .setLockingMode(0)
                .disableAutoCancel()
                .build()).await()
            ensureActive()
        } catch (error: CameraControl.OperationCanceledException) {
            throw CameraRequestSupersededException(error)
        }
    }

    suspend fun cancelMetering() {
        ensureActive()
        try {
            control.cancelFocusAndMetering().await()
            ensureActive()
        } catch (error: CameraControl.OperationCanceledException) {
            throw CameraRequestSupersededException(error)
        }
    }

    fun setZoomRatio(ratio: Float): com.google.common.util.concurrent.ListenableFuture<Void> {
        ensureActive()
        return control.setZoomRatio(ratio)
    }

    private fun ensureActive() {
        if (!active.get()) throw CameraRequestSupersededException()
    }

    fun detach() {
        if (active.getAndSet(false)) {
            // Submit cleanup to this camera before its lifecycle closes or a replacement binds.
            control.cancelFocusAndMetering()
        }
    }
}
