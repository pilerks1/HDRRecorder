package com.pilerks1.hdrrecorder.model

import android.util.Range

/** Null selections leave the corresponding Preview.Builder option unset. */
data class PreviewSettings(
    val frameRate: Range<Int>? = null,
    val dynamicRange: PreviewDynamicRange? = null
)

enum class PreviewDynamicRange(val storageId: String, val displayName: String) {
    SDR("sdr", "SDR 8-bit"),
    HLG("hlg", "HLG 10-bit"),
    HDR10("hdr10", "HDR10 10-bit"),
    HDR10_PLUS("hdr10_plus", "HDR10+ 10-bit"),
    DOLBY_VISION_8_BIT("dolby_vision_8", "Dolby Vision 8-bit"),
    DOLBY_VISION_10_BIT("dolby_vision_10", "Dolby Vision 10-bit");

    companion object {
        fun fromStorageId(value: String?): PreviewDynamicRange? =
            entries.firstOrNull { it.storageId == value }
    }
}

data class PreviewOptions(
    val frameRates: List<Range<Int>> = emptyList(),
    val dynamicRanges: List<PreviewDynamicRange> = emptyList()
) {
    fun reconcile(settings: PreviewSettings): PreviewSettings = settings.copy(
        frameRate = settings.frameRate?.takeIf { it in frameRates },
        dynamicRange = settings.dynamicRange?.takeIf { it in dynamicRanges }
    )
}
