package com.pilerks1.hdrrecorder.ui.viewmodels

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.core.net.toUri
import com.pilerks1.hdrrecorder.data.CameraManager
import com.pilerks1.hdrrecorder.data.Bitrate
import com.pilerks1.hdrrecorder.data.PreferencesManager
import com.pilerks1.hdrrecorder.data.RecordingManager
import com.pilerks1.hdrrecorder.data.StatsManager
import com.pilerks1.hdrrecorder.data.camera.CameraInteropApplier
import com.pilerks1.hdrrecorder.data.camera.InteropSettings
import com.pilerks1.hdrrecorder.model.GammaCurve
import com.pilerks1.hdrrecorder.ui.CameraUiEvent
import com.pilerks1.hdrrecorder.ui.CameraUiState
import com.pilerks1.hdrrecorder.ui.ManualControlsState
import com.pilerks1.hdrrecorder.ui.resetForTapToMeter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Managers are constructor-injected (via CameraViewModelFactory in MainActivity) so the
 * ViewModel no longer news up its own Android dependencies. Application is retained only
 * for the content resolver used when persisting a storage URI permission.
 */
@ExperimentalCamera2Interop
class CameraViewModel(
    application: Application,
    private val statsManager: StatsManager,
    private val preferencesManager: PreferencesManager,
    private val cameraManager: CameraManager,
    private val recordingManager: RecordingManager
) : AndroidViewModel(application) {

    // --- Deferred settings and camera binding state ---
    private var pendingHardRebind = false
    private var pendingSoftRebind = false
    private var cameraLifecycleOwner: LifecycleOwner? = null
    private var lastDisplayRotation = 0
    private var cameraRebindJob: kotlinx.coroutines.Job? = null

    // --- UI State ---
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    init {
        val globalStorageUri = preferencesManager.storageUri
        if (!preferencesManager.hasPreset("Default")) preferencesManager.savePreset("Default", _uiState.value)

        val names = preferencesManager.getPresetNames().toList()
        val currentPreset = if (names.contains(preferencesManager.currentPresetName)) preferencesManager.currentPresetName else "Default"
        val loadedState = preferencesManager.loadPreset(currentPreset, _uiState.value)

        _uiState.update { loadedState.copy(presetsList = names, storageUri = globalStorageUri) }

        collectStats()
        collectRecordingState()
        statsManager.setRecordingState(false, requestedFpsForStats(), Bitrate.parseOrDefault(_uiState.value.bitrate), _uiState.value.storageUri)
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
            if (requiresHardRebind) {
                scheduleCameraRebind(debounce = true)
            } else {
                applyInterop()
            }
        }
    }

    fun onEvent(event: CameraUiEvent) {
        when (event) {
            // Preset Management
            is CameraUiEvent.SavePreset -> {
                preferencesManager.savePreset(event.name, _uiState.value.copy(currentPresetName = event.name))
                preferencesManager.currentPresetName = event.name
                _uiState.update { it.copy(presetsList = preferencesManager.getPresetNames().toList(), currentPresetName = event.name) }
            }
            is CameraUiEvent.LoadPreset -> {
                val loadedState = preferencesManager.loadPreset(event.name, _uiState.value)
                preferencesManager.currentPresetName = event.name
                _uiState.update { loadedState.copy(presetsList = preferencesManager.getPresetNames().toList()) }
                triggerRebindForPresets()
            }
            is CameraUiEvent.DeletePreset -> {
                preferencesManager.deletePreset(event.name)
                val names = preferencesManager.getPresetNames().toList()
                val nextPreset = if (names.contains("Default")) "Default" else names.firstOrNull() ?: "Default"

                if (_uiState.value.currentPresetName == event.name) {
                    val loadedState = preferencesManager.loadPreset(nextPreset, _uiState.value)
                    preferencesManager.currentPresetName = nextPreset
                    _uiState.update { loadedState.copy(presetsList = names) }
                    triggerRebindForPresets()
                } else _uiState.update { it.copy(presetsList = names) }
            }
            is CameraUiEvent.DeleteAllPresets -> {
                preferencesManager.deleteAllPresets()
                val loadedState = preferencesManager.loadPreset("Default", CameraUiState())
                preferencesManager.savePreset("Default", loadedState)
                preferencesManager.currentPresetName = "Default"
                _uiState.update { loadedState.copy(presetsList = listOf("Default")) }
                triggerRebindForPresets()
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
                    state.copy(selectedResolution = state.selectedResolution.next())
                }
            }

            // Format & Gamma (auto-saved)
            is CameraUiEvent.CycleColorFormat -> updateSettingsAndSave(requiresHardRebind = true) { state ->
                val newFormat = state.colorFormat.next()
                state.copy(
                    colorFormat = newFormat,
                    gammaCurve = if (newFormat.supportsGammaCurveSelection) state.gammaCurve else GammaCurve.AUTO
                )
            }
            is CameraUiEvent.CycleGammaCurve -> if (_uiState.value.colorFormat.supportsGammaCurveSelection) {
                updateSettingsAndSave(requiresHardRebind = false) { state ->
                    state.copy(gammaCurve = state.gammaCurve.next())
                }
            }

            is CameraUiEvent.SetNoiseReduction -> updateSettingsAndSave(requiresHardRebind = false) { it.copy(isNoiseReductionEnabled = event.enabled) }
            is CameraUiEvent.SetBitrate -> {
                if (event.bitrate.isEmpty() || Bitrate.parse(event.bitrate) != null) {
                    updateSettingsAndSave(requiresHardRebind = Bitrate.parse(event.bitrate) != null) {
                        it.copy(bitrate = event.bitrate)
                    }
                }
            }
            is CameraUiEvent.SetStabilization -> updateSettingsAndSave(requiresHardRebind = true) { it.copy(isStabilizationEnabled = event.enabled) }

            // SDR Hacks (auto-saved)
            is CameraUiEvent.SetSdrToneMap -> updateSettingsAndSave(requiresHardRebind = true) {
                it.copy(isSdrToneMapEnabled = event.enabled, isForceDisplaySdrEnabled = if (event.enabled) false else it.isForceDisplaySdrEnabled)
            }
            is CameraUiEvent.SetForceDisplaySdr -> updateSettingsAndSave(requiresHardRebind = false) {
                it.copy(isForceDisplaySdrEnabled = event.enabled, isSdrToneMapEnabled = if (event.enabled) false else it.isSdrToneMapEnabled)
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
                statsManager.updateStorageUri(event.uri)
                _uiState.update { it.copy(storageUri = event.uri) }
            }

            is CameraUiEvent.TapToMeter -> {
                val manualControls = _uiState.value.manualControlsState
                val automaticControls = manualControls.resetForTapToMeter()
                if (automaticControls != manualControls) {
                    _uiState.update { it.copy(manualControlsState = automaticControls) }
                    applyInterop()
                }
                cameraManager.tapToMeter(event.meteringPoint)
            }
            is CameraUiEvent.OpenSettings -> _uiState.update { it.copy(isSettingsSheetVisible = true) }
            is CameraUiEvent.CloseSettings -> {
                _uiState.update { it.copy(isSettingsSheetVisible = false) }

                if (pendingHardRebind) {
                    scheduleCameraRebind(debounce = false)
                } else if (pendingSoftRebind) {
                    applyInterop()
                }

                pendingHardRebind = false
                pendingSoftRebind = false
            }
        }
    }

    private fun triggerRebindForPresets() {
        if (_uiState.value.isSettingsSheetVisible) pendingHardRebind = true
        else scheduleCameraRebind(debounce = true)
    }

    /**
     * Keeps CameraX aligned with the rotation of the Activity's actual display.
     * The Activity itself is sensor-driven by Android (fullSensor), so this callback
     * never decides or requests a screen orientation and never rebinds the camera.
     */
    fun onDisplayRotationChanged(rotation: Int) {
        lastDisplayRotation = rotation
        if (_uiState.value.isRecording) return
        cameraManager.updateRotation(rotation)
    }

    private fun toggleRecording() {
        if (_uiState.value.isRecording) {
            recordingManager.stopRecording()
        } else {
            val videoCapture = cameraManager.videoCapture ?: return
            val targetBitrate = Bitrate.parseOrDefault(_uiState.value.bitrate)
            if (!recordingManager.startRecording(videoCapture, _uiState.value.storageUri)) return
            val requestedFps = requestedFpsForStats()
            statsManager.startFpsCalculation(requestedFps)
            statsManager.setRecordingState(true, requestedFps, targetBitrate, _uiState.value.storageUri)
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

    fun updateManualControls(updater: (ManualControlsState) -> ManualControlsState) {
        val caps = _uiState.value.cameraCapabilities
        var triggersRebind = false
        _uiState.update {
            val exposureDefaults = ManualControlStateUpdater.ExposureDefaults(
                isoValue = it.stats.iso.takeIf { iso -> iso > 0 },
                shutterNanos = it.stats.shutterSpeed
                    .takeIf { shutterSpeed -> shutterSpeed > 0.0 }
                    ?.let { shutterSpeed -> (1_000_000_000.0 / shutterSpeed).toLong() }
            )
            val (newState, rebind) = ManualControlStateUpdater.calculateNextState(
                it.manualControlsState,
                caps,
                exposureDefaults,
                updater
            )
            triggersRebind = rebind

            it.copy(
                manualControlsState = newState
            )
        }

        if (triggersRebind && _uiState.value.isSettingsSheetVisible) pendingHardRebind = true
        else if (triggersRebind) scheduleCameraRebind(debounce = true)
        applyInterop()
    }

    /** Attaches the current lifecycle and starts the initial bind without an arbitrary delay. */
    fun attachCamera(lifecycleOwner: LifecycleOwner, displayRotation: Int) {
        val lifecycleChanged = cameraLifecycleOwner !== lifecycleOwner
        cameraLifecycleOwner = lifecycleOwner
        lastDisplayRotation = displayRotation
        scheduleCameraRebind(debounce = !lifecycleChanged)
    }

    /**
     * Coalesces quick changes (such as 24 -> 30 -> 60 FPS) but never waits for a guessed camera
     * startup time. CameraManager binds only after the ProcessCameraProvider is actually ready.
     */
    private fun scheduleCameraRebind(debounce: Boolean) {
        val lifecycleOwner = cameraLifecycleOwner ?: return
        cameraRebindJob?.cancel()

        val bind = {
            _uiState.update { it.copy(isCameraReady = false) }
            cameraManager.requestBindWhenReady(
                lifecycleOwner = lifecycleOwner,
                onSurfaceRequest = { request -> _surfaceRequest.value = request },
                uiState = _uiState.value,
                displayRotation = lastDisplayRotation,
                onCameraBound = { control, caps ->
                    _uiState.update { it.copy(cameraCapabilities = caps, isCameraReady = true) }
                    CameraInteropApplier.apply(control, _uiState.value.manualControlsState, currentInteropSettings(), caps)
                }
            )
        }

        if (debounce && _uiState.value.isCameraReady) {
            cameraRebindJob = viewModelScope.launch {
                delay(CAMERA_REBIND_DEBOUNCE_MS)
                bind()
            }
        } else {
            bind()
        }
    }

    private fun requestedFpsForStats(): Int? = _uiState.value.selectedFps
        .takeUnless { _uiState.value.manualControlsState.isManualFps }

    /**
     * Single entry point for all Camera2 interop writes. Builds one request from the current
     * manual control state + non-manual interop settings via the unified applier.
     */
    private fun applyInterop() {
        val cameraControl = cameraManager.getCameraControl() ?: return
        val state = _uiState.value
        CameraInteropApplier.apply(cameraControl, state.manualControlsState, currentInteropSettings(), state.cameraCapabilities)
    }

    private fun currentInteropSettings(): InteropSettings {
        val state = _uiState.value
        return InteropSettings(
            gammaCurve = state.gammaCurve,
            isNoiseReductionEnabled = state.isNoiseReductionEnabled
        )
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
                if (!isRecording) {
                    statsManager.stopFpsCalculation()
                    val state = _uiState.value
                    statsManager.setRecordingState(
                        recording = false,
                        targetFps = requestedFpsForStats(),
                        targetBitrate = Bitrate.parseOrDefault(state.bitrate),
                        storageUri = state.storageUri
                    )
                }
            }
        }
    }

    override fun onCleared() {
        cameraRebindJob?.cancel()
        cameraManager.release()
        statsManager.cleanup()
    }

    private companion object {
        const val CAMERA_REBIND_DEBOUNCE_MS = 300L
    }
}
