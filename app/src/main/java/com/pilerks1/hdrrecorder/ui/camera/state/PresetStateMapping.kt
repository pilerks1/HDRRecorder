package com.pilerks1.hdrrecorder.ui.camera.state

import com.pilerks1.hdrrecorder.camera.model.CaptureConfiguration
import com.pilerks1.hdrrecorder.data.presets.CameraPreset
import com.pilerks1.hdrrecorder.data.presets.PresetRecordingControls
import com.pilerks1.hdrrecorder.data.presets.PresetSettings

internal fun CameraUiState.toPreset(id: String, name: String) = CameraPreset(
    id = id,
    name = name,
    saveRecordingOptions = saveRecordingOptions,
    saveStorageLocation = saveStorageLocation,
    storageUri = storageUri.takeIf { saveStorageLocation },
    settings = PresetSettings(
        videoCodec = videoCodec,
        audioCodec = audioCodec,
        audioBitrate = audioBitrate,
        videoBitDepth = videoBitDepth,
        encoding = encoding,
        bitrate = bitrate,
        isStabilizationEnabled = isStabilizationEnabled,
        isForceDisplaySdrEnabled = isForceDisplaySdrEnabled,
        isRespectDisplayCutoutsEnabled = isRespectDisplayCutoutsEnabled,
        isExtendManualRangesEnabled = isExtendManualRangesEnabled,
        isWaitToLockAeEnabled = isWaitToLockAeEnabled,
        gammaSettings = gammaSettings,
        previewSettings = previewSettings,
        camera2Settings = camera2Settings
    ),
    recording = if (!saveRecordingOptions) null else PresetRecordingControls(
        selectedFps = selectedFps,
        selectedResolution = selectedResolution,
        selectedAspectRatio = selectedAspectRatio,
        zoomRatio = zoomState.ratio,
        controls = manualControlsState
    )
)

internal fun CameraUiState.withPreset(preset: CameraPreset): CameraUiState {
    val settings = preset.settings
    val recording = preset.recording
    return copy(
        currentPresetId = preset.id,
        currentPresetName = preset.name,
        saveRecordingOptions = preset.saveRecordingOptions,
        saveStorageLocation = preset.saveStorageLocation,
        videoCodec = settings.videoCodec,
        audioCodec = settings.audioCodec,
        audioBitrate = settings.audioBitrate,
        videoBitDepth = settings.videoBitDepth,
        encoding = settings.encoding,
        bitrate = settings.bitrate,
        isStabilizationEnabled = settings.isStabilizationEnabled,
        isForceDisplaySdrEnabled = settings.isForceDisplaySdrEnabled,
        isRespectDisplayCutoutsEnabled = settings.isRespectDisplayCutoutsEnabled,
        isExtendManualRangesEnabled = settings.isExtendManualRangesEnabled,
        isWaitToLockAeEnabled = settings.isWaitToLockAeEnabled,
        gammaSettings = settings.gammaSettings,
        previewSettings = settings.previewSettings,
        camera2Settings = settings.camera2Settings.reconcile(cameraCapabilities?.camera2Modes),
        zoomState = zoomState.copy(ratio = recording?.zoomRatio ?: zoomState.ratio),
        selectedFps = recording?.selectedFps ?: selectedFps,
        selectedResolution = recording?.selectedResolution ?: selectedResolution,
        selectedAspectRatio = recording?.selectedAspectRatio ?: selectedAspectRatio,
        manualControlsState = recording?.controls ?: manualControlsState
    )
}

/** The data-layer view of everything the bound camera session depends on. */
internal fun CameraUiState.toCaptureConfiguration() = CaptureConfiguration(
    fps = selectedFps,
    manualFpsRange = manualControlsState.fpsRange.takeIf { manualControlsState.isManualFps },
    resolution = selectedResolution,
    aspectRatio = selectedAspectRatio,
    encoding = encoding,
    bitDepth = videoBitDepth,
    videoCodec = videoCodec,
    audioCodec = audioCodec,
    bitrate = bitrate,
    audioBitrate = audioBitrate,
    isStabilizationEnabled = isStabilizationEnabled,
    previewSettings = previewSettings,
    isExtendManualRangesEnabled = isExtendManualRangesEnabled,
    zoomRatio = zoomState.ratio
)

/** Restores session-defining settings from a retained session; zoom keeps its live value. */
internal fun CameraUiState.withCaptureConfiguration(config: CaptureConfiguration) = copy(
    selectedFps = config.fps,
    selectedResolution = config.resolution,
    selectedAspectRatio = config.aspectRatio,
    encoding = config.encoding,
    videoBitDepth = config.bitDepth,
    videoCodec = config.videoCodec,
    audioCodec = config.audioCodec,
    bitrate = config.bitrate,
    audioBitrate = config.audioBitrate,
    isStabilizationEnabled = config.isStabilizationEnabled,
    previewSettings = config.previewSettings,
    isExtendManualRangesEnabled = config.isExtendManualRangesEnabled,
    manualControlsState = manualControlsState.copy(
        isManualFps = config.manualFpsRange != null,
        fpsRange = config.manualFpsRange
    )
)
