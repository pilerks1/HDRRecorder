package com.pilerks1.hdrrecorder.ui.settingsUI

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.pilerks1.hdrrecorder.model.RecordingOptions
import android.util.Range
import com.pilerks1.hdrrecorder.model.PreviewSettings
import com.pilerks1.hdrrecorder.model.PreviewOptions
import com.pilerks1.hdrrecorder.model.PreviewDynamicRange
import com.pilerks1.hdrrecorder.errorReporter
import com.pilerks1.hdrrecorder.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pilerks1.hdrrecorder.model.VideoEncoding
import com.pilerks1.hdrrecorder.model.GammaMode
import com.pilerks1.hdrrecorder.model.GammaSettings
import com.pilerks1.hdrrecorder.data.camera.core.Camera2Control
import com.pilerks1.hdrrecorder.data.camera.core.Camera2ModeCapabilities
import com.pilerks1.hdrrecorder.data.camera.core.Camera2Settings

/**
 * Settings UI overlay.
 * Full screen black background style with modular sections.
 */
@Composable
fun SettingsUI(
    // Presets
    currentPreset: String,
    presetsList: List<String>,
    onAddPreset: (String) -> Unit,
    onSavePreset: () -> Unit,
    saveRecordingOptions: Boolean,
    onSaveRecordingOptionsChange: (Boolean) -> Unit,
    saveStorageLocation: Boolean,
    onSaveStorageLocationChange: (Boolean) -> Unit,
    presetsEnabled: Boolean,
    presetBusy: Boolean,
    onRetryPresets: () -> Unit,
    onLoadPreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onDeleteAllPresets: () -> Unit,

    videoCodec: String?,
    onVideoCodecChange: (String?) -> Unit,
    audioCodec: String?,
    onAudioCodecChange: (String?) -> Unit,
    audioBitrate: String,
    onAudioBitrateChange: (String) -> Unit,
    recordingOptions: RecordingOptions,
    bitDepth: Int,
    onBitDepthChange: (Int) -> Unit,
    // Video Settings
    bitrate: String,
    onBitrateChange: (String) -> Unit,
    isStabilizationEnabled: Boolean,
    onStabilizationChange: (Boolean) -> Unit,

    // Color Settings
    encoding: VideoEncoding,
    onEncodingChange: (VideoEncoding) -> Unit,
    gammaSettings: GammaSettings,
    gammaModes: List<GammaMode>,
    onGammaSettingsChange: (GammaSettings) -> Unit,

    // Preview
    previewSettings: PreviewSettings,
    previewOptions: PreviewOptions,
    isRecording: Boolean,
    onPreviewFrameRateChange: (Range<Int>?) -> Unit,
    onPreviewDynamicRangeChange: (PreviewDynamicRange?) -> Unit,
    isRespectDisplayCutoutsEnabled: Boolean,
    onRespectDisplayCutoutsChange: (Boolean) -> Unit,
    isForceDisplaySdrEnabled: Boolean,
    onForceDisplaySdrChange: (Boolean) -> Unit,

    // Camera2
    camera2Settings: Camera2Settings,
    camera2Capabilities: Camera2ModeCapabilities?,
    isManualFocus: Boolean,
    onCamera2OverrideEnabledChange: (Camera2Control, Boolean) -> Unit,
    onCamera2ModeChange: (Camera2Control, Int) -> Unit,
    isExtendManualRangesEnabled: Boolean,
    onExtendManualRangesChange: (Boolean) -> Unit,
    isWaitToLockAeEnabled: Boolean,
    onWaitToLockAeChange: (Boolean) -> Unit,

    // Storage
    storageUri: String?,
    onStorageUriSelected: (String) -> Unit,

    onNavigateToCompatibility: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(enabled = true) {}
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SettingsHeader(onClose = onClose)
        val reporter = LocalContext.current.errorReporter

        PresetSection(
            currentPreset = currentPreset,
            presetsList = presetsList,
            onAddPreset = onAddPreset,
            onSavePreset = onSavePreset,
            saveRecordingOptions = saveRecordingOptions,
            onSaveRecordingOptionsChange = onSaveRecordingOptionsChange,
            saveStorageLocation = saveStorageLocation,
            onSaveStorageLocationChange = onSaveStorageLocationChange,
            presetsEnabled = presetsEnabled,
            presetBusy = presetBusy,
            onRetryPresets = onRetryPresets,
            onLoadPreset = onLoadPreset,
            onDeletePreset = onDeletePreset,
            onDeleteAllPresets = onDeleteAllPresets
        )

        SectionDivider()

        VideoSection(
            videoCodec = videoCodec,
            videoCodecs = recordingOptions.videoCodecs,
            onVideoCodecChange = onVideoCodecChange,
            compatibleVideoCodecs = recordingOptions.colorsByVideoCodec.filterValues {
                com.pilerks1.hdrrecorder.model.VideoColorOption(bitDepth, encoding) in it
            }.keys,
            bitrate = bitrate,
            onBitrateChange = onBitrateChange,
            isStabilizationEnabled = isStabilizationEnabled,
            onStabilizationChange = onStabilizationChange
        )

        SectionDivider()

        ColorSection(
            bitDepth = bitDepth,
            colors = recordingOptions.allColors(),
            compatibleColors = recordingOptions.colors(videoCodec),
            onBitDepthChange = onBitDepthChange,
            encoding = encoding,
            onEncodingChange = onEncodingChange
        )

        if (encoding.supportsGammaCurveSelection) {
            SectionDivider()
            GammaSection(gammaSettings, gammaModes, onGammaSettingsChange)
        }

        SectionDivider()

        AudioSection(
            codec = audioCodec,
            codecs = recordingOptions.audioCodecs,
            onCodecChange = onAudioCodecChange,
            bitrate = audioBitrate,
            onBitrateChange = onAudioBitrateChange
        )

        SectionDivider()

        PreviewSection(
            settings = previewSettings,
            options = previewOptions,
            enabled = !isRecording,
            onFrameRateChange = onPreviewFrameRateChange,
            onDynamicRangeChange = onPreviewDynamicRangeChange,
            isRespectDisplayCutoutsEnabled = isRespectDisplayCutoutsEnabled,
            onRespectDisplayCutoutsChange = onRespectDisplayCutoutsChange,
            isForceDisplaySdrEnabled = isForceDisplaySdrEnabled,
            onForceDisplaySdrChange = onForceDisplaySdrChange
        )

        SectionDivider()

        Camera2Section(
            settings = camera2Settings,
            capabilities = camera2Capabilities,
            isManualFocus = isManualFocus,
            onOverrideEnabledChange = onCamera2OverrideEnabledChange,
            onModeChange = onCamera2ModeChange,
            isExtendManualRangesEnabled = isExtendManualRangesEnabled,
            onExtendManualRangesChange = onExtendManualRangesChange,
            isWaitToLockAeEnabled = isWaitToLockAeEnabled,
            onWaitToLockAeChange = onWaitToLockAeChange
        )

        SectionDivider()

        StorageSection(
            storageUri = storageUri,
            onStorageUriSelected = onStorageUriSelected
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
            onClick = { onClose(); reporter.showHistory(true) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text(stringResource(R.string.diagnostics_title))
        }
    }
}
