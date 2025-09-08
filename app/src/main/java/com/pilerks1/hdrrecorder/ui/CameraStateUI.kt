package com.pilerks1.hdrrecorder.ui

import com.pilerks1.hdrrecorder.model.Resolution

/**
 * Represents the single source of truth for the entire camera screen's UI.
 * This data class holds all the state necessary to render the UI at any given moment.
 */
data class CameraUiState(
    // Recording State
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val recordingTime: Long = 0L,

    // Camera Settings
    val selectedResolution: Resolution = Resolution.UHD,
    val selectedFps: Int = 30,
    val focusMode: String = "Auto",
    val gammaMode: String = "Custom",
    val isNoiseReductionEnabled: Boolean = true,

    // Camera Stats
    val effectiveFps: Int = 0,
    val droppedFrames: Int = 0,
    val addedFrames: Int = 0,
    val iso: Int = 0,
    val shutterSpeed: Double = 0.0,

    // UI Visibility State
    val isSettingsSheetVisible: Boolean = false,
    val isCameraReady: Boolean = false
)

/**
 * Defines all possible actions a user can perform on the camera screen.
 * Using a sealed class ensures that the ViewModel handles a finite, well-defined
 * set of user intentions.
 */
sealed class CameraUiEvent {
    object ToggleRecording : CameraUiEvent()
    object TogglePause : CameraUiEvent()
    object OpenSettings : CameraUiEvent()
    object CloseSettings : CameraUiEvent()
    object CycleFps : CameraUiEvent()
    object CycleResolution : CameraUiEvent()
    object CycleFocusMode : CameraUiEvent()
    object CycleGammaMode : CameraUiEvent()
    data class SetNoiseReduction(val enabled: Boolean) : CameraUiEvent()
    data class TapToMeter(val meteringPoint: androidx.camera.core.MeteringPoint) : CameraUiEvent()
}
