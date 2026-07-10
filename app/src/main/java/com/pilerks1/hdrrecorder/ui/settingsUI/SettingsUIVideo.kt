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
fun VideoSection(
    bitrate: String,
    onBitrateChange: (String) -> Unit,
    noiseReductionEnabled: Boolean,
    onNoiseReductionChange: (Boolean) -> Unit,
    isStabilizationEnabled: Boolean,
    onStabilizationChange: (Boolean) -> Unit
) {
    val focusManager = LocalFocusManager.current

    Text(
        text = "Video",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    // Bitrate
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Bitrate (Mbps)", fontSize = 16.sp, color = Color.White)
        }
        OutlinedTextField(
            value = bitrate,
            onValueChange = { newValue ->
                if (newValue.all { it.isDigit() } && (newValue.isEmpty() || Bitrate.parse(newValue) != null)) {
                    onBitrateChange(newValue)
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            ),
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

    Spacer(modifier = Modifier.height(8.dp))

    HdrSettingsSwitchRow(
        label = "Noise Reduction",
        checked = noiseReductionEnabled,
        onCheckedChange = onNoiseReductionChange
    )

    Spacer(modifier = Modifier.height(8.dp))

    HdrSettingsSwitchRow(
        label = "Stabilization",
        checked = isStabilizationEnabled,
        onCheckedChange = onStabilizationChange
    )
}
