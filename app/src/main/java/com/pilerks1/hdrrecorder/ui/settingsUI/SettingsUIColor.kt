package com.pilerks1.hdrrecorder.ui.settingsUI

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import com.pilerks1.hdrrecorder.model.VideoColorOption
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pilerks1.hdrrecorder.model.VideoEncoding

@Composable
fun ColorSection(
    encoding: VideoEncoding,
    onEncodingChange: (VideoEncoding) -> Unit,
    bitDepth: Int,
    colors: List<VideoColorOption>,
    compatibleColors: List<VideoColorOption>,
    onBitDepthChange: (Int) -> Unit
) {
    Text(
        text = "Color",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    RecordingChoiceRow("Bit Depth", bitDepth.toString(),
        colors.map { it.bitDepth }.distinct().sorted().map { it to it.toString() }, onBitDepthChange,
        isCompatible = { VideoColorOption(it, encoding) in compatibleColors })
    RecordingChoiceRow("Encoding", encoding.displayName,
        colors.map { it.encoding }.distinct().map { it to it.displayName }, onEncodingChange,
        isCompatible = { VideoColorOption(bitDepth, it) in compatibleColors })
}
