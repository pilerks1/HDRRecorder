package com.pilerks1.hdrrecorder.ui.settingsUI

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Camera2Section(
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
