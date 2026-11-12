package com.pilerks1.hdrrecorder.ui.camera.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import com.pilerks1.hdrrecorder.camera.camera2.GammaMode
import com.pilerks1.hdrrecorder.camera.camera2.GammaPreset
import com.pilerks1.hdrrecorder.camera.camera2.GammaSettings
import com.pilerks1.hdrrecorder.camera.model.GammaCurve
import com.pilerks1.hdrrecorder.ui.style.SettingsDimensions
import com.pilerks1.hdrrecorder.ui.style.UiTextStyles

@Composable
fun GammaSettingsSection(settings: GammaSettings, modes: List<GammaMode>, onChange: (GammaSettings) -> Unit) {
    SectionTitle("Gamma")
    val mode = settings.supportedMode(modes)
    RecordingChoiceRow(
        label = "Mode",
        value = mode?.displayName ?: "Unavailable",
        choices = modes.map { it to it.displayName },
        onSelect = { onChange(settings.copy(mode = it)) },
        enabled = modes.isNotEmpty()
    )
    if (mode == null) return
    val activeSettings = settings.copy(mode = mode)
    when (mode) {
        GammaMode.GAMMA_VALUE -> {
            Spacer(Modifier.height(SettingsDimensions.smallGap))
            GammaValueRow(settings.value) { onChange(activeSettings.copy(value = it)) }
        }
        GammaMode.CONTRAST_CURVE -> {
            Spacer(Modifier.height(SettingsDimensions.smallGap))
            RecordingChoiceRow(
                label = "Custom Curve",
                value = settings.curve.displayName,
                choices = GammaCurve.entries.map { it to it.displayName },
                onSelect = { onChange(activeSettings.copy(curve = it)) }
            )
        }
        GammaMode.PRESET_CURVE -> {
            Spacer(Modifier.height(SettingsDimensions.smallGap))
            RecordingChoiceRow(
                label = "Preset Curve",
                value = settings.preset.displayName,
                choices = GammaPreset.entries.map { it to it.displayName },
                onSelect = { onChange(activeSettings.copy(preset = it)) }
            )
        }
        GammaMode.FAST, GammaMode.HIGH_QUALITY -> Unit
    }
}

private fun validGamma(text: String): Float? = text.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }

/**
 * The text is a local draft so it can be cleared or hold intermediate input such as "0.".
 * Only valid drafts update the setting; an invalid draft reverts to the setting on focus loss.
 */
@Composable
private fun GammaValueRow(value: Float, onChange: (Float) -> Unit) {
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        if (validGamma(text) != value) text = value.toString()
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text("Gamma Value", fontSize = UiTextStyles.Settings.rowTitleSize, color = Color.White, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                if (input.all { it.isDigit() || it == '.' }) {
                    text = input
                    validGamma(input)?.let(onChange)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            textStyle = LocalTextStyle.current.copy(color = Color.White, textAlign = TextAlign.End),
            singleLine = true,
            modifier = Modifier
                .width(SettingsDimensions.inputWidth)
                .onFocusChanged { if (!it.isFocused && validGamma(text) == null) text = value.toString() },
            colors = settingsTextFieldColors()
        )
    }
}
