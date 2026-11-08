package com.pilerks1.hdrrecorder.data

import androidx.camera.core.CameraInfo
import androidx.camera.core.DynamicRange
import androidx.camera.core.Preview
import com.pilerks1.hdrrecorder.model.PreviewDynamicRange
import com.pilerks1.hdrrecorder.model.PreviewOptions
import com.pilerks1.hdrrecorder.model.PreviewSettings

/** CameraX mapping and discovery for the independent preview preferences. */
object PreviewConfiguration {
    private val dynamicRanges = linkedMapOf(
        PreviewDynamicRange.SDR to DynamicRange.SDR,
        PreviewDynamicRange.HLG to DynamicRange.HLG_10_BIT,
        PreviewDynamicRange.HDR10 to DynamicRange.HDR10_10_BIT,
        PreviewDynamicRange.HDR10_PLUS to DynamicRange.HDR10_PLUS_10_BIT,
        PreviewDynamicRange.DOLBY_VISION_8_BIT to DynamicRange.DOLBY_VISION_8_BIT,
        PreviewDynamicRange.DOLBY_VISION_10_BIT to DynamicRange.DOLBY_VISION_10_BIT
    )

    fun queryOptions(cameraInfo: CameraInfo): PreviewOptions {
        val supported = cameraInfo.querySupportedDynamicRanges(dynamicRanges.values.toSet())
        return PreviewOptions(
            frameRates = cameraInfo.supportedFrameRateRanges
                .sortedWith(compareBy({ it.lower }, { it.upper })),
            dynamicRanges = dynamicRanges.filterValues { it in supported }.keys.toList()
        )
    }

    fun build(settings: PreviewSettings, rotation: Int, stabilization: Boolean): Preview =
        Preview.Builder()
            .setTargetRotation(rotation)
            .setPreviewStabilizationEnabled(stabilization)
            .apply {
                settings.frameRate?.let(::setTargetFrameRate)
                settings.dynamicRange?.let { setDynamicRange(dynamicRanges.getValue(it)) }
            }
            .build()
}
