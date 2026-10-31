package com.pilerks1.hdrrecorder.ui.settingsUI

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.pilerks1.hdrrecorder.model.GammaCurve
import com.pilerks1.hdrrecorder.model.GammaMode
import com.pilerks1.hdrrecorder.model.GammaPreset
import com.pilerks1.hdrrecorder.model.GammaSettings

@Composable
fun GammaSection(settings: GammaSettings, modes: List<GammaMode>, onChange: (GammaSettings) -> Unit) {
    Text("Gamma", fontSize = 14.sp, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
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
            Spacer(Modifier.height(8.dp))
            GammaValueRow(settings.value) { onChange(activeSettings.copy(value = it)) }
        }
        GammaMode.CONTRAST_CURVE -> {
            Spacer(Modifier.height(8.dp))
            RecordingChoiceRow(
                label = "Custom Curve",
                value = settings.curve.displayName,
                choices = GammaCurve.entries.filter { it != GammaCurve.AUTO }.map { it to it.displayName },
                onSelect = { onChange(activeSettings.copy(curve = it)) }
            )
        }
        GammaMode.PRESET_CURVE -> {
            Spacer(Modifier.height(8.dp))
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

@Composable
private fun GammaValueRow(value: Float, onChange: (Float) -> Unit) {
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        if (text.toFloatOrNull() != value) text = value.toString()
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text("Gamma Value", fontSize = 16.sp, color = Color.White, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                val gamma = input.toFloatOrNull()
                if (input.all { it.isDigit() || it == '.' } && gamma != null && gamma.isFinite() && gamma > 0f) {
                    text = input
                    onChange(gamma)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            textStyle = LocalTextStyle.current.copy(color = Color.White, textAlign = TextAlign.End),
            singleLine = true,
            modifier = Modifier.width(100.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.DarkGray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}
