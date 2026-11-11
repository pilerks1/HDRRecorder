package com.pilerks1.hdrrecorder.ui.settingsUI

import com.pilerks1.hdrrecorder.model.VideoColorOption
import androidx.compose.runtime.Composable
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
    SectionTitle("Color")

    RecordingChoiceRow("Bit Depth", "$bitDepth-bit",
        colors.map { it.bitDepth }.distinct().sorted().map { it to "$it-bit" }, onBitDepthChange,
        isCompatible = { VideoColorOption(it, encoding) in compatibleColors })
    RecordingChoiceRow("Encoding", encoding.displayName,
        colors.map { it.encoding }.distinct().map { it to it.displayName }, onEncodingChange,
        isCompatible = { VideoColorOption(bitDepth, it) in compatibleColors })
}
