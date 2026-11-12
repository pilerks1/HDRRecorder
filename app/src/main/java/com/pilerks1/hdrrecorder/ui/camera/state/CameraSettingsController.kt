package com.pilerks1.hdrrecorder.ui.camera.state

import androidx.core.net.toUri
import com.pilerks1.hdrrecorder.camera.camera2.Camera2Control
import com.pilerks1.hdrrecorder.camera.camera2.Camera2Settings
import com.pilerks1.hdrrecorder.camera.model.Bitrate
import com.pilerks1.hdrrecorder.camera.model.VideoColorOption
import com.pilerks1.hdrrecorder.data.diagnostics.ErrorReporter
import com.pilerks1.hdrrecorder.data.diagnostics.IssueCode
import com.pilerks1.hdrrecorder.data.storage.AppPreferences
import com.pilerks1.hdrrecorder.data.storage.RecordingStorageManager
import com.pilerks1.hdrrecorder.data.telemetry.StatsManager
import com.pilerks1.hdrrecorder.ui.camera.CameraRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Applies settings edits using the existing recording and rebind gates. */
internal class CameraSettingsController(
    private val uiState: MutableStateFlow<CameraUiState>,
    private val preferencesManager: AppPreferences,
    private val recordingStorage: RecordingStorageManager,
    private val statsManager: StatsManager,
    private val cameraRuntime: CameraRuntime,
    private val reporter: ErrorReporter,
    private val binding: CameraBindingController,
    private val isRecordingBusy: () -> Boolean,
    private val applyCameraControls: () -> Unit
) {
    fun onEvent(event: CameraUiEvent) {
        when (event) {
            // Draft settings modifications (saved only by Add/Save)
            is CameraUiEvent.CycleFps -> updateSettings(requiresHardRebind = true) { state ->
                state.copy(selectedFps = when (state.selectedFps) { 24 -> 30; 30 -> 60; else -> 24 })
            }
            is CameraUiEvent.CycleResolution -> {
                updateSettings(requiresHardRebind = true) { state ->
                    state.copy(selectedResolution = state.selectedResolution.next())
                }
            }
            is CameraUiEvent.CycleAspectRatio -> {
                updateSettings(requiresHardRebind = true) { state ->
                    state.copy(selectedAspectRatio = state.selectedAspectRatio.next())
                }
            }

            // Encoding & Gamma
            is CameraUiEvent.SetVideoBitDepth -> selectVideoConfiguration(
                RecordingSetting.BIT_DEPTH, uiState.value.copy(videoBitDepth = event.bitDepth))
            is CameraUiEvent.SetEncoding -> selectVideoConfiguration(
                RecordingSetting.ENCODING, uiState.value.copy(encoding = event.encoding))
            is CameraUiEvent.SetVideoCodec -> selectVideoConfiguration(
                RecordingSetting.CODEC, uiState.value.copy(videoCodec = event.mimeType))
            is CameraUiEvent.SetAudioCodec -> updateSettings(requiresHardRebind = true) {
                it.copy(audioCodec = event.mimeType)
            }
            is CameraUiEvent.SetAudioBitrate -> if (Bitrate.isValidAudio(event.bitrate)) {
                updateSettings(requiresHardRebind = true) { it.copy(audioBitrate = event.bitrate) }
            }
            is CameraUiEvent.SetGammaSettings -> if (uiState.value.encoding.supportsGammaCurveSelection &&
                event.settings.mode in uiState.value.cameraCapabilities?.gammaModes.orEmpty()) {
                updateSettings(requiresHardRebind = false) { state ->
                    state.copy(gammaSettings = event.settings)
                }
            }

            is CameraUiEvent.SetBitrate -> if (Bitrate.isValid(event.bitrate)) {
                updateSettings(requiresHardRebind = true) { it.copy(bitrate = event.bitrate) }
            }
            is CameraUiEvent.SetStabilization -> updateSettings(requiresHardRebind = true) { it.copy(isStabilizationEnabled = event.enabled) }
            is CameraUiEvent.SetCamera2OverrideEnabled -> {
                if (event.control != Camera2Control.CONTROL_AF_MODE || !uiState.value.manualControlsState.isManualFocus) {
                    updateCamera2Settings { settings ->
                        settings.withOverride(event.control, settings[event.control].copy(enabled = event.enabled))
                    }
                }
            }
            is CameraUiEvent.SetCamera2Mode -> {
                val state = uiState.value
                val modes = state.cameraCapabilities?.camera2Modes?.modes
                    ?.get(event.control).orEmpty()
                if (state.camera2Settings[event.control].enabled && event.mode in modes &&
                    (event.control != Camera2Control.CONTROL_AF_MODE || !state.manualControlsState.isManualFocus)
                ) {
                    updateCamera2Settings { settings ->
                        settings.withOverride(event.control, settings[event.control].copy(mode = event.mode))
                    }
                }
            }

            is CameraUiEvent.SetExtendManualRanges -> updateSettings(requiresHardRebind = true) {
                it.copy(
                    isExtendManualRangesEnabled = event.enabled,
                    manualControlsState = it.manualControlsState.resetManualSlidersToAuto()
                )
            }

            is CameraUiEvent.SetWaitToLockAe -> updateSettings {
                it.copy(isWaitToLockAeEnabled = event.enabled)
            }

            is CameraUiEvent.SetPreviewFrameRate -> updateSettings(requiresHardRebind = true) {
                it.copy(previewSettings = it.previewSettings.copy(frameRate = event.range))
            }
            is CameraUiEvent.SetPreviewDynamicRange -> updateSettings(requiresHardRebind = true) {
                it.copy(previewSettings = it.previewSettings.copy(dynamicRange = event.dynamicRange))
            }

            is CameraUiEvent.SetRespectDisplayCutouts -> {
                if (!isRecordingBusy()) {
                    uiState.update { it.copy(isRespectDisplayCutoutsEnabled = event.enabled) }
                }
            }

            // Display headroom
            is CameraUiEvent.SetForceDisplaySdr -> updateSettings(requiresHardRebind = false) {
                it.copy(isForceDisplaySdrEnabled = event.enabled)
            }

            // Active recording destination remains global; presets may optionally restore it.
            is CameraUiEvent.SetStorageUri -> {
                try {
                    val uri = event.uri.toUri()
                    recordingStorage.takePersistablePermission(uri)
                } catch (e: Exception) {
                    reporter.report(IssueCode.STORAGE_ACCESS, "selectRecordingFolder", e)
                    return
                }

                preferencesManager.storageUri = event.uri
                statsManager.updateStorageUri(event.uri)
                uiState.update { it.copy(storageUri = event.uri) }
            }

            else -> Unit
        }
    }

    /**
     * Helper to automatically apply state whenever a setting changes.
     * Defer camera rebinds if the Settings UI is currently open.
     */
    private fun updateSettings(requiresHardRebind: Boolean = false, update: (CameraUiState) -> CameraUiState) {
        if (isRecordingBusy()) return
        val wasBoostSelected = uiState.value.manualControlsState.isLowLightBoostEnabled
        uiState.update { update(it).normalizeLowLightBoost() }
        if (!cameraRuntime.usesCameraHardware) return

        if (wasBoostSelected && !uiState.value.manualControlsState.isLowLightBoostEnabled) applyCameraControls()
        when {
            requiresHardRebind -> binding.requestRebind()
            // Defer changes while in settings UI
            uiState.value.isSettingsSheetVisible -> binding.deferSoftRebind()
            else -> applyCameraControls()
        }
    }

    private fun updateCamera2Settings(update: (Camera2Settings) -> Camera2Settings) {
        if (isRecordingBusy()) return
        val capabilities = uiState.value.cameraCapabilities?.camera2Modes ?: return
        uiState.update { state ->
            state.copy(camera2Settings = update(state.camera2Settings).reconcile(capabilities))
        }
        // Camera2 overrides apply immediately, including while the settings screen is open.
        applyCameraControls()
    }

    private enum class RecordingSetting { CODEC, BIT_DEPTH, ENCODING }

    private var recordingPriority = listOf(RecordingSetting.CODEC, RecordingSetting.BIT_DEPTH, RecordingSetting.ENCODING)

    private fun selectVideoConfiguration(touched: RecordingSetting, requested: CameraUiState) {
        val options = uiState.value.recordingOptions
        val combinations = options.colorsByVideoCodec.flatMap { (codec, colors) -> colors.map { codec to it } }
        fun matches(candidate: Pair<String?, VideoColorOption>, setting: RecordingSetting): Boolean = when (setting) {
            RecordingSetting.CODEC -> candidate.first == requested.videoCodec
            RecordingSetting.BIT_DEPTH -> candidate.second.bitDepth == requested.videoBitDepth
            RecordingSetting.ENCODING -> candidate.second.encoding == requested.encoding
        }
        // Keep the touched value, then preserve the other values in most-recently-touched order.
        val remaining = recordingPriority.filter { it != touched }
        val selected = combinations.filter { matches(it, touched) }.maxWithOrNull(
            compareBy<Pair<String?, VideoColorOption>> { matches(it, remaining[0]) }
                .thenBy { matches(it, remaining[1]) }
        ) ?: return
        recordingPriority = listOf(touched) + remaining
        updateSettings(requiresHardRebind = true) {
            it.copy(videoCodec = selected.first, videoBitDepth = selected.second.bitDepth, encoding = selected.second.encoding)
        }
    }
}
