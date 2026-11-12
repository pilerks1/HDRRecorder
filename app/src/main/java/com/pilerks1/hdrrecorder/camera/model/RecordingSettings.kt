package com.pilerks1.hdrrecorder.camera.model



/** The authoritative bitrate rules: video in Mbps, audio in kbps; empty text means Auto. */
object Bitrate {
    const val MAX_MBPS = 999

    fun parse(value: String): Int? = value.toIntOrNull()?.takeIf { it in 1..MAX_MBPS }

    fun parseAudio(value: String): Int? = value.toIntOrNull()?.takeIf { it in 1..Int.MAX_VALUE / 1_000 }

    fun isValid(value: String): Boolean = value.isEmpty() || parse(value) != null

    fun isValidAudio(value: String): Boolean = value.isEmpty() || parseAudio(value) != null
}

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

/** Stable encoding identifiers for UI state and persisted presets. */
enum class VideoEncoding(val storageId: String, val displayName: String) {
    SDR("sdr", "SDR"),
    HLG("hlg", "HLG"),
    HDR10("hdr10", "HDR10"),
    HDR10_PLUS("hdr10_plus", "HDR10+"),
    DOLBY_VISION("dolby_vision", "DV"),
    UNSPECIFIED("unspecified", "Unspecified");

    val supportsGammaCurveSelection: Boolean
        get() = this == UNSPECIFIED

    companion object {
        fun fromStorageId(value: String?): VideoEncoding? = entries.firstOrNull { it.storageId == value }
    }
}

/** Stable gamma-curve identifiers for UI state and persisted presets. */
enum class GammaCurve(val storageId: String, val displayName: String) {
    HLG("hlg", "HLG"),
    PQ("pq", "PQ"),
    CUSTOM("custom", "Custom");

    companion object {
        fun fromStorageId(value: String?): GammaCurve? = entries.firstOrNull { it.storageId == value }
    }
}
