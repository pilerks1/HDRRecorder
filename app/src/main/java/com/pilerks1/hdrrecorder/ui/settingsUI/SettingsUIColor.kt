package com.pilerks1.hdrrecorder.ui.settingsUI

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import com.pilerks1.hdrrecorder.model.VideoColorOption
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pilerks1.hdrrecorder.model.VideoEncoding
import com.pilerks1.hdrrecorder.model.GammaCurve

@Composable
fun ColorSection(
    encoding: VideoEncoding,
    onEncodingChange: (VideoEncoding) -> Unit,
    bitDepth: Int,
    colors: List<VideoColorOption>,
    compatibleColors: List<VideoColorOption>,
    onBitDepthChange: (Int) -> Unit,
    gammaCurve: GammaCurve,
    onGammaCurveChange: () -> Unit
) {
    Text(
        text = "Color",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    RecordingChoiceRow("Bit Depth", "$bitDepth bit",
        colors.map { it.bitDepth }.distinct().sorted().map { it to "$it bit" }, onBitDepthChange,
        isCompatible = { VideoColorOption(it, encoding) in compatibleColors })
    RecordingChoiceRow("Encoding", encoding.displayName,
        colors.map { it.encoding }.distinct().map { it to it.displayName }, onEncodingChange,
        isCompatible = { VideoColorOption(bitDepth, it) in compatibleColors })
    Spacer(modifier = Modifier.height(8.dp))
    // Gamma
    val isGammaEnabled = encoding.supportsGammaCurveSelection
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Gamma", fontSize = 16.sp, color = if (isGammaEnabled) Color.White else Color.Gray)
        Button(
            onClick = onGammaCurveChange,
            enabled = isGammaEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledContainerColor = Color.DarkGray,
                disabledContentColor = Color.Gray
            )
        ) {
            Text(text = gammaCurve.displayName)
        }
    }
}
