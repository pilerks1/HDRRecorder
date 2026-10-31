package com.pilerks1.hdrrecorder.ui.settingsUI

import android.hardware.camera2.CaptureRequest
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pilerks1.hdrrecorder.data.camera.core.Camera2CapabilitySource
import com.pilerks1.hdrrecorder.data.camera.core.Camera2Control
import com.pilerks1.hdrrecorder.data.camera.core.Camera2ModeCapabilities
import com.pilerks1.hdrrecorder.data.camera.core.Camera2ModeOverride
import com.pilerks1.hdrrecorder.data.camera.core.Camera2Settings

@Composable
fun Camera2Section(
    settings: Camera2Settings,
    capabilities: Camera2ModeCapabilities?,
    isManualFocus: Boolean,
    onCapabilitySourceChange: (Camera2CapabilitySource) -> Unit,
    onOverrideEnabledChange: (Camera2Control, Boolean) -> Unit,
    onModeChange: (Camera2Control, Int) -> Unit,
    isExtendManualRangesEnabled: Boolean,
    onExtendManualRangesChange: (Boolean) -> Unit,
    isWaitToLockAeEnabled: Boolean,
    onWaitToLockAeChange: (Boolean) -> Unit
) {
    Text(
        text = "Camera2",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    val sources = listOf(
        Camera2CapabilitySource.MAIN_CAMERA to "Main",
        Camera2CapabilitySource.COMMON_REAR_CAMERAS to "Common"
    )
    RecordingChoiceRow(
        label = "Options From",
        value = sources.first { it.first == settings.capabilitySource }.second,
        choices = sources,
        onSelect = onCapabilitySourceChange
    )
    val modes = capabilities?.modes(settings.capabilitySource).orEmpty()
    Camera2Control.entries.forEach { control ->
        Spacer(Modifier.height(12.dp))
        Camera2ModeRow(
            control = control,
            value = settings[control],
            modes = modes[control].orEmpty(),
            enabled = control != Camera2Control.CONTROL_AF_MODE || !isManualFocus,
            onEnabledChange = { onOverrideEnabledChange(control, it) },
            onModeChange = { onModeChange(control, it) }
        )
    }
    Spacer(Modifier.height(16.dp))
    HdrSettingsSwitchRow(
        label = "Extend Manual Ranges",
        description = "Accept larger ranges for manual SS and ISO",
        checked = isExtendManualRangesEnabled,
        onCheckedChange = onExtendManualRangesChange
    )
    Spacer(modifier = Modifier.height(16.dp))
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = if (available) Color.White else Color.Gray)
            control.description?.let {
                Text(it, fontSize = 12.sp, lineHeight = 12.sp, color = Color.Gray)
            }
        }
        Switch(
            checked = value.enabled,
            onCheckedChange = onEnabledChange,
            enabled = available,
            modifier = Modifier.semantics { contentDescription = "$title override" },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                uncheckedThumbColor = Color.LightGray,
                uncheckedTrackColor = Color.DarkGray,
                uncheckedBorderColor = Color.LightGray
            )
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

private val Camera2Control.title: String
    get() = when (this) {
        Camera2Control.COLOR_CORRECTION_ABERRATION_MODE -> "Aberration"
        Camera2Control.CONTROL_AF_MODE -> "Control AF"
        Camera2Control.DISTORTION_CORRECTION_MODE -> "Distortion"
        Camera2Control.EDGE_MODE -> "Sharpening"
        Camera2Control.HOT_PIXEL_MODE -> "Hot Pixel"
        Camera2Control.NOISE_REDUCTION_MODE -> "Noise"
        Camera2Control.SHADING_MODE -> "Shading"
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
