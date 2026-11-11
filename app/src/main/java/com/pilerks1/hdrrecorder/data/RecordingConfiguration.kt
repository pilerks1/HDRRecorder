package com.pilerks1.hdrrecorder.data

import android.util.Range
import androidx.annotation.OptIn
import androidx.camera.core.CameraInfo
import androidx.camera.core.DynamicRange
import androidx.camera.core.Preview
import androidx.camera.core.SessionConfig
import androidx.camera.video.ExperimentalMimeTypeApi
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapabilities
import com.pilerks1.hdrrecorder.model.PreviewSettings
import com.pilerks1.hdrrecorder.model.RecordingAspectRatio
import com.pilerks1.hdrrecorder.model.RecordingOptions
import com.pilerks1.hdrrecorder.model.VideoColorOption
import com.pilerks1.hdrrecorder.model.VideoEncoding
import com.pilerks1.hdrrecorder.data.camera.toCameraXQuality
import com.pilerks1.hdrrecorder.data.diagnostics.IssueCode
import com.pilerks1.hdrrecorder.model.Resolution

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

/** A known configuration rejection, independent of platform exception wording. */
class CameraConfigurationException(val issue: IssueCode, cause: Throwable? = null) :
    IllegalArgumentException(issue.name, cause)

/** CameraX capability discovery and recording configuration share the same encoding mapping. */
object RecordingConfiguration {
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

    fun validate(config: CaptureConfiguration, options: RecordingOptions) {
        requireSupported(config.videoCodec == null || config.videoCodec in options.videoCodecs, IssueCode.CONFIG_VIDEO_CODEC)
        requireSupported(config.audioCodec == null || config.audioCodec in options.audioCodecs, IssueCode.CONFIG_AUDIO_CODEC)
        requireSupported(config.bitDepth == 8 || config.bitDepth == 10, IssueCode.CONFIG_BIT_DEPTH)
        requireSupported(VideoColorOption(config.bitDepth, config.encoding) in options.colors(config.videoCodec),
            IssueCode.CONFIG_COLOR)
        requireSupported(Bitrate.isValid(config.bitrate), IssueCode.CONFIG_VIDEO_BITRATE)
        requireSupported(Bitrate.isValidAudio(config.audioBitrate), IssueCode.CONFIG_AUDIO_BITRATE)
    }

    /** Check only the requested configuration, before replacing a working camera session. */
    fun validateSession(info: CameraInfo, config: CaptureConfiguration, session: SessionConfig) {
        val caps = videoCapabilities(info, config.videoCodec)
            ?: throw CameraConfigurationException(IssueCode.CONFIG_VIDEO_CODEC)
        requireSupported(isResolutionSupported(caps, config), IssueCode.CONFIG_RESOLUTION)
        if (config.isStabilizationEnabled) {
            requireSupported(caps.isStabilizationSupported && Preview.getPreviewCapabilities(info).isStabilizationSupported,
                IssueCode.CONFIG_STABILIZATION)
        }
        val supported = try {
            info.isSessionConfigSupported(session)
        } catch (error: IllegalArgumentException) {
            // CameraX documents conflicting session options as IllegalArgumentException.
            throw CameraConfigurationException(IssueCode.CONFIG_COMBINATION, error)
        }
        requireSupported(supported, IssueCode.CONFIG_COMBINATION)
        if (config.manualFpsRange == null) {
            requireSupported(Range(config.fps, config.fps) in info.getSupportedFrameRateRanges(session),
                IssueCode.CONFIG_FRAME_RATE)
        }
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

    private fun requireSupported(supported: Boolean, issue: IssueCode) {
        if (!supported) throw CameraConfigurationException(issue)
    }

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
