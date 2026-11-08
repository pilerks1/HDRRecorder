package com.pilerks1.hdrrecorder.model

import android.util.Range
import com.pilerks1.hdrrecorder.data.camera.core.Camera2Settings

/** Persisted configuration only; excludes runtime and UI visibility. */
data class CameraPreset(
    val id: String,
    val name: String,
    val settings: PresetSettings,
    val saveRecordingOptions: Boolean = false,
    val saveStorageLocation: Boolean = false,
    val recording: PresetRecordingControls? = null,
    val storageUri: String? = null,
    val schemaVersion: Int = 1
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
    val isManualIso: Boolean = false,
    val isoValue: Int? = null,
    val isManualPostRawSensitivity: Boolean = false,
    val postRawSensitivityBoost: Int? = null,
    val isManualSs: Boolean = false,
    val ssValueNanos: Long? = null,
    val isManualEv: Boolean = false,
    val evValueIndex: Int = 0,
    val isLowLightBoostEnabled: Boolean = false,
    val isManualFocus: Boolean = false,
    val focusDistanceDiopters: Float? = null,
    val isManualWb: Boolean = false,
    val wbTemp: Int? = null,
    val wbTint: Int? = null,
    val isManualFps: Boolean = false,
    val fpsRange: Range<Int>? = null
)
