package com.pilerks1.hdrrecorder.ui.camera.state

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pilerks1.hdrrecorder.camera.camerax.CameraControlCoordinator
import com.pilerks1.hdrrecorder.camera.camerax.CameraXRecordingConfiguration
import com.pilerks1.hdrrecorder.camera.camerax.CameraXRecordingManager
import com.pilerks1.hdrrecorder.camera.camerax.CameraXSessionManager
import com.pilerks1.hdrrecorder.camera.model.Bitrate
import com.pilerks1.hdrrecorder.data.diagnostics.ErrorReporter
import com.pilerks1.hdrrecorder.data.presets.PresetManager
import com.pilerks1.hdrrecorder.data.storage.AppPreferences
import com.pilerks1.hdrrecorder.data.storage.RecordingDestination
import com.pilerks1.hdrrecorder.data.storage.RecordingStorageManager
import com.pilerks1.hdrrecorder.data.telemetry.StatsManager
import com.pilerks1.hdrrecorder.data.telemetry.TelemetryCoordinator
import com.pilerks1.hdrrecorder.ui.camera.CameraRuntime
import com.pilerks1.hdrrecorder.ui.camera.CameraRuntimeEventResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns camera screen state, event gates, recording actions, and state collection.
 * CameraViewModelFactory supplies platform dependencies; controllers share this screen scope.
 */
internal class CameraViewModel(
    private val statsManager: StatsManager,
    private val telemetryCoordinator: TelemetryCoordinator,
    preferencesManager: AppPreferences,
    presetManager: PresetManager,
    private val cameraManager: CameraXSessionManager,
    private val recordingManager: CameraXRecordingManager,
    recordingStorage: RecordingStorageManager,
    private val controlCoordinator: CameraControlCoordinator,
    private val cameraRuntime: CameraRuntime,
    private val reporter: ErrorReporter
) : ViewModel() {

    private var recordingStartJob: Job? = null

    // --- UI State ---
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()
    val stats = telemetryCoordinator.statsState
    val recordingDurationNanos = telemetryCoordinator.recordingDurationNanos
    val sliderTelemetry = telemetryCoordinator.sliderTelemetry

    private val manualControls: ManualControlsController = ManualControlsController(
        uiState = _uiState,
        scope = viewModelScope,
        telemetryCoordinator = telemetryCoordinator,
        controlCoordinator = controlCoordinator,
        cameraRuntime = cameraRuntime,
        reporter = reporter,
        requestRebind = { binding.requestRebind() }
    )
    private val binding: CameraBindingController = CameraBindingController(
        uiState = _uiState,
        scope = viewModelScope,
        surfaceRequests = _surfaceRequest,
        cameraManager = cameraManager,
        controlCoordinator = controlCoordinator,
        cameraRuntime = cameraRuntime,
        isRecordingBusy = ::isRecordingBusy,
        cancelExposureTransition = manualControls::cancelTransition,
        applyCameraControls = { manualControls.applyCameraControls() }
    )
    private val presets = CameraPresetController(
        uiState = _uiState,
        scope = viewModelScope,
        presetManager = presetManager,
        preferencesManager = preferencesManager,
        recordingStorage = recordingStorage,
        statsManager = statsManager,
        controlCoordinator = controlCoordinator,
        cameraRuntime = cameraRuntime,
        reporter = reporter,
        isRecordingBusy = ::isRecordingBusy,
        requestRebind = binding::requestRebind,
        bindImmediately = binding::bindImmediately,
        cancelExposureTransition = manualControls::cancelTransition,
        normalizeManualControls = manualControls::normalizeManualControls
    )
    private val settings = CameraSettingsController(
        uiState = _uiState,
        preferencesManager = preferencesManager,
        recordingStorage = recordingStorage,
        statsManager = statsManager,
        cameraRuntime = cameraRuntime,
        reporter = reporter,
        binding = binding,
        isRecordingBusy = ::isRecordingBusy,
        applyCameraControls = { manualControls.applyCameraControls() }
    )

    init {
        _uiState.value = (cameraRuntime.initialUiState() ?: CameraUiState()).copy(
            storageUri = preferencesManager.storageUri
        )
        reporter.contextProvider = {
            val state = _uiState.value
            CameraXRecordingConfiguration.diagnosticContext(state.toCaptureConfiguration()) +
                mapOf("cameraId" to (state.zoomState.inventory?.defaultCameraId ?: "unavailable"),
                "recording" to state.isRecording.toString(),
                "destination" to if (state.storageUri == null) "default" else "custom")
        }
        presets.initializePresets()

        // The recording-state collector also delivers the initial stats state.
        collectRecordingState()
        collectLowLightBoostState()
        viewModelScope.launch {
            controlCoordinator.lowLightBoostRequest.collect { request ->
                _uiState.update { it.copy(lowLightBoostRequest = request) }
            }
        }

        collectZoomState()
        viewModelScope.launch {
            cameraManager.recordingOptions.collect { options ->
                _uiState.update { it.copy(recordingOptions = options) }
            }
        }
        viewModelScope.launch {
            controlCoordinator.meteringState.collect { state ->
                _uiState.update { it.copy(meteringState = state) }
            }
        }
    }

    /** True from the moment a recording start is requested until its finalization completes. */
    private fun isRecordingBusy(): Boolean =
        recordingManager.isRecording.value || recordingStartJob?.isActive == true

    fun onEvent(event: CameraUiEvent) {
        // Panel selection is presentation state only; it never touches camera settings.
        if (event is CameraUiEvent.TogglePanel) {
            _uiState.update { it.copy(activePanel = if (it.activePanel == event.panel) null else event.panel) }
            return
        }
        if (_uiState.value.isPresetBusy) return
        if (!_uiState.value.arePresetsInitialized && event != CameraUiEvent.RetryPresets &&
            event != CameraUiEvent.OpenSettings && event != CameraUiEvent.CloseSettings) return
        // isRecording stays true until Finalize, including after stop was requested.
        if (isRecordingBusy() && when (event) {
            is CameraUiEvent.AddPreset, CameraUiEvent.SavePreset,
            is CameraUiEvent.SetSaveRecordingOptions, is CameraUiEvent.SetSaveStorageLocation,
            is CameraUiEvent.LoadPreset, is CameraUiEvent.DeletePreset,
            CameraUiEvent.DeleteAllPresets, CameraUiEvent.OpenSettings,
            is CameraUiEvent.SetStorageUri -> true
            else -> false
        }) return
        when (val runtimeResult = cameraRuntime.handleEvent(event, _uiState.value)) {
            CameraRuntimeEventResult.NotHandled -> Unit
            is CameraRuntimeEventResult.Handled -> {
                runtimeResult.uiState?.let { _uiState.value = it }
                return
            }
        }

        when (event) {
            CameraUiEvent.RetryPresets, is CameraUiEvent.AddPreset, CameraUiEvent.SavePreset,
            is CameraUiEvent.LoadPreset, is CameraUiEvent.DeletePreset, CameraUiEvent.DeleteAllPresets,
            is CameraUiEvent.SetSaveRecordingOptions, is CameraUiEvent.SetSaveStorageLocation -> presets.onEvent(event)

            // Camera Toggles
            is CameraUiEvent.ToggleRecording -> toggleRecording()
            is CameraUiEvent.TogglePause -> togglePause()

            CameraUiEvent.CycleFps, CameraUiEvent.CycleResolution, CameraUiEvent.CycleAspectRatio,
            is CameraUiEvent.SetVideoBitDepth, is CameraUiEvent.SetEncoding, is CameraUiEvent.SetVideoCodec,
            is CameraUiEvent.SetAudioCodec, is CameraUiEvent.SetAudioBitrate, is CameraUiEvent.SetGammaSettings,
            is CameraUiEvent.SetBitrate, is CameraUiEvent.SetStabilization,
            is CameraUiEvent.SetCamera2OverrideEnabled, is CameraUiEvent.SetCamera2Mode,
            is CameraUiEvent.SetExtendManualRanges, is CameraUiEvent.SetWaitToLockAe,
            is CameraUiEvent.SetPreviewFrameRate, is CameraUiEvent.SetPreviewDynamicRange,
            is CameraUiEvent.SetRespectDisplayCutouts, is CameraUiEvent.SetForceDisplaySdr,
            is CameraUiEvent.SetStorageUri -> settings.onEvent(event)

            is CameraUiEvent.SetLowLightBoost -> manualControls.setLowLightBoostRequested(event.enabled)
            is CameraUiEvent.SetZoomRatio -> {
                if (cameraRuntime.usesCameraHardware) cameraManager.setZoomRatio(event.ratio)
            }
            is CameraUiEvent.SelectZoomLens -> selectZoomLens(event.cameraId)

            is CameraUiEvent.StartMetering -> controlCoordinator.startMetering(
                event.point, event.anchor, event.mode, event.generation
            )
            is CameraUiEvent.RemapMetering -> controlCoordinator.remapMetering(
                event.point, event.generation, event.requestId
            )
            is CameraUiEvent.CancelMetering -> controlCoordinator.cancelMetering(event.generation)
            is CameraUiEvent.OpenSettings -> _uiState.update { it.copy(isSettingsSheetVisible = true) }
            is CameraUiEvent.CloseSettings -> binding.onSettingsClosed()
            is CameraUiEvent.TogglePanel -> Unit // Handled before the preset and recording gates.
        }
    }

    private fun toggleRecording() {
        if (!cameraRuntime.usesCameraHardware) return
        if (_uiState.value.isRecording) {
            recordingManager.stopRecording()
            return
        }
        // A scheduled or deferred rebind would interrupt the recording, so it must finish first.
        if (isRecordingBusy() || binding.isRebindPending) return
        val state = _uiState.value
        if (!state.isCameraReady || state.isSettingsSheetVisible) return
        val videoCapture = cameraManager.videoCapture ?: return
        recordingStartJob = viewModelScope.launch {
            val result = recordingManager.startRecording(
                videoCapture, RecordingDestination.fromStorageUri(state.storageUri)
            )
            if (result !is CameraXRecordingManager.StartResult.Started) return@launch
            telemetryCoordinator.startFpsCalculation()
            statsManager.setRecordingState(true, requestedFpsForStats(), Bitrate.parse(state.bitrate) ?: 0,
                result.destination.toStorageUri())
        }
    }

    private fun togglePause() {
        if (!cameraRuntime.usesCameraHardware) return
        val changed = if (_uiState.value.isPaused) {
            recordingManager.resumeRecording()
        } else {
            recordingManager.pauseRecording()
        }
        if (changed) _uiState.update { it.copy(isPaused = !it.isPaused) }
    }

    private fun selectZoomLens(cameraId: String) {
        val lens = _uiState.value.zoomState.shortcuts
            .firstOrNull { it.cameraId == cameraId } ?: return
        cameraManager.setZoomRatio(lens.zoomRatio)
    }

    private fun requestedFpsForStats(): Int? = _uiState.value.selectedFps
        .takeUnless { _uiState.value.manualControlsState.isManualFps }

    fun attachCamera(lifecycleOwner: LifecycleOwner, displayRotation: Int) =
        binding.attachCamera(lifecycleOwner, displayRotation)

    fun onDisplayRotationChanged(rotation: Int) = binding.onDisplayRotationChanged(rotation)

    fun updateManualControls(updater: (ManualControlsState) -> ManualControlsState) =
        manualControls.updateManualControls(updater)

    fun finishManualControlInteraction() = manualControls.applyCameraControls()

    // --- State Collection ---
    private fun collectRecordingState() {
        viewModelScope.launch {
            recordingManager.isRecording.collect { isRecording ->
                _uiState.update { it.copy(isRecording = isRecording, isPaused = if (!isRecording) false else it.isPaused) }
                if (!isRecording) {
                    telemetryCoordinator.stopFpsCalculation()
                    val state = _uiState.value
                    statsManager.setRecordingState(
                        recording = false,
                        targetFps = requestedFpsForStats(),
                        targetBitrate = (Bitrate.parse(state.bitrate) ?: 0),
                        storageUri = state.storageUri
                    )
                    // Apply a rebind that was deferred so it would not interrupt the recording.
                    binding.onRecordingFinalized()
                }
            }
        }
    }

    private fun collectLowLightBoostState() {
        viewModelScope.launch {
            cameraManager.lowLightBoostState.collect { state ->
                _uiState.update { it.copy(lowLightBoostState = state) }
            }
        }
    }

    private fun collectZoomState() {
        if (!cameraRuntime.usesCameraHardware) return
        viewModelScope.launch {
            cameraManager.zoomState.collect { zoom ->
                _uiState.update { it.copy(zoomState = zoom, isCameraReady = zoom.isReady) }
            }
        }
    }

    override fun onCleared() {
        reporter.contextProvider = null
        cameraManager.release()
        controlCoordinator.close()
        telemetryCoordinator.cleanup()
        statsManager.cleanup()
    }

}
