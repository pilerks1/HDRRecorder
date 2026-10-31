package com.pilerks1.hdrrecorder.model

import android.hardware.camera2.CaptureRequest

enum class GammaMode(val camera2Value: Int, val displayName: String) {
    CONTRAST_CURVE(CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE, "Contrast Curve"),
    FAST(CaptureRequest.TONEMAP_MODE_FAST, "Fast"),
    HIGH_QUALITY(CaptureRequest.TONEMAP_MODE_HIGH_QUALITY, "High Quality"),
    GAMMA_VALUE(CaptureRequest.TONEMAP_MODE_GAMMA_VALUE, "Gamma Value"),
    PRESET_CURVE(CaptureRequest.TONEMAP_MODE_PRESET_CURVE, "Preset Curve")
}

enum class GammaPreset(val camera2Value: Int, val displayName: String) {
    SRGB(CaptureRequest.TONEMAP_PRESET_CURVE_SRGB, "sRGB"),
    REC709(CaptureRequest.TONEMAP_PRESET_CURVE_REC709, "Rec.709")
}

data class GammaSettings(
    val mode: GammaMode = GammaMode.HIGH_QUALITY,
    val value: Float = 2.2f,
    val curve: GammaCurve = GammaCurve.HLG,
    val preset: GammaPreset = GammaPreset.SRGB
) {
    init {
        require(value.isFinite() && value > 0f) { "Gamma must be a finite positive number" }
        require(curve != GammaCurve.AUTO) { "Select an explicit contrast curve" }
    }

    fun supportedMode(modes: List<GammaMode>): GammaMode? =
        mode.takeIf { it in modes }
            ?: GammaMode.HIGH_QUALITY.takeIf { it in modes }
            ?: GammaMode.FAST.takeIf { it in modes }
            ?: modes.firstOrNull()
}
