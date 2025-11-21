package com.pilerks1.hdrrecorder.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Settings UI overlay.
 * Full screen black background style with compact controls.
 */
@Composable
fun SettingsUI(
    gammaMode: String,
    onGammaChange: () -> Unit,
    noiseReductionEnabled: Boolean,
    onNoiseReductionChange: (Boolean) -> Unit,

    // SDR Hacks
    isSdrToneMapEnabled: Boolean,
    onSdrToneMapChange: (Boolean) -> Unit,
    isForceDisplaySdrEnabled: Boolean,
    onForceDisplaySdrChange: (Boolean) -> Unit,

    onNavigateToCompatibility: () -> Unit,
    onClose: () -> Unit
) {
    // Full screen container with black background
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(enabled = true) {} // Consume clicks to prevent pass-through
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // --- Header ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Existing Settings ---

        // Gamma Mode
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Gamma Mode",
                fontSize = 16.sp,
                color = Color.White
            )
            Button(
                onClick = onGammaChange,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(text = gammaMode)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Noise Reduction
        SettingsSwitchRow(
            label = "Noise Reduction",
            checked = noiseReductionEnabled,
            onCheckedChange = onNoiseReductionChange
        )

        Divider(modifier = Modifier.padding(vertical = 16.dp), color = Color.DarkGray)

        // --- SDR Hacks ---
        Text(
            text = "SDR Preview Options",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Hack 1: SDR Tone Map
        SettingsSwitchRow(
            label = "SDR Tone Map",
            description = "Forces SDR in preview builder",
            checked = isSdrToneMapEnabled,
            onCheckedChange = onSdrToneMapChange
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Hack 2: Force Display SDR (Android 15+ only)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            SettingsSwitchRow(
                label = "Force Display SDR",
                description = "Limits HDR headroom (Android 15+)",
                checked = isForceDisplaySdrEnabled,
                onCheckedChange = onForceDisplaySdrChange
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- Navigation ---
        OutlinedButton(
            onClick = {
                onClose()
                onNavigateToCompatibility()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text("Check Device Compatibility")
        }
    }
}

@Composable
fun SettingsSwitchRow(
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 16.sp,
                color = Color.White
            )
            if (description != null) {
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                uncheckedThumbColor = Color.LightGray,
                uncheckedTrackColor = Color.DarkGray,
                uncheckedBorderColor = Color.LightGray
            )
        )
    }
}