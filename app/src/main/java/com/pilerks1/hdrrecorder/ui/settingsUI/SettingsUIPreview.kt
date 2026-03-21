package com.pilerks1.hdrrecorder.ui.settingsUI

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SdrSection(
    isSdrToneMapEnabled: Boolean,
    onSdrToneMapChange: (Boolean) -> Unit,
    isForceDisplaySdrEnabled: Boolean,
    onForceDisplaySdrChange: (Boolean) -> Unit
) {
    Text(
        text = "SDR Preview Options",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    HdrSettingsSwitchRow(
        label = "SDR Tone Map",
        description = "Forces SDR in preview builder",
        checked = isSdrToneMapEnabled,
        onCheckedChange = onSdrToneMapChange
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        HdrSettingsSwitchRow(
            label = "Force Display SDR",
            description = "Limits HDR headroom (Android 15+)",
            checked = isForceDisplaySdrEnabled,
            onCheckedChange = onForceDisplaySdrChange
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}