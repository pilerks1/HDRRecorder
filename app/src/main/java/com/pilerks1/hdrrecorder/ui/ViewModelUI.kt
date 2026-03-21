package com.pilerks1.hdrrecorder.ui

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pilerks1.hdrrecorder.data.CameraManager
import com.pilerks1.hdrrecorder.data.PreferencesManager
import com.pilerks1.hdrrecorder.data.RecordingManager
import com.pilerks1.hdrrecorder.data.SettingsManager
import com.pilerks1.hdrrecorder.data.StatsManager
import com.pilerks1.hdrrecorder.model.Resolution
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.core.net.toUri

@ExperimentalCamera2Interop
class CameraViewModel(application: Application) : AndroidViewModel(application) {

    // --- Managers ---
    private val statsManager = StatsManager()
    private val settingsManager = SettingsManager()
    private val preferencesManager = PreferencesManager(application)
    private val cameraManager = CameraManager(application, statsManager)
    private val recordingManager = RecordingManager(application)

    // --- Dirty Flags for Settings UI ---
    private var pendingHardRebind = false
    private var pendingSoftRebind = false

    // --- UI State ---
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    init {
        // Load persisted global storage preference
        val globalStorageUri = preferencesManager.storageUri

        // If no "Default" preset exists, capture initial state and save it
        if (!preferencesManager.hasPreset("Default")) {
            preferencesManager.savePreset("Default", _uiState.value)
        }

        // Determine current preset name
        val names = preferencesManager.getPresetNames().toList()
        var currentPreset = preferencesManager.currentPresetName
        if (!names.contains(currentPreset)) currentPreset = "Default"

        // Load the stored preset state
        val loadedState = preferencesManager.loadPreset(currentPreset, _uiState.value)
        _uiState.update {
            loadedState.copy(
                presetsList = names,
                storageUri = globalStorageUri
            )
        }

        collectStats()
        collectRecordingState()
    }

    /**
     * Helper to automatically apply state whenever a setting changes.
     * Defer camera rebinds if the Settings UI is currently open.
     */
    private fun updateSettingsAndSave(requiresHardRebind: Boolean = false, update: (CameraUiState) -> CameraUiState) {
        _uiState.update { oldState -> update(oldState) }

        if (_uiState.value.isSettingsSheetVisible) {
            // Defer changes while in settings UI
            if (requiresHardRebind) pendingHardRebind = true else pendingSoftRebind = true
        } else {
            // Apply immediately if not in settings UI
            if (requiresHardRebind) {
                _uiState.update { it.copy(cameraRebindTrigger = it.cameraRebindTrigger + 1) }
            } else {
                applySettingsOnly()
            }
        }
    }

    fun onEvent(event: CameraUiEvent) {
        when (event) {
            // Preset Management
            is CameraUiEvent.SavePreset -> {
                val newState = _uiState.value.copy(currentPresetName = event.name)
                preferencesManager.savePreset(event.name, newState)
                preferencesManager.currentPresetName = event.name
                _uiState.update { it.copy(
                    presetsList = preferencesManager.getPresetNames().toList(),
                    currentPresetName = event.name
                ) }
            }
            is CameraUiEvent.LoadPreset -> {
                val loadedState = preferencesManager.loadPreset(event.name, _uiState.value)
                preferencesManager.currentPresetName = event.name
                _uiState.update { loadedState.copy(presetsList = preferencesManager.getPresetNames().toList()) }

                if (_uiState.value.isSettingsSheetVisible) {
                    pendingHardRebind = true
                } else {
                    _uiState.update { it.copy(cameraRebindTrigger = it.cameraRebindTrigger + 1) }
                }
            }
            is CameraUiEvent.DeletePreset -> {
                preferencesManager.deletePreset(event.name)
                val names = preferencesManager.getPresetNames().toList()
                val nextPreset = if (names.contains("Default")) "Default" else names.firstOrNull() ?: "Default"

                if (_uiState.value.currentPresetName == event.name) {
                    val loadedState = preferencesManager.loadPreset(nextPreset, _uiState.value)
                    preferencesManager.currentPresetName = nextPreset
                    _uiState.update { loadedState.copy(presetsList = names) }

                    if (_uiState.value.isSettingsSheetVisible) {
                        pendingHardRebind = true
                    } else {
                        _uiState.update { it.copy(cameraRebindTrigger = it.cameraRebindTrigger + 1) }
                    }
                } else {
                    _uiState.update { it.copy(presetsList = names) }
                }
            }
            is CameraUiEvent.DeleteAllPresets -> {
                preferencesManager.deleteAllPresets()
                val nextPreset = "Default"
                // Seed a fresh default config
                val loadedState = preferencesManager.loadPreset(nextPreset, CameraUiState())
                preferencesManager.savePreset(nextPreset, loadedState)
                preferencesManager.currentPresetName = nextPreset
                _uiState.update { loadedState.copy(presetsList = listOf("Default")) }

                if (_uiState.value.isSettingsSheetVisible) {
                    pendingHardRebind = true
                } else {
                    _uiState.update { it.copy(cameraRebindTrigger = it.cameraRebindTrigger + 1) }
                }
            }

            // Camera Toggles
            is CameraUiEvent.ToggleRecording -> toggleRecording()
            is CameraUiEvent.TogglePause -> togglePause()

            // Settings modifications (auto-saved)
            is CameraUiEvent.CycleFps -> {
                updateSettingsAndSave(requiresHardRebind = true) { state ->
                    val newFps = when (state.selectedFps) { 24 -> 30; 30 -> 60; else -> 24 }
                    state.copy(selectedFps = newFps)
                }
            }
            is CameraUiEvent.CycleResolution -> {
                updateSettingsAndSave(requiresHardRebind = true) { state ->
                    val newRes = when (state.selectedResolution) {
                        is Resolution.FHD -> Resolution.UHD
                        is Resolution.UHD -> Resolution.HIGHEST
                        is Resolution.HIGHEST -> Resolution.FHD
                    }
                    state.copy(selectedResolution = newRes)
                }
            }
            is CameraUiEvent.CycleFocusMode -> {
                updateSettingsAndSave(requiresHardRebind = false) { state ->
                    val newFocusMode = if (state.focusMode == "Auto") "Manual" else "Auto"
                    state.copy(focusMode = newFocusMode)
                }
            }

            // Format & Gamma (auto-saved)
            is CameraUiEvent.CycleColorFormat -> {
                updateSettingsAndSave(requiresHardRebind = true) { state ->
                    val newFormat = when (state.colorFormat) {
                        "HLG" -> "HDR10"
                        "HDR10" -> "HDR10+"
                        "HDR10+" -> "DB 8.4"
                        "DB 8.4" -> "Unspec"
                        else -> "HLG"
                    }
                    val newGamma = if (newFormat != "Unspec") "Auto" else state.gammaCurve
                    state.copy(colorFormat = newFormat, gammaCurve = newGamma)
                }
            }
            is CameraUiEvent.CycleGammaCurve -> {
                if (_uiState.value.colorFormat == "Unspec") {
                    updateSettingsAndSave(requiresHardRebind = false) { state ->
                        val newGamma = when (state.gammaCurve) {
                            "Auto" -> "HLG"
                            "HLG" -> "PQ"
                            "PQ" -> "Custom"
                            else -> "Auto"
                        }
                        state.copy(gammaCurve = newGamma)
                    }
                }
            }

            is CameraUiEvent.SetNoiseReduction -> {
                updateSettingsAndSave(requiresHardRebind = false) { it.copy(isNoiseReductionEnabled = event.enabled) }
            }
            is CameraUiEvent.SetBitrate -> {
                updateSettingsAndSave(requiresHardRebind = true) { it.copy(bitrate = event.bitrate) }
            }
            is CameraUiEvent.SetStabilization -> {
                updateSettingsAndSave(requiresHardRebind = true) { it.copy(isStabilizationEnabled = event.enabled) }
            }

            // SDR Hacks (auto-saved)
            is CameraUiEvent.SetSdrToneMap -> {
                updateSettingsAndSave(requiresHardRebind = true) {
                    it.copy(
                        isSdrToneMapEnabled = event.enabled,
                        isForceDisplaySdrEnabled = if (event.enabled) false else it.isForceDisplaySdrEnabled
                    )
                }
            }
            is CameraUiEvent.SetForceDisplaySdr -> {
                updateSettingsAndSave(requiresHardRebind = false) {
                    it.copy(
                        isForceDisplaySdrEnabled = event.enabled,
                        isSdrToneMapEnabled = if (event.enabled) false else it.isSdrToneMapEnabled
                    )
                }
            }

            // Storage (Maintained globally out of the presets)
            is CameraUiEvent.SetStorageUri -> {
                try {
                    val uri = event.uri.toUri()
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    getApplication<Application>().contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: Exception) {
                    Log.e("CameraViewModel", "Failed to take persistable URI permission", e)
                }

                preferencesManager.storageUri = event.uri
                _uiState.update { it.copy(storageUri = event.uri) }
            }

            is CameraUiEvent.TapToMeter -> cameraManager.tapToMeter(event.meteringPoint)
            is CameraUiEvent.OpenSettings -> _uiState.update { it.copy(isSettingsSheetVisible = true) }
            is CameraUiEvent.CloseSettings -> {
                _uiState.update { it.copy(isSettingsSheetVisible = false) }

                if (pendingHardRebind) {
                    _uiState.update { it.copy(cameraRebindTrigger = it.cameraRebindTrigger + 1) }
                } else if (pendingSoftRebind) {
                    applySettingsOnly()
                }

                pendingHardRebind = false
                pendingSoftRebind = false
            }
        }
    }

    fun onOrientationChanged(rotation: Int) {
        cameraManager.updateRotation(rotation)
    }

    private fun toggleRecording() {
        if (_uiState.value.isRecording) {
            recordingManager.stopRecording()
            statsManager.stopFpsCalculation()
        } else {
            val videoCapture = cameraManager.videoCapture ?: return
            recordingManager.startRecording(videoCapture, _uiState.value.storageUri)
            statsManager.startFpsCalculation(_uiState.value.selectedFps)
        }
    }

    private fun togglePause() {
        if (_uiState.value.isPaused) {
            recordingManager.resumeRecording()
        } else {
            recordingManager.pauseRecording()
        }
        _uiState.update { it.copy(isPaused = !it.isPaused) }
    }

    fun startCamera(lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        _uiState.update { it.copy(isCameraReady = false) }

        cameraManager.startCamera(
            lifecycleOwner = lifecycleOwner,
            onSurfaceRequest = { request -> _surfaceRequest.value = request },
            uiState = _uiState.value
        )

        viewModelScope.launch {
            // Fast poll to wait for the camera hardware to actually bind
            // instead of using a rigid 1500ms delay.
            var attempts = 0
            while (cameraManager.getCameraControl() == null && attempts < 50) {
                delay(50) // check every 50ms (max 2.5 seconds timeout)
                attempts++
            }

            // Give the pipeline a tiny fraction to stabilize surface formats
            delay(100)

            _uiState.update { it.copy(isCameraReady = true) }
            applySettingsOnly()
        }
    }

    private fun applySettingsOnly() {
        val cameraControl = cameraManager.getCameraControl() ?: return
        updateSettingsManager()
        settingsManager.applyAllSettings(cameraControl)
    }

    private fun updateSettingsManager() {
        val currentState = _uiState.value
        settingsManager.setFrameRate(currentState.selectedFps)
        settingsManager.setFocusMode(currentState.focusMode)
        settingsManager.setNoiseReduction(currentState.isNoiseReductionEnabled)
        settingsManager.setGammaCurve(currentState.gammaCurve)
    }

    // --- State Collection ---
    private fun collectStats() {
        viewModelScope.launch {
            statsManager.statsState.collect { snapshot ->
                _uiState.update { it.copy(stats = snapshot) }
            }
        }
    }

    private fun collectRecordingState() {
        viewModelScope.launch {
            recordingManager.isRecording.collect { isRecording ->
                _uiState.update { it.copy(isRecording = isRecording, isPaused = if (!isRecording) false else it.isPaused) }
            }
        }
        viewModelScope.launch {
            recordingManager.recordingTimeSeconds.collect { time ->
                _uiState.update { it.copy(recordingTime = time) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cameraManager.release()
        statsManager.cleanup()
    }
}