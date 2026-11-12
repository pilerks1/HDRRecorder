package com.pilerks1.hdrrecorder.ui.camera.settings

import android.hardware.camera2.CaptureRequest
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.pilerks1.hdrrecorder.camera.camera2.Camera2Control
import com.pilerks1.hdrrecorder.camera.camera2.Camera2ModeCapabilities
import com.pilerks1.hdrrecorder.camera.camera2.Camera2ModeOverride
import com.pilerks1.hdrrecorder.camera.camera2.Camera2Settings
import com.pilerks1.hdrrecorder.ui.style.SettingsDimensions
import com.pilerks1.hdrrecorder.ui.style.UiTextStyles

@Composable
fun Camera2SettingsSection(
    settings: Camera2Settings,
    capabilities: Camera2ModeCapabilities?,
    isManualFocus: Boolean,
    onOverrideEnabledChange: (Camera2Control, Boolean) -> Unit,
    onModeChange: (Camera2Control, Int) -> Unit,
    isExtendManualRangesEnabled: Boolean,
    onExtendManualRangesChange: (Boolean) -> Unit,
    isWaitToLockAeEnabled: Boolean,
    onWaitToLockAeChange: (Boolean) -> Unit
) {
    SectionTitle("Camera2")
    val modes = capabilities?.modes.orEmpty()
    Camera2Control.entries.forEach { control ->
        Spacer(Modifier.height(SettingsDimensions.controlSpacing))
        Camera2ModeRow(
            control = control,
            value = settings[control],
            modes = modes[control].orEmpty(),
            enabled = control != Camera2Control.CONTROL_AF_MODE || !isManualFocus,
            onEnabledChange = { onOverrideEnabledChange(control, it) },
            onModeChange = { onModeChange(control, it) }
        )
    }
    Spacer(Modifier.height(SettingsDimensions.sectionSpacing))
    HdrSettingsSwitchRow(
        label = "Extend Manual Ranges",
        description = "Accept larger ranges for manual SS and ISO",
        checked = isExtendManualRangesEnabled,
        onCheckedChange = onExtendManualRangesChange
    )
    Spacer(modifier = Modifier.height(SettingsDimensions.sectionSpacing))
    HdrSettingsSwitchRow(
        label = "Wait to Lock AE",
        description = "Read manual for further info",
        checked = isWaitToLockAeEnabled,
        onCheckedChange = onWaitToLockAeChange
    )
}

@Composable
private fun Camera2ModeRow(
    control: Camera2Control,
    value: Camera2ModeOverride,
    modes: List<Int>,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onModeChange: (Int) -> Unit
) {
    val available = enabled && modes.isNotEmpty()
    val dropdownEnabled = available && value.enabled
    val title = control.title
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.rowGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = UiTextStyles.Settings.rowTitleSize, color = if (available) Color.White else Color.Gray)
            control.description?.let {
                Text(it, fontSize = UiTextStyles.Settings.descriptionSize, lineHeight = UiTextStyles.Settings.descriptionLineHeight, color = Color.Gray)
            }
        }
        Switch(
            checked = value.enabled,
            onCheckedChange = onEnabledChange,
            enabled = available,
            modifier = Modifier.semantics { contentDescription = "$title override" },
            colors = settingsSwitchColors()
        )
        SettingsChoiceDropdown(
            value = value.mode?.let(control::modeName) ?: "—",
            choices = modes.map { it to control.modeName(it) },
            onSelect = onModeChange,
            modifier = Modifier.semantics { contentDescription = "$title mode" },
            enabled = dropdownEnabled
        )
    }
}

private val Camera2Control.description: String?
    get() = when (this) {
        Camera2Control.COLOR_CORRECTION_ABERRATION_MODE -> "Reduces colored fringes around edges"
        Camera2Control.CONTROL_AF_MODE -> "Autofocus behavior; manual focus takes priority"
        Camera2Control.DISTORTION_CORRECTION_MODE -> "Corrects lens distortion"
        Camera2Control.EDGE_MODE -> "Adjusts sharpening"
        Camera2Control.HOT_PIXEL_MODE -> "Corrects defective sensor pixels"
        Camera2Control.NOISE_REDUCTION_MODE -> null
        Camera2Control.SHADING_MODE -> "Corrects brightness falloff toward image corners"
    }

private fun Camera2Control.modeName(mode: Int): String = when {
    this == Camera2Control.CONTROL_AF_MODE -> when (mode) {
        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO -> "Continuous Video"
        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE -> "Continuous Picture"
        CaptureRequest.CONTROL_AF_MODE_EDOF -> "EDOF"
        else -> mode.toString()
    }
    this == Camera2Control.EDGE_MODE && mode == CaptureRequest.EDGE_MODE_ZERO_SHUTTER_LAG -> "Zero Shutter Lag"
    this == Camera2Control.NOISE_REDUCTION_MODE && mode == CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL -> "Minimal"
    this == Camera2Control.NOISE_REDUCTION_MODE && mode == CaptureRequest.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG -> "Zero Shutter Lag"
    else -> when (mode) {
        0 -> "Off"
        1 -> "Fast"
        2 -> "High Quality"
        else -> mode.toString()
    }
}
