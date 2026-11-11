package com.pilerks1.hdrrecorder.ui.viewmodels

import com.pilerks1.hdrrecorder.model.*
import com.pilerks1.hdrrecorder.ui.CameraUiState
import com.pilerks1.hdrrecorder.ui.ManualControlsState

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
        isManualIso = manualControlsState.isManualIso,
        isoValue = manualControlsState.isoValue,
        isManualPostRawSensitivity = manualControlsState.isManualPostRawSensitivity,
        postRawSensitivityBoost = manualControlsState.postRawSensitivityBoost,
        isManualSs = manualControlsState.isManualSs,
        ssValueNanos = manualControlsState.ssValueNanos,
        isManualEv = manualControlsState.isManualEv,
        evValueIndex = manualControlsState.evValueIndex,
        isLowLightBoostEnabled = manualControlsState.isLowLightBoostEnabled,
        isManualFocus = manualControlsState.isManualFocus,
        focusDistanceDiopters = manualControlsState.focusDistanceDiopters,
        isManualWb = manualControlsState.isManualWb,
        wbTemp = manualControlsState.wbTemp,
        wbTint = manualControlsState.wbTint,
        isManualFps = manualControlsState.isManualFps,
        fpsRange = manualControlsState.fpsRange
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
        manualControlsState = if (recording == null) manualControlsState else ManualControlsState(
            isManualIso = recording.isManualIso,
            isoValue = recording.isoValue,
            isManualPostRawSensitivity = recording.isManualPostRawSensitivity,
            postRawSensitivityBoost = recording.postRawSensitivityBoost,
            isManualSs = recording.isManualSs,
            ssValueNanos = recording.ssValueNanos,
            isManualEv = recording.isManualEv,
            evValueIndex = recording.evValueIndex,
            isLowLightBoostEnabled = recording.isLowLightBoostEnabled,
            isManualFocus = recording.isManualFocus,
            focusDistanceDiopters = recording.focusDistanceDiopters,
            isManualWb = recording.isManualWb,
            wbTemp = recording.wbTemp,
            wbTint = recording.wbTint,
            isManualFps = recording.isManualFps,
            fpsRange = recording.fpsRange
        )
    )
}
