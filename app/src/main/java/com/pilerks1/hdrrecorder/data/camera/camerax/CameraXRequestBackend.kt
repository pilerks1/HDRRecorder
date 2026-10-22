package com.pilerks1.hdrrecorder.data.camera.camerax

import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.applyCamera2Interop
import androidx.camera.core.CameraControl
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPoint
import com.pilerks1.hdrrecorder.data.camera.core.CameraRequestBackend
import com.pilerks1.hdrrecorder.data.camera.core.CameraRequestOverrides
import com.pilerks1.hdrrecorder.data.camera.core.CameraRequestSupersededException
import kotlinx.coroutines.guava.await
import java.util.concurrent.atomic.AtomicBoolean

/** Only this adapter writes CameraX interop. Its caller serializes all operations. */
class CameraXRequestBackend(
    override val cameraId: String,
    override val generation: Long,
    private val control: CameraControl
) : CameraRequestBackend {
    private val active = AtomicBoolean(true)
    private var successful: CameraRequestOverrides? = null
    private val ownedKeys = mutableSetOf<CaptureRequest.Key<*>>()
    private var exposureCompensation: Int? = null
    private var lowLightBoost: Boolean? = null

    override suspend fun apply(overrides: CameraRequestOverrides) {
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

    suspend fun setExposureCompensation(index: Int) {
        ensureActive()
        if (exposureCompensation == index) return
        try {
            control.setExposureCompensationIndex(index).await()
            ensureActive()
            exposureCompensation = index
        } catch (error: Exception) {
            exposureCompensation = null
            if (error is CameraControl.OperationCanceledException) throw CameraRequestSupersededException(error)
            throw error
        }
    }

    suspend fun setLowLightBoost(enabled: Boolean) {
        ensureActive()
        if (lowLightBoost == enabled) return
        try {
            control.enableLowLightBoostAsync(enabled).await()
            ensureActive()
            lowLightBoost = enabled
        } catch (error: Exception) {
            lowLightBoost = null
            if (error is CameraControl.OperationCanceledException) throw CameraRequestSupersededException(error)
            throw error
        }
    }

    suspend fun tapToMeter(point: MeteringPoint) {
        ensureActive()
        try {
            control.startFocusAndMetering(FocusMeteringAction.Builder(
                point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
            ).build()).await()
            ensureActive()
        } catch (error: CameraControl.OperationCanceledException) {
            throw CameraRequestSupersededException(error)
        }
    }

    private fun ensureActive() {
        if (!active.get()) throw CameraRequestSupersededException()
    }

    override fun detach() { active.set(false) }
}
