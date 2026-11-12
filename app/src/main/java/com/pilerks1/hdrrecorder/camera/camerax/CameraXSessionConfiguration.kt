package com.pilerks1.hdrrecorder.camera.camerax

import android.util.Range
import androidx.camera.core.*
import androidx.camera.core.CameraInfo
import androidx.camera.core.DynamicRange
import androidx.camera.core.Preview
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import com.pilerks1.hdrrecorder.camera.model.CaptureConfiguration
import com.pilerks1.hdrrecorder.camera.model.PreviewDynamicRange
import com.pilerks1.hdrrecorder.camera.model.PreviewOptions
import com.pilerks1.hdrrecorder.camera.model.PreviewSettings
import com.pilerks1.hdrrecorder.camera.model.RecordingOptions
import com.pilerks1.hdrrecorder.camera.model.Resolution

/** CameraX mapping and discovery for the independent preview preferences. */
object CameraXPreviewConfiguration {
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

/** The only translation between the app's resolution enum and CameraX's Quality type. */
fun Resolution.toCameraXQuality(): Quality = when (this) {
    Resolution.FHD -> Quality.FHD
    Resolution.QHD -> Quality.QHD
    Resolution.UHD -> Quality.UHD
    Resolution.HIGHEST -> Quality.HIGHEST
}

/** Builds unbound use cases shared by recording and capability queries. */
object CameraXSessionFactory {
    fun preview(config: CaptureConfiguration, rotation: Int): Preview =
        CameraXPreviewConfiguration.build(config.previewSettings, rotation, config.isStabilizationEnabled)

    /** The Recorder keeps CameraX's default executor, separate from capture-result processing. */
    fun video(config: CaptureConfiguration, rotation: Int): VideoCapture<Recorder> {
        val recorder = CameraXRecordingConfiguration.configure(
            Recorder.Builder()
                .setQualitySelector(QualitySelector.from(config.resolution.toCameraXQuality()))
                .setAspectRatio(config.aspectRatio.cameraXValue),
            config
        ).build()
        return VideoCapture.Builder(recorder)
            .setVideoStabilizationEnabled(config.isStabilizationEnabled)
            .setTargetRotation(rotation)
            .setDynamicRange(CameraXRecordingConfiguration.dynamicRange(config))
            .apply {
                if (config.manualFpsRange == null) setTargetFrameRate(Range(config.fps, config.fps))
            }.build()
    }
}

/** Construct the requested outputs without predicting whether the device will accept them. */
internal data class PreparedCameraXSession(
    val previewOptions: PreviewOptions,
    val configuration: CaptureConfiguration,
    val preview: Preview,
    val videoCapture: VideoCapture<Recorder>
)

internal object CameraXSessionPreparation {
    fun prepare(
        cameraInfo: CameraInfo,
        requested: CaptureConfiguration,
        displayRotation: Int,
        onRecordingOptions: (RecordingOptions) -> Unit
    ): PreparedCameraXSession {
        val previewOptions = CameraXPreviewConfiguration.queryOptions(cameraInfo)
        onRecordingOptions(CameraXRecordingConfiguration.queryOptions(cameraInfo))
        val video = CameraXSessionFactory.video(requested, displayRotation)
        val preview = CameraXSessionFactory.preview(requested, displayRotation)
        return PreparedCameraXSession(previewOptions, requested, preview, video)
    }
}
