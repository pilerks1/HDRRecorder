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
