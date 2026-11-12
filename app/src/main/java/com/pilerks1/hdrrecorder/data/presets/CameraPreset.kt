package com.pilerks1.hdrrecorder.data.presets

import com.pilerks1.hdrrecorder.camera.camera2.Camera2Settings
import com.pilerks1.hdrrecorder.camera.camera2.GammaSettings
import com.pilerks1.hdrrecorder.camera.model.CameraControlValues
import com.pilerks1.hdrrecorder.camera.model.PreviewSettings
import com.pilerks1.hdrrecorder.camera.model.RecordingAspectRatio
import com.pilerks1.hdrrecorder.camera.model.Resolution
import com.pilerks1.hdrrecorder.camera.model.VideoEncoding

/** Persisted configuration only; excludes runtime and UI visibility. */
data class CameraPreset(
    val id: String,
    val name: String,
    val settings: PresetSettings,
    val saveRecordingOptions: Boolean = false,
    val saveStorageLocation: Boolean = false,
    val recording: PresetRecordingControls? = null,
    val storageUri: String? = null
)

data class PresetSettings(
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val audioBitrate: String = "",
    val videoBitDepth: Int = 10,
    val encoding: VideoEncoding = VideoEncoding.HLG,
    val bitrate: String = "",
    val isStabilizationEnabled: Boolean = true,
    val isForceDisplaySdrEnabled: Boolean = false,
    val isRespectDisplayCutoutsEnabled: Boolean = false,
    val isExtendManualRangesEnabled: Boolean = false,
    val isWaitToLockAeEnabled: Boolean = true,
    val gammaSettings: GammaSettings = GammaSettings(),
    val previewSettings: PreviewSettings = PreviewSettings(),
    val camera2Settings: Camera2Settings = Camera2Settings()
)

data class PresetRecordingControls(
    val selectedFps: Int = 30,
    val selectedResolution: Resolution = Resolution.FHD,
    val selectedAspectRatio: RecordingAspectRatio = RecordingAspectRatio.FOUR_THREE,
    val zoomRatio: Float = 1f,
    val controls: CameraControlValues = CameraControlValues()
)

/** The authoritative preset-name rule; returns a user-facing error, or null when valid. */
fun presetNameError(name: String, existingNames: List<String> = emptyList()): String? = when {
    name.isBlank() -> "Enter a preset name"
    existingNames.any { it.equals(name.trim(), ignoreCase = true) } -> "A preset with that name already exists"
    else -> null
}
