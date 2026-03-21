package com.pilerks1.hdrrecorder.data

import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.TonemapCurve
import android.util.Range
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraControl
import com.pilerks1.hdrrecorder.model.tonemapPoints
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Manages all Camera2 Interop settings.
 */
@OptIn(ExperimentalCamera2Interop::class)
class SettingsManager {

    private var gammaCurve: String = "Auto"
    private var frameRate: Int = 30
    private var focusMode: String = "Auto"
    private var isNoiseReductionEnabled: Boolean = true

    fun setGammaCurve(curve: String) {
        this.gammaCurve = curve
    }

    fun setFrameRate(fps: Int) {
        this.frameRate = fps
    }

    fun setFocusMode(mode: String) {
        this.focusMode = mode
    }

    fun setNoiseReduction(enabled: Boolean) {
        this.isNoiseReductionEnabled = enabled
    }

    fun applyAllSettings(cameraControl: CameraControl) {
        val camera2Control = Camera2CameraControl.from(cameraControl)
        val builder = CaptureRequestOptions.Builder()

        // Framerate
        builder.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
            Range(frameRate, frameRate)
        )

        // Focus
        when (focusMode) {
            "Auto" -> {
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                )
            }
            "Manual" -> {
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_OFF
                )
                builder.setCaptureRequestOption(
                    CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f
                )
            }
        }

        // Noise Reduction
        if (isNoiseReductionEnabled) {
            builder.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
            builder.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY)
        } else {
            builder.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF)
        }

        // Gamma Curve (Camera2Interop)
        when (gammaCurve) {
            "Auto" -> {
                // Auto means no interop manipulation, let hardware handle Tonemap
                builder.setCaptureRequestOption(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY)
            }
            "HLG" -> {
                builder.setCaptureRequestOption(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
                builder.setCaptureRequestOption(CaptureRequest.TONEMAP_CURVE, createHLGCurve())
            }
            "PQ" -> {
                builder.setCaptureRequestOption(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
                builder.setCaptureRequestOption(CaptureRequest.TONEMAP_CURVE, createPQCurve())
            }
            "Custom" -> {
                // Uses the LUT defined in com.pilerks1.hdrrecorder.model.gammaCurve.kt
                val customCurve = TonemapCurve(tonemapPoints, tonemapPoints, tonemapPoints)
                builder.setCaptureRequestOption(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
                builder.setCaptureRequestOption(CaptureRequest.TONEMAP_CURVE, customCurve)
            }
        }

        camera2Control.captureRequestOptions = builder.build()
    }

    /** Reference HLG Curve (OETF) */
    private fun createHLGCurve(): TonemapCurve {
        val numPoints = 64
        val hlgPoints = FloatArray(numPoints * 2)
        val a = 0.17883277f
        val b = 0.28466892f
        val c = 0.55991073f

        for (i in 0 until numPoints) {
            val x = i.toFloat() / (numPoints - 1)
            val y = if (x <= 1.0f / 12.0f) {
                sqrt(3.0f * x)
            } else {
                (a * ln(12.0f * x - b) + c)
            }
            hlgPoints[i * 2] = x
            hlgPoints[i * 2 + 1] = y.coerceIn(0.0f, 1.0f)
        }
        return TonemapCurve(hlgPoints, hlgPoints, hlgPoints)
    }

    /** Reference PQ Curve (SMPTE ST 2084 EOTF^-1) */
    private fun createPQCurve(): TonemapCurve {
        val numPoints = 64
        val points = FloatArray(numPoints * 2)
        val m1 = 0.1593017578125f
        val m2 = 78.84375f
        val c1 = 0.8359375f
        val c2 = 18.8515625f
        val c3 = 18.6875f

        for (i in 0 until numPoints) {
            val linear = i.toFloat() / (numPoints - 1)
            val num = c1 + c2 * linear.pow(m1)
            val den = 1.0f + c3 * linear.pow(m1)
            val nonlinear = (num / den).pow(m2)

            points[i * 2] = linear
            points[i * 2 + 1] = nonlinear.coerceIn(0.0f, 1.0f)
        }
        return TonemapCurve(points, points, points)
    }
}