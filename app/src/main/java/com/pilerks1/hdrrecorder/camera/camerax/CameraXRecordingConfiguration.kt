package com.pilerks1.hdrrecorder.camera.camerax

import androidx.annotation.OptIn
import androidx.camera.core.CameraInfo
import androidx.camera.core.DynamicRange
import androidx.camera.video.ExperimentalMimeTypeApi
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapabilities
import com.pilerks1.hdrrecorder.camera.model.Bitrate
import com.pilerks1.hdrrecorder.camera.model.CaptureConfiguration
import com.pilerks1.hdrrecorder.camera.model.RecordingOptions
import com.pilerks1.hdrrecorder.camera.model.Resolution
import com.pilerks1.hdrrecorder.camera.model.VideoColorOption
import com.pilerks1.hdrrecorder.camera.model.VideoEncoding

/** CameraX capability discovery and recording configuration share the same encoding mapping. */
object CameraXRecordingConfiguration {
    @OptIn(ExperimentalMimeTypeApi::class)
    fun queryOptions(cameraInfo: CameraInfo): RecordingOptions {
        val video = Recorder.getSupportedVideoMimeTypes().distinct()
        val audio = Recorder.getSupportedAudioMimeTypes().distinct()
        val colors = (listOf<String?>(null) + video).associateWith { mime ->
            colors(videoCapabilities(cameraInfo, mime))
        }
        return RecordingOptions(
            video.filter { colors[it].orEmpty().isNotEmpty() },
            audio,
            colors.filterValues { it.isNotEmpty() }
        )
    }

    /** Null [mimeType] selects CameraX's automatic codec. */
    @OptIn(ExperimentalMimeTypeApi::class)
    fun videoCapabilities(cameraInfo: CameraInfo, mimeType: String?): VideoCapabilities? =
        if (mimeType == null) Recorder.getVideoCapabilities(cameraInfo)
        else Recorder.getVideoCapabilities(cameraInfo, mimeType)

    fun colors(capabilities: VideoCapabilities?): List<VideoColorOption> {
        val reported = capabilities?.supportedDynamicRanges.orEmpty().mapNotNull { range ->
            VideoEncoding.entries.firstOrNull { encodingValue(it) == range.encoding }
                ?.let { VideoColorOption(range.bitDepth, it) }
        }.filter { it.bitDepth == 8 || it.bitDepth == 10 }.distinct()
        return reported + reported.map { VideoColorOption(it.bitDepth, VideoEncoding.UNSPECIFIED) }.distinct()
    }

    fun dynamicRange(config: CaptureConfiguration): DynamicRange = when (config.bitDepth) {
        8 -> DynamicRange(encodingValue(config.encoding), DynamicRange.BIT_DEPTH_8_BIT)
        10 -> DynamicRange(encodingValue(config.encoding), DynamicRange.BIT_DEPTH_10_BIT)
        else -> error("Unsupported bit depth: ${config.bitDepth}")
    }

    fun isResolutionSupported(caps: VideoCapabilities, config: CaptureConfiguration): Boolean {
        val range = dynamicRange(config)
        val quality = config.resolution.toCameraXQuality()
        return if (config.resolution == Resolution.HIGHEST || config.encoding == VideoEncoding.UNSPECIFIED)
            caps.isQualitySupported(quality, range) else quality in caps.getSupportedQualities(range)
    }

    fun diagnosticContext(config: CaptureConfiguration): Map<String, String> = mapOf(
        "resolution" to config.resolution.name,
        "fps" to (config.manualFpsRange?.let { if (it.lower == it.upper) "${it.upper}" else "${it.lower}–${it.upper}" }
            ?: config.fps.toString()),
        "encoding" to config.encoding.displayName,
        "bitDepth" to config.bitDepth.toString(),
        "videoCodec" to (config.videoCodec ?: "Auto"),
        "stabilization" to config.isStabilizationEnabled.toString()
    )

    fun configure(builder: Recorder.Builder, config: CaptureConfiguration): Recorder.Builder = builder.apply {
        config.videoCodec?.let(::setVideoMimeType)
        config.audioCodec?.let(::setAudioMimeType)
        Bitrate.parse(config.bitrate)?.let { setTargetVideoEncodingBitRate(it * 1_000_000) }
        Bitrate.parseAudio(config.audioBitrate)?.let { setTargetAudioEncodingBitRate(it * 1_000) }
    }

    private fun encodingValue(encoding: VideoEncoding): Int = when (encoding) {
        VideoEncoding.SDR -> DynamicRange.ENCODING_SDR
        VideoEncoding.HLG -> DynamicRange.ENCODING_HLG
        VideoEncoding.HDR10 -> DynamicRange.ENCODING_HDR10
        VideoEncoding.HDR10_PLUS -> DynamicRange.ENCODING_HDR10_PLUS
        VideoEncoding.DOLBY_VISION -> DynamicRange.ENCODING_DOLBY_VISION
        VideoEncoding.UNSPECIFIED -> DynamicRange.ENCODING_UNSPECIFIED
    }
}
