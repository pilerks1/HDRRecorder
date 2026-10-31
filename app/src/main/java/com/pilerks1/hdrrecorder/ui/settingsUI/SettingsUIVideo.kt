package com.pilerks1.hdrrecorder.ui.settingsUI

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
@Composable
fun VideoSection(
    videoCodec: String?,
    videoCodecs: List<String>,
    onVideoCodecChange: (String?) -> Unit,
    compatibleVideoCodecs: Set<String?>,
    bitrate: String,
    onBitrateChange: (String) -> Unit,
    isStabilizationEnabled: Boolean,
    onStabilizationChange: (Boolean) -> Unit
) {
    Text(
        text = "Video",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    CodecRow("Video Codec", videoCodec, videoCodecs, onVideoCodecChange,
        isCompatible = { it in compatibleVideoCodecs })
    RecordingBitrateRow("Bitrate (Mbps)", bitrate, onBitrateChange)
    Spacer(modifier = Modifier.height(8.dp))

    HdrSettingsSwitchRow(
        label = "Stabilization",
        checked = isStabilizationEnabled,
        onCheckedChange = onStabilizationChange
    )
}
