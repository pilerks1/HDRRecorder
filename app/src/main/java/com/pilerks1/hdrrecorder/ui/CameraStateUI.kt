package com.pilerks1.hdrrecorder.ui

import androidx.camera.core.MeteringPoint
import com.pilerks1.hdrrecorder.data.camera.core.MeteringAnchor
import com.pilerks1.hdrrecorder.data.camera.core.MeteringMode
import com.pilerks1.hdrrecorder.data.camera.core.RegionalMeteringState
import com.pilerks1.hdrrecorder.model.ColorFormat
import com.pilerks1.hdrrecorder.model.GammaCurve
import com.pilerks1.hdrrecorder.model.Resolution
import com.pilerks1.hdrrecorder.model.RecordingAspectRatio
import android.util.Range
import com.pilerks1.hdrrecorder.data.camera.core.CameraZoomState

/**
 * Represents the state of all manual controls.
 */
data class ManualControlsState(
    val activePanel: CameraControlPanel? = null,
    
    // ISO
    val isManualIso: Boolean = false,
    val isoValue: Int? = null,
    val isManualPostRawSensitivity: Boolean = false,
    val postRawSensitivityBoost: Int? = null,
    
    // Shutter Speed (SS)
    val isManualSs: Boolean = false,
    val ssValueNanos: Long? = null,
    
    // Exposure Value (EV)
    val isManualEv: Boolean = false,
    val evValueIndex: Int = 0,
    val isLowLightBoostEnabled: Boolean = false,
    
    // Focus
    val isManualFocus: Boolean = false,
    val focusDistanceDiopters: Float? = null,
    
    // White Balance
    val isManualWb: Boolean = false,
    val wbTemp: Int? = null,
    val wbTint: Int? = null,
    
    // FPS
    val isManualFps: Boolean = false,
    val fpsRange: Range<Int>? = null
)

/**
 * Explicit reset-all action; preview metering preserves manual overrides.
 */
internal val ManualControlsState.hasManualSliderOverride: Boolean
    get() = isManualIso ||
        isManualPostRawSensitivity ||
        isManualSs ||
        evValueIndex != 0 ||
        isManualFocus ||
        isManualWb

internal fun ManualControlsState.resetManualSlidersToAuto(): ManualControlsState {
    if (!hasManualSliderOverride) return this
    return copy(
        isManualIso = false,
        isoValue = null,
        isManualPostRawSensitivity = false,
        postRawSensitivityBoost = null,
        isManualSs = false,
        ssValueNanos = null,
        isManualEv = false,
        evValueIndex = 0,
        isManualFocus = false,
        focusDistanceDiopters = null,
        isManualWb = false,
        wbTemp = null,
        wbTint = null
    )
}

/**
 * Represents the current state of the Camera UI.
 */
data class CameraUiState(
    // Presets
    val currentPresetName: String = "Default",
    val presetsList: List<String> = listOf("Default"),

    // Camera Status
    val isCameraReady: Boolean = false,
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val isLowLightBoostActive: Boolean = false,
    val isCameraHardwareEnabled: Boolean = true,
    val previewPlaceholderLabel: String? = null,

    // Settings / Configuration
    val selectedFps: Int = 30,
    val selectedResolution: Resolution = Resolution.FHD,
    val selectedAspectRatio: RecordingAspectRatio = RecordingAspectRatio.FOUR_THREE,

    // Color Settings
    val colorFormat: ColorFormat = ColorFormat.HLG,
    val gammaCurve: GammaCurve = GammaCurve.AUTO,

    // Manual Controls
    val manualControlsState: ManualControlsState = ManualControlsState(),
    val cameraCapabilities: com.pilerks1.hdrrecorder.data.camera.CameraCapabilities? = null,
    val zoomState: CameraZoomState = CameraZoomState(),
    val meteringState: RegionalMeteringState = RegionalMeteringState(),

    // Video Settings
    val isNoiseReductionEnabled: Boolean = true,
    val bitrate: String = "30",
    val isStabilizationEnabled: Boolean = true,

    // SDR Hacks
    val isSdrToneMapEnabled: Boolean = false,
    val isForceDisplaySdrEnabled: Boolean = false,

    // Storage
    val storageUri: String? = null,

    // UI Visibility
    val isSettingsSheetVisible: Boolean = false,
    val isExposureTransitioning: Boolean = false,
    val exposureTransitionMessage: String? = null
)

/**
 * Represents all possible user actions or events that can occur in the Camera UI.
 */
sealed interface CameraUiEvent {
    // Preset Events
    data class SavePreset(val name: String) : CameraUiEvent
    data class LoadPreset(val name: String) : CameraUiEvent
    data class DeletePreset(val name: String) : CameraUiEvent
    data object DeleteAllPresets : CameraUiEvent

    data object ToggleRecording : CameraUiEvent
    data object TogglePause : CameraUiEvent
    data object CycleFps : CameraUiEvent
    data object CycleResolution : CameraUiEvent
    data object CycleAspectRatio : CameraUiEvent
    data class SetLinearZoom(val progress: Float) : CameraUiEvent
    data class SetZoomRatio(val ratio: Float) : CameraUiEvent
    data class SelectZoomLens(val cameraId: String) : CameraUiEvent
    data class SetLowLightBoost(val enabled: Boolean) : CameraUiEvent

    // Color Events
    data object CycleColorFormat : CameraUiEvent
    data object CycleGammaCurve : CameraUiEvent

    data class SetNoiseReduction(val enabled: Boolean) : CameraUiEvent
    data class SetBitrate(val bitrate: String) : CameraUiEvent
    data class SetStabilization(val enabled: Boolean) : CameraUiEvent

    // SDR Hack Events
    data class SetSdrToneMap(val enabled: Boolean) : CameraUiEvent
    data class SetForceDisplaySdr(val enabled: Boolean) : CameraUiEvent

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
