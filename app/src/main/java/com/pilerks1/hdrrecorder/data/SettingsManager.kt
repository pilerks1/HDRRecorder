package com.pilerks1.hdrrecorder.data

import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.TonemapCurve
import android.util.Log
import android.util.Range
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import com.pilerks1.hdrrecorder.model.tonemapPoints
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Manages all Camera2 Interop settings.
 * This class is responsible for configuring advanced camera features like
 * noise reduction, tonemap curves, focus, and frame rate. It ensures that all
 * settings are bundled together and applied in a single transaction to prevent
 * conflicts.
 */
@OptIn(ExperimentalCamera2Interop::class)
class SettingsManager {

    private val captureRequestOptionsBuilder = CaptureRequestOptions.Builder()

    init {
        // Default to having noise reduction on
        setNoiseReduction(true)
        // Default focus mode
        setFocusMode("Auto")
    }

    fun setNoiseReduction(enabled: Boolean) {
        if (enabled) {
            captureRequestOptionsBuilder
                .setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                .setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
        } else {
            captureRequestOptionsBuilder
                .setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
        }
    }

    fun setTonemapMode(mode: String) {
        when (mode) {
            "Device" -> {
                captureRequestOptionsBuilder
                    .setCaptureRequestOption(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY)
                captureRequestOptionsBuilder.clearCaptureRequestOption(CaptureRequest.TONEMAP_CURVE)
            }
            "HLG" -> {
                val hlgCurve = createHlgTonemapCurve()
                captureRequestOptionsBuilder
                    .setCaptureRequestOption(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
                    .setCaptureRequestOption(CaptureRequest.TONEMAP_CURVE, hlgCurve)
            }
            "Custom" -> {
                val customCurve = TonemapCurve(tonemapPoints, tonemapPoints, tonemapPoints)
                captureRequestOptionsBuilder
                    .setCaptureRequestOption(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
                    .setCaptureRequestOption(CaptureRequest.TONEMAP_CURVE, customCurve)
            }
        }
    }

    fun setFocusMode(focusMode: String) {
        when (focusMode) {
            "Auto" -> {
                captureRequestOptionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                )
                captureRequestOptionsBuilder.clearCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE)
            }
            "Manual" -> {
                captureRequestOptionsBuilder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_OFF
                ).setCaptureRequestOption(
                    CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f
                )
            }
        }
    }

    fun setFrameRate(fps: Int) {
        captureRequestOptionsBuilder.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
            Range(fps, fps)
        )
    }

    /**
     * Applies all configured settings to the camera session at once.
     * This is the single point of contact for updating the camera's state,
     * ensuring all CaptureRequest options are bundled together.
     */
    fun applyAllSettings(cameraControl: CameraControl) {
        Camera2CameraControl.from(cameraControl).setCaptureRequestOptions(captureRequestOptionsBuilder.build())
        Log.d("SettingsManager", "All pending settings have been applied to the camera.")
    }

    private fun createHlgTonemapCurve(): TonemapCurve {
        val numPoints = 64
        val hlgPoints = FloatArray(numPoints * 2)

        for (i in 0 until numPoints) {
            val x = i.toFloat() / (numPoints - 1)
            val y = if (x <= 1.0f / 12.0f) {
                sqrt(3.0f * x)
            } else {
                val a = 0.17883277f
                val b = 0.28466892f
                val c = 0.55991073f
                (a * ln(12.0f * x - b) + c)
            }
            hlgPoints[i * 2] = x
            hlgPoints[i * 2 + 1] = y.coerceIn(0.0f, 1.0f)
        }
        return TonemapCurve(hlgPoints, hlgPoints, hlgPoints)
    }
}
