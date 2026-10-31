package com.pilerks1.hdrrecorder.data.camera.core

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest

/** Public Camera2 integer-mode controls exposed by settings. */
enum class Camera2Control(
    val requestKey: CaptureRequest.Key<Int>,
    val availableModesKey: CameraCharacteristics.Key<IntArray>
) {
    COLOR_CORRECTION_ABERRATION_MODE(
        CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
        CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES
    ),
    CONTROL_AF_MODE(CaptureRequest.CONTROL_AF_MODE, CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES),
    DISTORTION_CORRECTION_MODE(
        CaptureRequest.DISTORTION_CORRECTION_MODE,
        CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES
    ),
    EDGE_MODE(CaptureRequest.EDGE_MODE, CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES),
    HOT_PIXEL_MODE(CaptureRequest.HOT_PIXEL_MODE, CameraCharacteristics.HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES),
    NOISE_REDUCTION_MODE(
        CaptureRequest.NOISE_REDUCTION_MODE,
        CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES
    ),
    SHADING_MODE(CaptureRequest.SHADING_MODE, CameraCharacteristics.SHADING_AVAILABLE_MODES)
}

enum class Camera2CapabilitySource { MAIN_CAMERA, COMMON_REAR_CAMERAS }

data class Camera2ModeOverride(val enabled: Boolean = false, val mode: Int? = null)

data class Camera2Settings(
    val capabilitySource: Camera2CapabilitySource = Camera2CapabilitySource.MAIN_CAMERA,
    val overrides: Map<Camera2Control, Camera2ModeOverride> = emptyMap()
) {
    operator fun get(control: Camera2Control): Camera2ModeOverride = overrides[control] ?: Camera2ModeOverride()

    fun selectedMode(control: Camera2Control): Int? = get(control).takeIf { it.enabled }?.mode

    fun withOverride(control: Camera2Control, value: Camera2ModeOverride): Camera2Settings =
        copy(overrides = overrides + (control to value))

    /** Reconcile against the selected source, never against the currently active lens. */
    fun reconcile(capabilities: Camera2ModeCapabilities?): Camera2Settings {
        capabilities ?: return this // Preserve saved choices until discovery completes.
        val available = capabilities.modes(capabilitySource)
        return copy(overrides = Camera2Control.entries.associateWith { control ->
            val current = get(control)
            val modes = available[control].orEmpty()
            if (current.mode != null && current.mode in modes) current
            else Camera2ModeOverride(mode = modes.firstOrNull())
        })
    }
}

data class Camera2ModeCapabilities(
    val mainCameraModes: Map<Camera2Control, List<Int>>,
    val commonRearModes: Map<Camera2Control, List<Int>>
) {
    fun modes(source: Camera2CapabilitySource): Map<Camera2Control, List<Int>> = when (source) {
        Camera2CapabilitySource.MAIN_CAMERA -> mainCameraModes
        Camera2CapabilitySource.COMMON_REAR_CAMERAS -> commonRearModes
    }
}
