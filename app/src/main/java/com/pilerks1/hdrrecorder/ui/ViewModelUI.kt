package com.pilerks1.hdrrecorder.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
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

@ExperimentalCamera2Interop
class CameraViewModel(application: Application) : AndroidViewModel(application) {

    // --- Managers ---
    private val statsManager = StatsManager()
    private val settingsManager = SettingsManager()
    private val preferencesManager = PreferencesManager(application)
    private val cameraManager = CameraManager(application, statsManager)
    private val recordingManager = RecordingManager(application)

    // --- UI State ---
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    init {
        // Load persisted preferences
        _uiState.update { it.copy(storageUri = preferencesManager.storageUri) }

        collectStats()
        collectRecordingState()
    }

    fun onEvent(event: CameraUiEvent) {
        when (event) {
            is CameraUiEvent.ToggleRecording -> toggleRecording()
            is CameraUiEvent.TogglePause -> togglePause()
            is CameraUiEvent.CycleFps -> cycleFps()
            is CameraUiEvent.CycleResolution -> cycleResolution()
            is CameraUiEvent.CycleFocusMode -> cycleFocusMode()

            // Format & Gamma
            is CameraUiEvent.CycleColorFormat -> cycleColorFormat()
            is CameraUiEvent.CycleGammaCurve -> cycleGammaCurve()

            is CameraUiEvent.SetNoiseReduction -> setNoiseReduction(event.enabled)
            is CameraUiEvent.SetBitrate -> _uiState.update { it.copy(bitrate = event.bitrate) }

            is CameraUiEvent.SetStabilization -> {
                _uiState.update { it.copy(isStabilizationEnabled = event.enabled) }
                rebindCameraAndApplySettings() // Requires Use Case Rebind
            }

            // SDR Hacks
            is CameraUiEvent.SetSdrToneMap -> {
                _uiState.update {
                    it.copy(
                        isSdrToneMapEnabled = event.enabled,
                        isForceDisplaySdrEnabled = if (event.enabled) false else it.isForceDisplaySdrEnabled
                    )
                }
                rebindCameraAndApplySettings() // Requires Use Case Rebind
            }
            is CameraUiEvent.SetForceDisplaySdr -> {
                _uiState.update {
                    it.copy(
                        isForceDisplaySdrEnabled = event.enabled,
                        isSdrToneMapEnabled = if (event.enabled) false else it.isSdrToneMapEnabled
                    )
                }
                // No rebind needed for window attributes
            }

            // Storage
            is CameraUiEvent.SetStorageUri -> {
                try {
                    val uri = Uri.parse(event.uri)
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    // Tell Android we want to remember access to this folder permanently
                    getApplication<Application>().contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: Exception) {
                    Log.e("CameraViewModel", "Failed to take persistable URI permission", e)
                }

                preferencesManager.storageUri = event.uri
                _uiState.update { it.copy(storageUri = event.uri) }
            }

            is CameraUiEvent.TapToMeter -> cameraManager.tapToMeter(event.meteringPoint)
            is CameraUiEvent.OpenSettings -> _uiState.update { it.copy(isSettingsSheetVisible = true) }
            is CameraUiEvent.CloseSettings -> _uiState.update { it.copy(isSettingsSheetVisible = false) }
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

    private fun cycleFps() {
        val newFps = when (_uiState.value.selectedFps) {
            24 -> 30
            30 -> 60
            else -> 24
        }
        _uiState.update { it.copy(selectedFps = newFps) }
        rebindCameraAndApplySettings()
    }

    private fun cycleResolution() {
        val newResolution = when (_uiState.value.selectedResolution) {
            is Resolution.FHD -> Resolution.UHD
            is Resolution.UHD -> Resolution.HIGHEST
            is Resolution.HIGHEST -> Resolution.FHD
        }
        _uiState.update { it.copy(selectedResolution = newResolution) }
        rebindCameraAndApplySettings()
    }

    private fun cycleFocusMode() {
        val newFocusMode = if (_uiState.value.focusMode == "Auto") "Manual" else "Auto"
        _uiState.update { it.copy(focusMode = newFocusMode) }
        applySettingsOnly()
    }

    private fun cycleColorFormat() {
        val newFormat = when (_uiState.value.colorFormat) {
            "HLG" -> "HDR10"
            "HDR10" -> "HDR10+"
            "HDR10+" -> "DB 8.4"
            "DB 8.4" -> "Unspec"
            else -> "HLG"
        }
        _uiState.update { state ->
            // Force Gamma to Auto if Format is anything other than Unspec
            val newGamma = if (newFormat != "Unspec") "Auto" else state.gammaCurve
            state.copy(colorFormat = newFormat, gammaCurve = newGamma)
        }
        rebindCameraAndApplySettings() // Changing dynamic range format requires full camera rebind
    }

    private fun cycleGammaCurve() {
        // Only allow changing gamma if format is Unspec
        if (_uiState.value.colorFormat != "Unspec") return

        val newGamma = when (_uiState.value.gammaCurve) {
            "Auto" -> "HLG"
            "HLG" -> "PQ"
            "PQ" -> "Custom"
            else -> "Auto"
        }
        _uiState.update { it.copy(gammaCurve = newGamma) }
        applySettingsOnly() // Gamma curve can be injected without rebuilding Use Cases
    }

    private fun setNoiseReduction(enabled: Boolean) {
        _uiState.update { it.copy(isNoiseReductionEnabled = enabled) }
        applySettingsOnly()
    }

    fun startCamera(lifecycleOwner: androidx.lifecycle.LifecycleOwner) {
        cameraManager.startCamera(
            lifecycleOwner = lifecycleOwner,
            onSurfaceRequest = { request -> _surfaceRequest.value = request },
            uiState = _uiState.value
        )

        viewModelScope.launch {
            delay(1500)
            _uiState.update { it.copy(isCameraReady = true) }
            applySettingsOnly()
        }
    }

    private fun applySettingsOnly() {
        val cameraControl = cameraManager.getCameraControl() ?: return
        updateSettingsManager()
        settingsManager.applyAllSettings(cameraControl)
    }

    private fun rebindCameraAndApplySettings() {
        viewModelScope.launch {
            delay(500)
            applySettingsOnly()
        }
    }

    private fun updateSettingsManager() {
        val currentState = _uiState.value
        settingsManager.setFrameRate(currentState.selectedFps)
        settingsManager.setFocusMode(currentState.focusMode)
        settingsManager.setNoiseReduction(currentState.isNoiseReductionEnabled)
        settingsManager.setGammaCurve(currentState.gammaCurve) // Pass the new curve state to your settings manager
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