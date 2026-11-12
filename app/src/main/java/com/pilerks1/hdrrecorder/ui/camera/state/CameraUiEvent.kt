package com.pilerks1.hdrrecorder.ui.camera.state

import android.util.Range
import androidx.camera.core.MeteringPoint
import com.pilerks1.hdrrecorder.camera.camera2.Camera2Control
import com.pilerks1.hdrrecorder.camera.camera2.GammaSettings
import com.pilerks1.hdrrecorder.camera.model.MeteringAnchor
import com.pilerks1.hdrrecorder.camera.model.MeteringMode
import com.pilerks1.hdrrecorder.camera.model.PreviewDynamicRange
import com.pilerks1.hdrrecorder.camera.model.VideoEncoding
import com.pilerks1.hdrrecorder.ui.camera.controls.CameraControlPanel

/**
 * Represents all possible user actions or events that can occur in the Camera UI.
 */
sealed interface CameraUiEvent {
    // Preset Events
    data object RetryPresets : CameraUiEvent
    data class AddPreset(val name: String) : CameraUiEvent
    data object SavePreset : CameraUiEvent
    data class SetSaveRecordingOptions(val enabled: Boolean) : CameraUiEvent
    data class SetSaveStorageLocation(val enabled: Boolean) : CameraUiEvent
    data class LoadPreset(val name: String) : CameraUiEvent
    data class DeletePreset(val name: String) : CameraUiEvent
    data object DeleteAllPresets : CameraUiEvent

    data object ToggleRecording : CameraUiEvent
    data object TogglePause : CameraUiEvent
    data object CycleFps : CameraUiEvent
    data object CycleResolution : CameraUiEvent
    data object CycleAspectRatio : CameraUiEvent
    data class TogglePanel(val panel: CameraControlPanel) : CameraUiEvent
    data class SetZoomRatio(val ratio: Float) : CameraUiEvent
    data class SelectZoomLens(val cameraId: String) : CameraUiEvent
    data class SetLowLightBoost(val enabled: Boolean) : CameraUiEvent

    // Color Events
    data class SetVideoBitDepth(val bitDepth: Int) : CameraUiEvent
    data class SetEncoding(val encoding: VideoEncoding) : CameraUiEvent
    data class SetVideoCodec(val mimeType: String?) : CameraUiEvent
    data class SetAudioCodec(val mimeType: String?) : CameraUiEvent
    data class SetAudioBitrate(val bitrate: String) : CameraUiEvent
    data class SetGammaSettings(val settings: GammaSettings) : CameraUiEvent

    data class SetBitrate(val bitrate: String) : CameraUiEvent
    data class SetStabilization(val enabled: Boolean) : CameraUiEvent

    // Preview Events
    data class SetPreviewFrameRate(val range: Range<Int>?) : CameraUiEvent
    data class SetPreviewDynamicRange(val dynamicRange: PreviewDynamicRange?) : CameraUiEvent
    data class SetRespectDisplayCutouts(val enabled: Boolean) : CameraUiEvent
    data class SetForceDisplaySdr(val enabled: Boolean) : CameraUiEvent

    // Camera2 Events
    data class SetCamera2OverrideEnabled(val control: Camera2Control, val enabled: Boolean) : CameraUiEvent
    data class SetCamera2Mode(val control: Camera2Control, val mode: Int) : CameraUiEvent
    data class SetExtendManualRanges(val enabled: Boolean) : CameraUiEvent
    data class SetWaitToLockAe(val enabled: Boolean) : CameraUiEvent

    // Storage Event
    data class SetStorageUri(val uri: String) : CameraUiEvent

    data class StartMetering(
        val point: MeteringPoint,
        val anchor: MeteringAnchor,
        val mode: MeteringMode,
        val generation: Long
    ) : CameraUiEvent
    data class RemapMetering(val point: MeteringPoint, val generation: Long, val requestId: Long) : CameraUiEvent
    data class CancelMetering(val generation: Long) : CameraUiEvent
    data object OpenSettings : CameraUiEvent
    data object CloseSettings : CameraUiEvent
}
