package com.pilerks1.hdrrecorder.data.diagnostics

import com.pilerks1.hdrrecorder.R

enum class IssueSeverity { INFO, WARNING, ERROR }

enum class IssueCode(
    @androidx.annotation.StringRes val message: Int,
    val severity: IssueSeverity = IssueSeverity.ERROR,
    val includeSettings: Boolean = false
) {
    CAMERA_START(R.string.issue_camera_start),
    CAMERA_RUNTIME(R.string.issue_camera_runtime),
    CAMERA_CONFIGURATION(R.string.issue_camera_configuration, includeSettings = true),
    CAMERA_SETUP(R.string.issue_camera_setup),
    CONFIG_VIDEO_CODEC(R.string.issue_config_video_codec, includeSettings = true),
    CONFIG_AUDIO_CODEC(R.string.issue_config_audio_codec, includeSettings = true),
    CONFIG_BIT_DEPTH(R.string.issue_config_bit_depth, includeSettings = true),
    CONFIG_COLOR(R.string.issue_config_color, includeSettings = true),
    CONFIG_VIDEO_BITRATE(R.string.issue_config_video_bitrate, includeSettings = true),
    CONFIG_AUDIO_BITRATE(R.string.issue_config_audio_bitrate, includeSettings = true),
    CONFIG_RESOLUTION(R.string.issue_config_resolution, includeSettings = true),
    CONFIG_STABILIZATION(R.string.issue_config_stabilization, includeSettings = true),
    CONFIG_FRAME_RATE(R.string.issue_config_frame_rate, includeSettings = true),
    CONFIG_COMBINATION(R.string.issue_config_combination, includeSettings = true),
    CAMERA_CONTROL(R.string.issue_camera_control, IssueSeverity.WARNING),
    PREVIEW_CONFIGURATION(R.string.issue_preview_configuration, IssueSeverity.WARNING),
    PREVIEW_RESTORED(R.string.issue_preview_restored, IssueSeverity.WARNING),
    RECORDING_START(R.string.issue_recording_start),
    RECORDING_BUSY(R.string.issue_recording_busy, IssueSeverity.INFO),
    RECORDING_FAILED(R.string.issue_recording_failed),
    RECORDING_CONTROL(R.string.issue_recording_control),
    RECORDING_METADATA(R.string.issue_recording_metadata, IssueSeverity.WARNING),
    STORAGE_ACCESS(R.string.issue_storage_access),
    STORAGE_FALLBACK(R.string.issue_storage_fallback, IssueSeverity.WARNING),
    STORAGE_CLEANUP(R.string.issue_storage_cleanup, IssueSeverity.WARNING),
    PRESET_OPERATION(R.string.issue_preset_operation),
    PRESET_UNREADABLE(R.string.issue_preset_unreadable, IssueSeverity.WARNING),
    PRESET_FOLDER(R.string.issue_preset_folder, IssueSeverity.WARNING),
    COMPATIBILITY_SCAN(R.string.issue_compatibility_scan),
    COMPATIBILITY_CACHE(R.string.issue_compatibility_cache, IssueSeverity.WARNING),
    CAPABILITY_QUERY(R.string.issue_capability_query, IssueSeverity.WARNING),
    TELEMETRY(R.string.issue_telemetry, IssueSeverity.WARNING),
    CRASH(R.string.issue_crash, IssueSeverity.INFO)
}

data class IssueRecord(
    val id: String,
    val code: IssueCode,
    val timestamp: Long,
    val operation: String,
    val details: String,
    val occurrences: Int = 1,
    val settings: Map<String, String> = emptyMap()
)

data class ErrorReportState(
    val history: List<IssueRecord> = emptyList(),
    val pending: List<String> = emptyList(),
    val persistenceAvailable: Boolean = true
)
