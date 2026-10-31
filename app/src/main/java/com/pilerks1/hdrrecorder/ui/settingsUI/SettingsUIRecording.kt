package com.pilerks1.hdrrecorder.ui.settingsUI

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pilerks1.hdrrecorder.data.Bitrate

@Composable
fun <T> RecordingChoiceRow(
    label: String,
    value: String,
    choices: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    isCompatible: (T) -> Boolean = { true },
    enabled: Boolean = true
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 16.sp,
            color = if (enabled) Color.White else Color.Gray)
        SettingsChoiceDropdown(value, choices, onSelect, enabled = enabled, isCompatible = isCompatible)
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
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 16.sp, color = Color.White)
            Text("Target; CameraX may adjust", fontSize = 12.sp, lineHeight = 12.sp, color = Color.Gray)
        }
        OutlinedTextField(
            value = value,
            onValueChange = { input ->
                if (input.all { it.isDigit() } && (input.isEmpty() ||
                    (if (audio) Bitrate.parseAudio(input) else Bitrate.parse(input)) != null)) onChange(input)
            },
            placeholder = { Text("Auto", color = Color.Gray) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            textStyle = LocalTextStyle.current.copy(color = Color.White, textAlign = TextAlign.End),
            singleLine = true,
            modifier = Modifier.width(100.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
fun AudioSection(codec: String?, codecs: List<String>, onCodecChange: (String?) -> Unit,
    bitrate: String, onBitrateChange: (String) -> Unit) {
    Text("Audio", fontSize = 14.sp, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
    CodecRow("Audio Codec", codec, codecs, onCodecChange)
    RecordingBitrateRow("Bitrate (kbps)", bitrate, onBitrateChange, audio = true)
}
