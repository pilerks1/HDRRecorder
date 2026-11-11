package com.pilerks1.hdrrecorder.ui.settingsUI

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.pilerks1.hdrrecorder.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pilerks1.hdrrecorder.model.VideoColorOption
import com.pilerks1.hdrrecorder.ui.CameraUiEvent
import com.pilerks1.hdrrecorder.ui.CameraUiState

/**
 * Settings UI overlay.
 * Full screen black background style with modular sections. Settings cannot open while
 * recording, so every section is editable here.
 */
@Composable
internal fun SettingsUI(
    state: CameraUiState,
    onEvent: (CameraUiEvent) -> Unit,
    onNavigateToCompatibility: () -> Unit,
    onShowDiagnostics: () -> Unit
) {
    val onClose = { onEvent(CameraUiEvent.CloseSettings) }
    val options = state.recordingOptions
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(enabled = true) {}
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SettingsHeader(onClose = onClose)

        PresetSection(
            currentPreset = state.currentPresetName,
            presetsList = state.presetsList,
            onAddPreset = { onEvent(CameraUiEvent.AddPreset(it)) },
            onSavePreset = { onEvent(CameraUiEvent.SavePreset) },
            saveRecordingOptions = state.saveRecordingOptions,
            onSaveRecordingOptionsChange = { onEvent(CameraUiEvent.SetSaveRecordingOptions(it)) },
            saveStorageLocation = state.saveStorageLocation,
            onSaveStorageLocationChange = { onEvent(CameraUiEvent.SetSaveStorageLocation(it)) },
            presetsEnabled = state.arePresetsInitialized,
            presetBusy = state.isPresetBusy,
            onRetryPresets = { onEvent(CameraUiEvent.RetryPresets) },
            onLoadPreset = { onEvent(CameraUiEvent.LoadPreset(it)) },
            onDeletePreset = { onEvent(CameraUiEvent.DeletePreset(it)) },
            onDeleteAllPresets = { onEvent(CameraUiEvent.DeleteAllPresets) }
        )

        SectionDivider()

        VideoSection(
            videoCodec = state.videoCodec,
            videoCodecs = options.videoCodecs,
            onVideoCodecChange = { onEvent(CameraUiEvent.SetVideoCodec(it)) },
            compatibleVideoCodecs = options.colorsByVideoCodec.filterValues {
                VideoColorOption(state.videoBitDepth, state.encoding) in it
            }.keys,
            bitrate = state.bitrate,
            onBitrateChange = { onEvent(CameraUiEvent.SetBitrate(it)) },
            isStabilizationEnabled = state.isStabilizationEnabled,
            onStabilizationChange = { onEvent(CameraUiEvent.SetStabilization(it)) }
        )

        SectionDivider()

        ColorSection(
            bitDepth = state.videoBitDepth,
            colors = options.allColors(),
            compatibleColors = options.colors(state.videoCodec),
            onBitDepthChange = { onEvent(CameraUiEvent.SetVideoBitDepth(it)) },
            encoding = state.encoding,
            onEncodingChange = { onEvent(CameraUiEvent.SetEncoding(it)) }
        )

        if (state.encoding.supportsGammaCurveSelection) {
            SectionDivider()
            GammaSection(state.gammaSettings, state.cameraCapabilities?.gammaModes.orEmpty()) {
                onEvent(CameraUiEvent.SetGammaSettings(it))
            }
        }

        SectionDivider()

        AudioSection(
            codec = state.audioCodec,
            codecs = options.audioCodecs,
            onCodecChange = { onEvent(CameraUiEvent.SetAudioCodec(it)) },
            bitrate = state.audioBitrate,
            onBitrateChange = { onEvent(CameraUiEvent.SetAudioBitrate(it)) }
        )

        SectionDivider()

        PreviewSection(
            settings = state.previewSettings,
            options = state.previewOptions,
            onFrameRateChange = { onEvent(CameraUiEvent.SetPreviewFrameRate(it)) },
            onDynamicRangeChange = { onEvent(CameraUiEvent.SetPreviewDynamicRange(it)) },
            isRespectDisplayCutoutsEnabled = state.isRespectDisplayCutoutsEnabled,
            onRespectDisplayCutoutsChange = { onEvent(CameraUiEvent.SetRespectDisplayCutouts(it)) },
            isForceDisplaySdrEnabled = state.isForceDisplaySdrEnabled,
            onForceDisplaySdrChange = { onEvent(CameraUiEvent.SetForceDisplaySdr(it)) }
        )

        SectionDivider()

        Camera2Section(
            settings = state.camera2Settings,
            capabilities = state.cameraCapabilities?.camera2Modes,
            isManualFocus = state.manualControlsState.isManualFocus,
            onOverrideEnabledChange = { control, enabled ->
                onEvent(CameraUiEvent.SetCamera2OverrideEnabled(control, enabled))
            },
            onModeChange = { control, mode -> onEvent(CameraUiEvent.SetCamera2Mode(control, mode)) },
            isExtendManualRangesEnabled = state.isExtendManualRangesEnabled,
            onExtendManualRangesChange = { onEvent(CameraUiEvent.SetExtendManualRanges(it)) },
            isWaitToLockAeEnabled = state.isWaitToLockAeEnabled,
            onWaitToLockAeChange = { onEvent(CameraUiEvent.SetWaitToLockAe(it)) }
        )

        SectionDivider()

        StorageSection(
            storageUri = state.storageUri,
            onStorageUriSelected = { onEvent(CameraUiEvent.SetStorageUri(it)) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = { onClose(); onNavigateToCompatibility() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text("Check Device Compatibility")
        }
        OutlinedButton(
            onClick = { onClose(); onShowDiagnostics() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text(stringResource(R.string.diagnostics_title))
        }
    }
}
