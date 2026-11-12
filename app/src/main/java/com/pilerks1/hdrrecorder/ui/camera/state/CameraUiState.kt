package com.pilerks1.hdrrecorder.ui.camera.state

import com.pilerks1.hdrrecorder.camera.camera2.Camera2Settings
import com.pilerks1.hdrrecorder.camera.camera2.GammaSettings
import com.pilerks1.hdrrecorder.camera.model.CameraCapabilities
import com.pilerks1.hdrrecorder.camera.model.CameraControlValues
import com.pilerks1.hdrrecorder.camera.model.CameraZoomState
import com.pilerks1.hdrrecorder.camera.model.LowLightBoostObservation
import com.pilerks1.hdrrecorder.camera.model.LowLightBoostPolicy
import com.pilerks1.hdrrecorder.camera.model.LowLightBoostRequestState
import com.pilerks1.hdrrecorder.camera.model.LowLightBoostRuntimeState
import com.pilerks1.hdrrecorder.camera.model.PreviewOptions
import com.pilerks1.hdrrecorder.camera.model.PreviewSettings
import com.pilerks1.hdrrecorder.camera.model.RecordingAspectRatio
import com.pilerks1.hdrrecorder.camera.model.RecordingOptions
import com.pilerks1.hdrrecorder.camera.model.RegionalMeteringState
import com.pilerks1.hdrrecorder.camera.model.Resolution
import com.pilerks1.hdrrecorder.camera.model.VideoEncoding
import com.pilerks1.hdrrecorder.data.telemetry.CameraTelemetry
import com.pilerks1.hdrrecorder.data.telemetry.StatsSnapshot
import com.pilerks1.hdrrecorder.ui.camera.controls.CameraControlPanel
import kotlinx.coroutines.flow.StateFlow

/**
 * Callback surface passed to control composables instead of the concrete CameraViewModel.
 * Keeps UI widgets decoupled from the ViewModel (no opt-in leakage, easier previews/testing).
 */
data class CameraActions(
    val onEvent: (CameraUiEvent) -> Unit,
    val onManualControlsChange: ((ManualControlsState) -> ManualControlsState) -> Unit,
    val onManualControlInteractionFinished: () -> Unit,
    val stats: StateFlow<StatsSnapshot>,
    val recordingDurationNanos: StateFlow<Long>,
    val sliderTelemetry: StateFlow<CameraTelemetry>
)

/**
 * Represents the current state of the Camera UI.
 */
data class CameraUiState(
    // Presets
    val currentPresetId: String? = null,
    val isPresetBusy: Boolean = false,
    val arePresetsInitialized: Boolean = false,
    val saveRecordingOptions: Boolean = false,
    val saveStorageLocation: Boolean = false,
    val currentPresetName: String = "Default",
    val presetsList: List<String> = emptyList(),

    // Camera Status
    val isCameraReady: Boolean = false,
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val lowLightBoostState: LowLightBoostObservation = LowLightBoostObservation(),
    val lowLightBoostRequest: LowLightBoostRequestState = LowLightBoostRequestState(),
    val previewPlaceholderLabel: String? = null,

    // Settings / Configuration
    val selectedFps: Int = 30,
    val selectedResolution: Resolution = Resolution.FHD,
    val selectedAspectRatio: RecordingAspectRatio = RecordingAspectRatio.FOUR_THREE,

    // Color Settings
    val encoding: VideoEncoding = VideoEncoding.HLG,
    val videoBitDepth: Int = 10,
    val recordingOptions: RecordingOptions = RecordingOptions(),
    val gammaSettings: GammaSettings = GammaSettings(),

    // Manual Controls
    val activePanel: CameraControlPanel? = null,
    val manualControlsState: ManualControlsState = ManualControlsState(),
    val cameraCapabilities: CameraCapabilities? = null,
    val zoomState: CameraZoomState = CameraZoomState(),
    val meteringState: RegionalMeteringState = RegionalMeteringState(),

    // Video Settings
    val bitrate: String = "",
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val audioBitrate: String = "",
    val isStabilizationEnabled: Boolean = true,

    // Preview
    val previewSettings: PreviewSettings = PreviewSettings(),
    val previewOptions: PreviewOptions = PreviewOptions(),
    val isRespectDisplayCutoutsEnabled: Boolean = false,
    val isForceDisplaySdrEnabled: Boolean = false,

    // Camera2
    val camera2Settings: Camera2Settings = Camera2Settings(),
    val isExtendManualRangesEnabled: Boolean = false,
    val isWaitToLockAeEnabled: Boolean = true,

    // Storage
    val storageUri: String? = null,

    // UI Visibility
    val isSettingsSheetVisible: Boolean = false,
    val isExposureTransitioning: Boolean = false
) {
    val isLowLightBoostEligible: Boolean
        get() = LowLightBoostPolicy.isSupported(cameraCapabilities?.supportsLowLightBoost == true) &&
            LowLightBoostPolicy.isEligible(manualControlsState, selectedFps, previewSettings.frameRate)

    val isLowLightBoostActive: Boolean
        get() = isCameraReady && !isExposureTransitioning && manualControlsState.isLowLightBoostEnabled &&
            isLowLightBoostEligible &&
            lowLightBoostState.generation != null && lowLightBoostState.generation == lowLightBoostRequest.generation &&
            lowLightBoostState.state == LowLightBoostRuntimeState.ACTIVE
}

internal fun CameraUiState.normalizeLowLightBoost(): CameraUiState {
    val caps = cameraCapabilities ?: return this
    return copy(manualControlsState = LowLightBoostPolicy.normalize(
        manualControlsState, caps.supportsLowLightBoost, selectedFps, previewSettings.frameRate
    ))
}

/** Manual controls use the shared domain control model; panel selection lives in [CameraUiState]. */
typealias ManualControlsState = CameraControlValues

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
    return withAutoExposure().copy(
        isManualEv = false,
        evValueIndex = 0,
        isManualFocus = false,
        focusDistanceDiopters = null,
        isManualWb = false,
        wbTemp = null,
        wbTint = null
    )
}
