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
import com.pilerks1.hdrrecorder.model.RecordingOptions
import com.pilerks1.hdrrecorder.model.VideoColorOption
import com.pilerks1.hdrrecorder.model.VideoEncoding
import com.pilerks1.hdrrecorder.ui.CameraUiState
import com.pilerks1.hdrrecorder.data.camera.toCameraXQuality
import com.pilerks1.hdrrecorder.data.diagnostics.IssueCode
import com.pilerks1.hdrrecorder.model.Resolution

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
            val capabilities = if (mime == null) Recorder.getVideoCapabilities(cameraInfo)
                else Recorder.getVideoCapabilities(cameraInfo, mime)
            colors(capabilities)
        }
        return RecordingOptions(
            video.filter { colors[it].orEmpty().isNotEmpty() },
            audio,
            colors.filterValues { it.isNotEmpty() }
        )
    }

    fun colors(capabilities: VideoCapabilities?): List<VideoColorOption> {
        val reported = capabilities?.supportedDynamicRanges.orEmpty().mapNotNull { range ->
            VideoEncoding.entries.firstOrNull { encodingValue(it) == range.encoding }
                ?.let { VideoColorOption(range.bitDepth, it) }
        }.filter { it.bitDepth == 8 || it.bitDepth == 10 }.distinct()
        return reported + reported.map { VideoColorOption(it.bitDepth, VideoEncoding.UNSPECIFIED) }.distinct()
    }

    fun dynamicRange(state: CameraUiState): DynamicRange = when (state.videoBitDepth) {
        8 -> DynamicRange(encodingValue(state.encoding), DynamicRange.BIT_DEPTH_8_BIT)
        10 -> DynamicRange(encodingValue(state.encoding), DynamicRange.BIT_DEPTH_10_BIT)
        else -> error("Unsupported bit depth: ${state.videoBitDepth}")
    }

    fun validate(state: CameraUiState, options: RecordingOptions) {
        requireSupported(state.videoCodec == null || state.videoCodec in options.videoCodecs, IssueCode.CONFIG_VIDEO_CODEC)
        requireSupported(state.audioCodec == null || state.audioCodec in options.audioCodecs, IssueCode.CONFIG_AUDIO_CODEC)
        requireSupported(state.videoBitDepth == 8 || state.videoBitDepth == 10, IssueCode.CONFIG_BIT_DEPTH)
        requireSupported(VideoColorOption(state.videoBitDepth, state.encoding) in options.colors(state.videoCodec),
            IssueCode.CONFIG_COLOR)
        requireSupported(state.bitrate.isEmpty() || Bitrate.parse(state.bitrate) != null, IssueCode.CONFIG_VIDEO_BITRATE)
        requireSupported(state.audioBitrate.isEmpty() || Bitrate.parseAudio(state.audioBitrate) != null, IssueCode.CONFIG_AUDIO_BITRATE)
    }

    /** Check only the requested configuration, before replacing a working camera session. */
    @OptIn(ExperimentalMimeTypeApi::class)
    fun validateSession(info: CameraInfo, state: CameraUiState, session: SessionConfig) {
        val caps = if (state.videoCodec == null) Recorder.getVideoCapabilities(info)
            else Recorder.getVideoCapabilities(info, state.videoCodec)
        requireSupported(caps != null, IssueCode.CONFIG_VIDEO_CODEC)
        requireSupported(isResolutionSupported(caps!!, state), IssueCode.CONFIG_RESOLUTION)
        if (state.isStabilizationEnabled) {
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
        if (!state.manualControlsState.isManualFps) {
            requireSupported(Range(state.selectedFps, state.selectedFps) in info.getSupportedFrameRateRanges(session),
                IssueCode.CONFIG_FRAME_RATE)
        }
    }

    fun isResolutionSupported(caps: VideoCapabilities, state: CameraUiState): Boolean {
        val range = dynamicRange(state)
        val quality = state.selectedResolution.toCameraXQuality()
        return if (state.selectedResolution == Resolution.HIGHEST || state.encoding == VideoEncoding.UNSPECIFIED)
            caps.isQualitySupported(quality, range) else quality in caps.getSupportedQualities(range)
    }

    fun diagnosticContext(state: CameraUiState): Map<String, String> = mapOf(
        "resolution" to state.selectedResolution.name,
        "fps" to if (state.manualControlsState.isManualFps) {
            state.manualControlsState.fpsRange?.let { if (it.lower == it.upper) "${it.upper}" else "${it.lower}–${it.upper}" } ?: "Auto"
        } else state.selectedFps.toString(),
        "encoding" to state.encoding.displayName,
        "bitDepth" to state.videoBitDepth.toString(),
        "videoCodec" to (state.videoCodec ?: "Auto"),
        "stabilization" to state.isStabilizationEnabled.toString()
    )

    private fun requireSupported(supported: Boolean, issue: IssueCode) {
        if (!supported) throw CameraConfigurationException(issue)
    }

    fun configure(builder: Recorder.Builder, state: CameraUiState): Recorder.Builder = builder.apply {
        state.videoCodec?.let(::setVideoMimeType)
        state.audioCodec?.let(::setAudioMimeType)
        Bitrate.parse(state.bitrate)?.let { setTargetVideoEncodingBitRate(it * 1_000_000) }
        Bitrate.parseAudio(state.audioBitrate)?.let { setTargetAudioEncodingBitRate(it * 1_000) }
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
