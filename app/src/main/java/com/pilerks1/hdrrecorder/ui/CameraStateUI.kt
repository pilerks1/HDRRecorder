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
    val gammaMode: String = "Device",
    val isNoiseReductionEnabled: Boolean = false,

    // SDR Hacks
    val isSdrToneMapEnabled: Boolean = false,
    val isForceDisplaySdrEnabled: Boolean = false, // Restored

    // Stats - Now grouped into a single object
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
    object CycleGammaMode : CameraUiEvent()
    data class SetNoiseReduction(val enabled: Boolean) : CameraUiEvent()

    // SDR Hack Events
    data class SetSdrToneMap(val enabled: Boolean) : CameraUiEvent()
    data class SetForceDisplaySdr(val enabled: Boolean) : CameraUiEvent() // Restored

    data class TapToMeter(val meteringPoint: MeteringPoint) : CameraUiEvent()
    object OpenSettings : CameraUiEvent()
    object CloseSettings : CameraUiEvent()
}