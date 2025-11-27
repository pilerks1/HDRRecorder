package com.pilerks1.hdrrecorder.ui

import android.app.Application
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pilerks1.hdrrecorder.data.CameraManager
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
    private val cameraManager = CameraManager(application, statsManager)
    private val recordingManager = RecordingManager(application)

    // --- UI State ---
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    init {
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
            is CameraUiEvent.CycleGammaMode -> cycleGammaMode()
            is CameraUiEvent.SetNoiseReduction -> setNoiseReduction(event.enabled)

            // SDR Hacks
            is CameraUiEvent.SetSdrToneMap -> {
                _uiState.update {
                    it.copy(
                        isSdrToneMapEnabled = event.enabled,
                        // Mutual exclusion logic
                        isForceDisplaySdrEnabled = if (event.enabled) false else it.isForceDisplaySdrEnabled
                    )
                }
                rebindCameraAndApplySettings()
            }
            is CameraUiEvent.SetForceDisplaySdr -> {
                _uiState.update {
                    it.copy(
                        isForceDisplaySdrEnabled = event.enabled,
                        // Mutual exclusion logic
                        isSdrToneMapEnabled = if (event.enabled) false else it.isSdrToneMapEnabled
                    )
                }
                // No rebind needed for window attributes
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
            recordingManager.startRecording(videoCapture)
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

    private fun cycleGammaMode() {
        val newGammaMode = when (_uiState.value.gammaMode) {
            "Device" -> "HLG"
            "HLG" -> "Custom"
            else -> "Device"
        }
        _uiState.update { it.copy(gammaMode = newGammaMode) }
        rebindCameraAndApplySettings()
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
        settingsManager.setTonemapMode(currentState.gammaMode)
        settingsManager.setNoiseReduction(currentState.isNoiseReductionEnabled)
    }

    // --- State Collection (Optimized) ---
    private fun collectStats() {
        viewModelScope.launch {
            // Single collection point for ALL stats.
            // Updates strictly at the interval defined in StatsManager (e.g., 500ms)
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
        statsManager.cleanup() // Stop the polling job
    }
}

