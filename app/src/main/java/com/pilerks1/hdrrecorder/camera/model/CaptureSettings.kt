package com.pilerks1.hdrrecorder.camera.model

import android.util.Range
import androidx.camera.core.AspectRatio

/**
 * Everything that determines the bound CameraX session, independent of screen state.
 * [manualFpsRange] is non-null only while FPS is manual, in which case [fps] is not requested.
 */
data class CaptureConfiguration(
    val fps: Int = 30,
    val manualFpsRange: Range<Int>? = null,
    val resolution: Resolution = Resolution.FHD,
    val aspectRatio: RecordingAspectRatio = RecordingAspectRatio.FOUR_THREE,
    val encoding: VideoEncoding = VideoEncoding.HLG,
    val bitDepth: Int = 10,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val bitrate: String = "",
    val audioBitrate: String = "",
    val isStabilizationEnabled: Boolean = true,
    val previewSettings: PreviewSettings = PreviewSettings(),
    val isExtendManualRangesEnabled: Boolean = false,
    val zoomRatio: Float = 1f
)

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

/** Recording and preview aspect ratios exposed by the camera UI. */
enum class RecordingAspectRatio(
    val displayLabel: String,
    val storageId: String,
    val width: Int,
    val height: Int,
    val cameraXValue: Int
) {
    FOUR_THREE("4:3", "4_3", 4, 3, AspectRatio.RATIO_4_3),
    SIXTEEN_NINE("16:9", "16_9", 16, 9, AspectRatio.RATIO_16_9);

    val landscapeRatio: Float
        get() = width.toFloat() / height.toFloat()

    fun next(): RecordingAspectRatio = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromStorageId(value: String?): RecordingAspectRatio? = entries.firstOrNull { it.storageId == value }
    }
}

/**
 * Defines the available video recording resolutions.
 * This enum provides a type-safe way to represent resolution options,
 * preventing the use of error-prone strings.
 */
enum class Resolution(val qualityName: String, val storageId: String) {
    FHD("FHD", "fhd"),
    QHD("QHD", "qhd"),
    UHD("UHD", "uhd"),
    HIGHEST("MAX", "highest");

    fun next(): Resolution = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromStorageId(value: String?): Resolution? = entries.firstOrNull { it.storageId == value }
    }
}
