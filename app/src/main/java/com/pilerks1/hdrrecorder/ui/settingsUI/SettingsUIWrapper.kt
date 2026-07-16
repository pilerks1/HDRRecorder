package com.pilerks1.hdrrecorder.ui.settingsUI

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pilerks1.hdrrecorder.model.ColorFormat
import com.pilerks1.hdrrecorder.model.GammaCurve

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

    // Video Settings
    noiseReductionEnabled: Boolean,
    onNoiseReductionChange: (Boolean) -> Unit,
    bitrate: String,
    onBitrateChange: (String) -> Unit,
    isStabilizationEnabled: Boolean,
    onStabilizationChange: (Boolean) -> Unit,

    // Color Settings
    colorFormat: ColorFormat,
    onColorFormatChange: () -> Unit,
    gammaCurve: GammaCurve,
    onGammaCurveChange: () -> Unit,

    // SDR Hacks
    isSdrToneMapEnabled: Boolean,
    onSdrToneMapChange: (Boolean) -> Unit,
    isForceDisplaySdrEnabled: Boolean,
    onForceDisplaySdrChange: (Boolean) -> Unit,

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
            bitrate = bitrate,
            onBitrateChange = onBitrateChange,
            noiseReductionEnabled = noiseReductionEnabled,
            onNoiseReductionChange = onNoiseReductionChange,
            isStabilizationEnabled = isStabilizationEnabled,
            onStabilizationChange = onStabilizationChange
        )

        SectionDivider()

        ColorSection(
            colorFormat = colorFormat,
            onColorFormatChange = onColorFormatChange,
            gammaCurve = gammaCurve,
            onGammaCurveChange = onGammaCurveChange
        )

        SectionDivider()

        SdrSection(
            isSdrToneMapEnabled = isSdrToneMapEnabled,
            onSdrToneMapChange = onSdrToneMapChange,
            isForceDisplaySdrEnabled = isForceDisplaySdrEnabled,
            onForceDisplaySdrChange = onForceDisplaySdrChange
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
