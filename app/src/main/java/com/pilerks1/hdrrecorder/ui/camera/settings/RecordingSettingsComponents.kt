package com.pilerks1.hdrrecorder.ui.camera.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.pilerks1.hdrrecorder.camera.model.Bitrate
import com.pilerks1.hdrrecorder.ui.style.SettingsDimensions
import com.pilerks1.hdrrecorder.ui.style.UiTextStyles

@Composable
fun <T> RecordingChoiceRow(
    label: String,
    value: String,
    choices: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    isCompatible: (T) -> Boolean = { true },
    enabled: Boolean = true,
    textMaxWidth: Dp = SettingsDimensions.dropdownMaxWidth
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SettingsDimensions.rowGap),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontSize = UiTextStyles.Settings.rowTitleSize,
            color = if (enabled) Color.White else Color.Gray)
        SettingsChoiceDropdown(value, choices, onSelect, enabled = enabled,
            isCompatible = isCompatible, textMaxWidth = textMaxWidth)
    }
}

@Composable
fun CodecRow(label: String, selected: String?, codecs: List<String>, onSelect: (String?) -> Unit,
    isCompatible: (String?) -> Boolean = { true }) {
    RecordingChoiceRow(label,
        selected ?: "Auto",
        listOf(null to "Auto") + codecs.map { it to it }, onSelect, isCompatible)
}

@Composable
fun RecordingBitrateRow(label: String, value: String, onChange: (String) -> Unit, audio: Boolean = false) {
    val focusManager = LocalFocusManager.current
    Row(Modifier.fillMaxWidth().padding(vertical = SettingsDimensions.bitrateVerticalPadding),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = UiTextStyles.Settings.rowTitleSize, color = Color.White)
            Text("Target; CameraX may adjust", fontSize = UiTextStyles.Settings.descriptionSize, lineHeight = UiTextStyles.Settings.descriptionLineHeight, color = Color.Gray)
        }
        OutlinedTextField(
            value = value,
            onValueChange = { input ->
                if (input.all { it.isDigit() } &&
                    (if (audio) Bitrate.isValidAudio(input) else Bitrate.isValid(input))) onChange(input)
            },
            placeholder = { Text("Auto", color = Color.Gray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            textStyle = LocalTextStyle.current.copy(color = Color.White, textAlign = TextAlign.End),
            singleLine = true,
            modifier = Modifier.width(SettingsDimensions.inputWidth),
            colors = settingsTextFieldColors()
        )
    }
}

@Composable
fun AudioSettingsSection(codec: String?, codecs: List<String>, onCodecChange: (String?) -> Unit,
    bitrate: String, onBitrateChange: (String) -> Unit) {
    SectionTitle("Audio")
    CodecRow("Audio Codec", codec, codecs, onCodecChange)
    RecordingBitrateRow("Bitrate (kbps)", bitrate, onBitrateChange, audio = true)
}
