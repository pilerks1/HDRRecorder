package com.pilerks1.hdrrecorder.model

data class VideoColorOption(val bitDepth: Int, val encoding: VideoEncoding)

/** Only combinations reported by CameraX, plus Unspecified for each reported depth. */
data class RecordingOptions(
    val videoCodecs: List<String> = emptyList(),
    val audioCodecs: List<String> = emptyList(),
    val colorsByVideoCodec: Map<String?, List<VideoColorOption>> = emptyMap()
) {
    fun colors(videoCodec: String?): List<VideoColorOption> = colorsByVideoCodec[videoCodec].orEmpty()

    fun allColors(): List<VideoColorOption> = colorsByVideoCodec.values.flatten().distinct()
}
