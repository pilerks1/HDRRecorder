package com.pilerks1.hdrrecorder.ui.settingsUI

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.pilerks1.hdrrecorder.model.RecordingOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pilerks1.hdrrecorder.model.VideoEncoding
import com.pilerks1.hdrrecorder.model.GammaMode
import com.pilerks1.hdrrecorder.model.GammaSettings
import com.pilerks1.hdrrecorder.data.camera.core.Camera2CapabilitySource
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
    onSavePreset: (String) -> Unit,
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

    // SDR Hacks
    isForceDisplaySdrEnabled: Boolean,
    onForceDisplaySdrChange: (Boolean) -> Unit,

    // Camera2
    camera2Settings: Camera2Settings,
    camera2Capabilities: Camera2ModeCapabilities?,
    isManualFocus: Boolean,
    onCamera2CapabilitySourceChange: (Camera2CapabilitySource) -> Unit,
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

        PresetSection(
            currentPreset = currentPreset,
            presetsList = presetsList,
            onSavePreset = onSavePreset,
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

        SdrSection(
            isForceDisplaySdrEnabled = isForceDisplaySdrEnabled,
            onForceDisplaySdrChange = onForceDisplaySdrChange
        )

        SectionDivider()

        Camera2Section(
            settings = camera2Settings,
            capabilities = camera2Capabilities,
            isManualFocus = isManualFocus,
            onCapabilitySourceChange = onCamera2CapabilitySourceChange,
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
    }
}
