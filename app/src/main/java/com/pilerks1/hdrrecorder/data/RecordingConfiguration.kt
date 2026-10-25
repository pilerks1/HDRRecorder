package com.pilerks1.hdrrecorder.data

import androidx.annotation.OptIn
import androidx.camera.core.CameraInfo
import androidx.camera.core.DynamicRange
import androidx.camera.video.ExperimentalMimeTypeApi
import androidx.camera.video.Recorder
import com.pilerks1.hdrrecorder.model.RecordingOptions
import com.pilerks1.hdrrecorder.model.VideoColorOption
import com.pilerks1.hdrrecorder.model.VideoEncoding
import com.pilerks1.hdrrecorder.ui.CameraUiState

/** CameraX capability discovery and recording configuration share the same encoding mapping. */
object RecordingConfiguration {
    @OptIn(ExperimentalMimeTypeApi::class)
    fun queryOptions(cameraInfo: CameraInfo): RecordingOptions {
        val video = Recorder.getSupportedVideoMimeTypes().distinct()
        val audio = Recorder.getSupportedAudioMimeTypes().distinct()
        val colors = (listOf<String?>(null) + video).associateWith { mime ->
            val capabilities = if (mime == null) Recorder.getVideoCapabilities(cameraInfo)
                else Recorder.getVideoCapabilities(cameraInfo, mime)
            val reported = capabilities?.supportedDynamicRanges.orEmpty().mapNotNull { range ->
                VideoEncoding.entries.firstOrNull { encodingValue(it) == range.encoding }
                    ?.let { VideoColorOption(range.bitDepth, it) }
            }.filter { it.bitDepth == 8 || it.bitDepth == 10 }.distinct()
            reported + reported.map { VideoColorOption(it.bitDepth, VideoEncoding.UNSPECIFIED) }.distinct()
        }
        return RecordingOptions(
            video.filter { colors[it].orEmpty().isNotEmpty() },
            audio,
            colors.filterValues { it.isNotEmpty() }
        )
    }

    fun dynamicRange(state: CameraUiState): DynamicRange = when (state.videoBitDepth) {
        8 -> DynamicRange(encodingValue(state.encoding), DynamicRange.BIT_DEPTH_8_BIT)
        10 -> DynamicRange(encodingValue(state.encoding), DynamicRange.BIT_DEPTH_10_BIT)
        else -> error("Unsupported bit depth: ${state.videoBitDepth}")
    }

    fun validate(state: CameraUiState, options: RecordingOptions) {
        require(state.videoCodec == null || state.videoCodec in options.videoCodecs) {
            "Video codec ${state.videoCodec} is not supported by this device"
        }
        require(state.audioCodec == null || state.audioCodec in options.audioCodecs) {
            "Audio codec ${state.audioCodec} is not supported by this device"
        }
        require(VideoColorOption(state.videoBitDepth, state.encoding) in options.colors(state.videoCodec)) {
            "The selected video codec does not support ${state.videoBitDepth} bit ${state.encoding.displayName}"
        }
        require(state.bitrate.isEmpty() || Bitrate.parse(state.bitrate) != null) { "Invalid video bitrate" }
        require(state.audioBitrate.isEmpty() || Bitrate.parseAudio(state.audioBitrate) != null) { "Invalid audio bitrate" }
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
