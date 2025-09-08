package com.pilerks1.hdrrecorder.ui

import android.app.Application
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
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

/**
 * The ViewModel for the CameraScreen.
 * This class is the central hub of the application's logic. It owns the UI state,
 * responds to user events, and coordinates the data managers (CameraManager,
 * SettingsManager, etc.) to perform the necessary actions.
 */
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

    init {
        // Collect flows from managers to keep the UI state updated
        collectStats()
        collectRecordingState()
    }

    /**
     * The main entry point for all user interactions from the UI.
     * It uses a 'when' statement to delegate actions based on the event type.
     */
    fun onEvent(event: CameraUiEvent) {
        when (event) {
            is CameraUiEvent.ToggleRecording -> toggleRecording()
            is CameraUiEvent.TogglePause -> togglePause()
            is CameraUiEvent.CycleFps -> cycleFps()
            is CameraUiEvent.CycleResolution -> cycleResolution()
            is CameraUiEvent.CycleFocusMode -> cycleFocusMode()
            is CameraUiEvent.CycleGammaMode -> cycleGammaMode()
            is CameraUiEvent.SetNoiseReduction -> setNoiseReduction(event.enabled)
            is CameraUiEvent.TapToMeter -> cameraManager.tapToMeter(event.meteringPoint)
            is CameraUiEvent.OpenSettings -> _uiState.update { it.copy(isSettingsSheetVisible = true) }
            is CameraUiEvent.CloseSettings -> _uiState.update { it.copy(isSettingsSheetVisible = false) }
        }
    }

    // --- Event Handlers ---

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

    // --- Camera Lifecycle and Settings Application ---

    fun startCamera(lifecycleOwner: androidx.lifecycle.LifecycleOwner, surfaceProvider: androidx.camera.core.Preview.SurfaceProvider) {
        cameraManager.startCamera(lifecycleOwner, surfaceProvider, _uiState.value)
        viewModelScope.launch {
            delay(1500) // Allow time for camera to initialize
            _uiState.update { it.copy(isCameraReady = true) }
            applySettingsOnly() // Apply initial settings
        }
    }

    /**
     * Applies camera2 settings that don't require re-binding camera use cases.
     */
    private fun applySettingsOnly() {
        val cameraControl = cameraManager.getCameraControl() ?: return
        updateSettingsManager()
        settingsManager.applyAllSettings(cameraControl)
    }

    /**
     * Re-binds camera use cases, which is necessary for settings like resolution or FPS.
     * This is expensive, slow, and can not be done while a recording is ongoing
     */
    private fun rebindCameraAndApplySettings() {
        // This function is implicitly handled by the UI's LaunchedEffect,
        // which will call startCamera again when a key state changes.
        // For a more advanced implementation, this could be handled here directly.
        // But for now, we rely on the UI to trigger the rebind.
        // After rebind, we must apply settings.
        viewModelScope.launch {
            delay(500) // Give a moment for rebind to complete
            applySettingsOnly()
        }
    }

    /**
     * Updates the SettingsManager with the latest values from the UI state
     * before applying them to the camera.
     */
    private fun updateSettingsManager() {
        val currentState = _uiState.value
        settingsManager.setFrameRate(currentState.selectedFps)
        settingsManager.setFocusMode(currentState.focusMode)
        settingsManager.setTonemapMode(currentState.gammaMode)
        settingsManager.setNoiseReduction(currentState.isNoiseReductionEnabled)
    }

    // --- State Collection ---

    private fun collectStats() {
        viewModelScope.launch {
            statsManager.iso.collect { iso -> _uiState.update { it.copy(iso = iso) } }
        }
        viewModelScope.launch {
            statsManager.shutterSpeed.collect { shutter -> _uiState.update { it.copy(shutterSpeed = shutter) } }
        }
        viewModelScope.launch {
            statsManager.effectiveFps.collect { fps -> _uiState.update { it.copy(effectiveFps = fps) } }
        }
        viewModelScope.launch {
            statsManager.droppedFrames.collect { frames -> _uiState.update { it.copy(droppedFrames = frames) } }
        }
        viewModelScope.launch {
            statsManager.addedFrames.collect { frames -> _uiState.update { it.copy(addedFrames = frames) } }
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
    }
}
