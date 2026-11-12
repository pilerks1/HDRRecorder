package com.pilerks1.hdrrecorder.ui.camera.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pilerks1.hdrrecorder.camera.model.VideoColorOption
import com.pilerks1.hdrrecorder.camera.model.VideoEncoding
import com.pilerks1.hdrrecorder.ui.style.SettingsDimensions

@Composable
fun ColorSettingsSection(
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

@Composable
fun VideoSettingsSection(
    videoCodec: String?,
    videoCodecs: List<String>,
    onVideoCodecChange: (String?) -> Unit,
    compatibleVideoCodecs: Set<String?>,
    bitrate: String,
    onBitrateChange: (String) -> Unit,
    isStabilizationEnabled: Boolean,
    onStabilizationChange: (Boolean) -> Unit
) {
    SectionTitle("Video")

    CodecRow("Video Codec", videoCodec, videoCodecs, onVideoCodecChange,
        isCompatible = { it in compatibleVideoCodecs })
    RecordingBitrateRow("Bitrate (Mbps)", bitrate, onBitrateChange)
    Spacer(modifier = Modifier.height(SettingsDimensions.smallGap))

    HdrSettingsSwitchRow(
        label = "Stabilization",
        checked = isStabilizationEnabled,
        onCheckedChange = onStabilizationChange
    )
}
