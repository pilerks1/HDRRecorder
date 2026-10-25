package com.pilerks1.hdrrecorder.model

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
        /** Accepts both stable IDs and the labels written by older app versions. */
        fun fromStorageId(value: String?): VideoEncoding? = when (value) {
            "dolby_vision_84", "DB 8.4" -> DOLBY_VISION
            "unspecified_10_bit", "Unspec" -> UNSPECIFIED
            else -> entries.firstOrNull { it.storageId == value || it.displayName == value }
        }
    }
}

/** Stable gamma-curve identifiers for UI state and persisted presets. */
enum class GammaCurve(val storageId: String, val displayName: String) {
    AUTO("auto", "Auto"),
    HLG("hlg", "HLG"),
    PQ("pq", "PQ"),
    CUSTOM("custom", "Custom");

    fun next(): GammaCurve = entries[(ordinal + 1) % entries.size]

    companion object {
        /** Accepts both stable IDs and the labels written by older app versions. */
        fun fromStorageId(value: String?): GammaCurve? = entries.firstOrNull {
            it.storageId == value || it.displayName == value
        }
    }
}
