package com.pilerks1.hdrrecorder.ui

import androidx.camera.core.MeteringPoint
import com.pilerks1.hdrrecorder.data.StatsSnapshot
import com.pilerks1.hdrrecorder.model.Resolution

/**
 * Represents the current state of the Camera UI.
 */
data class CameraUiState(
    // Camera Status
    val isCameraReady: Boolean = false,
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val recordingTime: Long = 0L,

    // Settings / Configuration
    val selectedFps: Int = 30,
    val selectedResolution: Resolution = Resolution.FHD,
    val focusMode: String = "Auto",

    // Color Settings
    val colorFormat: String = "HLG",
    val gammaCurve: String = "Auto",

    // Video Settings
    val isNoiseReductionEnabled: Boolean = true,
    val bitrate: String = "30",
    val isStabilizationEnabled: Boolean = true,

    // SDR Hacks
    val isSdrToneMapEnabled: Boolean = false,
    val isForceDisplaySdrEnabled: Boolean = false,

    // Stats
    val stats: StatsSnapshot = StatsSnapshot(),

    // UI Visibility
    val isSettingsSheetVisible: Boolean = false
)

/**
 * Represents all possible user actions or events that can occur in the Camera UI.
 */
sealed class CameraUiEvent {
    object ToggleRecording : CameraUiEvent()
    object TogglePause : CameraUiEvent()
    object CycleFps : CameraUiEvent()
    object CycleResolution : CameraUiEvent()
    object CycleFocusMode : CameraUiEvent()

    // Color Events
    object CycleColorFormat : CameraUiEvent()
    object CycleGammaCurve : CameraUiEvent()

    data class SetNoiseReduction(val enabled: Boolean) : CameraUiEvent()
    data class SetBitrate(val bitrate: String) : CameraUiEvent()
    data class SetStabilization(val enabled: Boolean) : CameraUiEvent()

    // SDR Hack Events
    data class SetSdrToneMap(val enabled: Boolean) : CameraUiEvent()
    data class SetForceDisplaySdr(val enabled: Boolean) : CameraUiEvent()

    data class TapToMeter(val meteringPoint: MeteringPoint) : CameraUiEvent()
    object OpenSettings : CameraUiEvent()
    object CloseSettings : CameraUiEvent()
}