package com.pilerks1.hdrrecorder.ui.viewmodels

import com.pilerks1.hdrrecorder.data.diagnostics.ErrorReporter
import com.pilerks1.hdrrecorder.data.diagnostics.IssueCode

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.net.toUri
import com.pilerks1.hdrrecorder.data.BindOutcome
import com.pilerks1.hdrrecorder.data.CameraManager
import com.pilerks1.hdrrecorder.data.Bitrate
import com.pilerks1.hdrrecorder.data.PresetManager
import com.pilerks1.hdrrecorder.model.CameraPreset
import com.pilerks1.hdrrecorder.model.PresetSettings
import com.pilerks1.hdrrecorder.model.presetNameError
import com.pilerks1.hdrrecorder.data.PreferencesManager
import com.pilerks1.hdrrecorder.data.RecordingManager
import com.pilerks1.hdrrecorder.data.RecordingConfiguration
import com.pilerks1.hdrrecorder.data.StatsManager
import com.pilerks1.hdrrecorder.data.storage.RecordingDestination
import com.pilerks1.hdrrecorder.data.storage.RecordingStorageManager
import com.pilerks1.hdrrecorder.data.camera.CameraControlCoordinator
import com.pilerks1.hdrrecorder.data.camera.DesiredCameraState
import com.pilerks1.hdrrecorder.data.camera.core.LowLightBoostPolicy
import com.pilerks1.hdrrecorder.data.camera.core.ExposureMode
import com.pilerks1.hdrrecorder.data.camera.core.CameraRequestSupersededException
import com.pilerks1.hdrrecorder.data.camera.CameraRuntime
import com.pilerks1.hdrrecorder.data.camera.CameraRuntimeEventResult
import com.pilerks1.hdrrecorder.data.camera.core.InteropSettings
import com.pilerks1.hdrrecorder.data.camera.core.Camera2Control
import com.pilerks1.hdrrecorder.data.camera.core.Camera2Settings
import com.pilerks1.hdrrecorder.data.camera.TelemetryCoordinator
import com.pilerks1.hdrrecorder.model.RecordingAspectRatio
import com.pilerks1.hdrrecorder.model.Resolution
import com.pilerks1.hdrrecorder.model.VideoColorOption
import com.pilerks1.hdrrecorder.ui.CameraUiEvent
import com.pilerks1.hdrrecorder.ui.CameraUiState
import com.pilerks1.hdrrecorder.ui.ManualControlsState
import com.pilerks1.hdrrecorder.ui.resetManualSlidersToAuto
import com.pilerks1.hdrrecorder.ui.manualcontrols.availableControlPanels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Managers are constructor-injected (via CameraViewModelFactory in MainActivity) so the
 * ViewModel no longer constructs its own Android dependencies.
 */
internal class CameraViewModel(
    private val statsManager: StatsManager,
    private val telemetryCoordinator: TelemetryCoordinator,
    private val preferencesManager: PreferencesManager,
    private val presetManager: PresetManager,
    private val cameraManager: CameraManager,
    private val recordingManager: RecordingManager,
    private val recordingStorage: RecordingStorageManager,
    private val controlCoordinator: CameraControlCoordinator,
    private val cameraRuntime: CameraRuntime,
    private val reporter: ErrorReporter
) : ViewModel() {

    // --- Deferred settings and camera binding state ---
    private var pendingHardRebind = false
    private var pendingSoftRebind = false
    private var cameraLifecycleOwner: LifecycleOwner? = null
    private var lastDisplayRotation = 0
    private var cameraRebindJob: Job? = null
    private var recordingStartJob: Job? = null
    private var exposureTransitionJob: Job? = null
    private var exposureTransitionGeneration = 0L

    // --- UI State ---
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()
    val stats = telemetryCoordinator.statsState
    val recordingDurationNanos = telemetryCoordinator.recordingDurationNanos
    val sliderTelemetry = telemetryCoordinator.sliderTelemetry

    private var presetDocuments: List<CameraPreset> = emptyList()

    init {
        _uiState.value = (cameraRuntime.initialUiState() ?: CameraUiState()).copy(
            storageUri = preferencesManager.storageUri
        )
        reporter.contextProvider = {
            val state = _uiState.value
            RecordingConfiguration.diagnosticContext(state.toCaptureConfiguration()) +
                mapOf("cameraId" to (state.zoomState.inventory?.defaultCameraId ?: "unavailable"),
                "recording" to state.isRecording.toString(),
                "destination" to if (state.storageUri == null) "default" else "custom")
        }
        initializePresets()

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

    /**
     * Helper to automatically apply state whenever a setting changes.
     * Defer camera rebinds if the Settings UI is currently open.
     */
    private fun updateSettings(requiresHardRebind: Boolean = false, update: (CameraUiState) -> CameraUiState) {
        if (isRecordingBusy()) return
        val wasBoostSelected = _uiState.value.manualControlsState.isLowLightBoostEnabled
        _uiState.update { update(it).normalizeLowLightBoost() }
        if (!cameraRuntime.usesCameraHardware) return

        if (wasBoostSelected && !_uiState.value.manualControlsState.isLowLightBoostEnabled) applyInterop()
        when {
            requiresHardRebind -> requestRebind()
            // Defer changes while in settings UI
            _uiState.value.isSettingsSheetVisible -> pendingSoftRebind = true
            else -> applyInterop()
        }
    }

    /** Rebinds now (debounced), or after Settings closes when it is open. */
    private fun requestRebind() {
        if (!cameraRuntime.usesCameraHardware) return
        if (_uiState.value.isSettingsSheetVisible) pendingHardRebind = true
        else scheduleCameraRebind(debounce = true)
    }

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
            // All document operations share a single busy gate, including recording startup.
            CameraUiEvent.RetryPresets -> if (!_uiState.value.arePresetsInitialized) initializePresets()
            is CameraUiEvent.AddPreset -> runPresetOperation {
                val name = event.name.trim()
                presetNameError(name, presetDocuments.map { it.name })?.let { throw IllegalArgumentException(it) }
                val preset = _uiState.value.toPreset(PresetManager.newId(), name)
                presetManager.save(preset, create = true)
                refreshPresets()
                selectSavedPreset(preset)
            }
            CameraUiEvent.SavePreset -> runPresetOperation {
                val state = _uiState.value
                val preset = state.toPreset(requireNotNull(state.currentPresetId), state.currentPresetName)
                presetManager.save(preset)
                refreshPresets()
                selectSavedPreset(preset)
            }
            is CameraUiEvent.LoadPreset -> runPresetOperation {
                val id = presetDocuments.first { it.name == event.name }.id
                val preset = presetManager.read(id)
                applyPreset(preset)
                selectSavedPreset(preset)
                requestRebind()
            }
            is CameraUiEvent.DeletePreset -> runPresetOperation {
                val id = presetDocuments.first { it.name == event.name }.id
                presetManager.delete(id)
                refreshPresets()
                selectAfterDeletion()
            }
            CameraUiEvent.DeleteAllPresets -> runPresetOperation {
                for (preset in presetDocuments) presetManager.delete(preset.id)
                refreshPresets()
                selectAfterDeletion()
            }
            is CameraUiEvent.SetSaveRecordingOptions -> _uiState.update { it.copy(saveRecordingOptions = event.enabled) }
            is CameraUiEvent.SetSaveStorageLocation -> _uiState.update { it.copy(saveStorageLocation = event.enabled) }

            // Camera Toggles
            is CameraUiEvent.ToggleRecording -> toggleRecording()
            is CameraUiEvent.TogglePause -> togglePause()

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
                RecordingSetting.BIT_DEPTH, _uiState.value.copy(videoBitDepth = event.bitDepth))
            is CameraUiEvent.SetEncoding -> selectVideoConfiguration(
                RecordingSetting.ENCODING, _uiState.value.copy(encoding = event.encoding))
            is CameraUiEvent.SetVideoCodec -> selectVideoConfiguration(
                RecordingSetting.CODEC, _uiState.value.copy(videoCodec = event.mimeType))
            is CameraUiEvent.SetAudioCodec -> {
                if (event.mimeType == null || event.mimeType in _uiState.value.recordingOptions.audioCodecs) {
                    updateSettings(requiresHardRebind = true) { it.copy(audioCodec = event.mimeType) }
                }
            }
            is CameraUiEvent.SetAudioBitrate -> if (Bitrate.isValidAudio(event.bitrate)) {
                updateSettings(requiresHardRebind = true) { it.copy(audioBitrate = event.bitrate) }
            }
            is CameraUiEvent.SetGammaSettings -> if (_uiState.value.encoding.supportsGammaCurveSelection &&
                event.settings.mode in _uiState.value.cameraCapabilities?.gammaModes.orEmpty()) {
                updateSettings(requiresHardRebind = false) { state ->
                    state.copy(gammaSettings = event.settings)
                }
            }

            is CameraUiEvent.SetBitrate -> if (Bitrate.isValid(event.bitrate)) {
                updateSettings(requiresHardRebind = true) { it.copy(bitrate = event.bitrate) }
            }
            is CameraUiEvent.SetStabilization -> updateSettings(requiresHardRebind = true) { it.copy(isStabilizationEnabled = event.enabled) }
            is CameraUiEvent.SetLowLightBoost -> setLowLightBoostRequested(event.enabled)
            is CameraUiEvent.SetZoomRatio -> {
                if (cameraRuntime.usesCameraHardware) cameraManager.setZoomRatio(event.ratio)
            }
            is CameraUiEvent.SelectZoomLens -> selectZoomLens(event.cameraId)

            is CameraUiEvent.SetCamera2OverrideEnabled -> {
                if (event.control != Camera2Control.CONTROL_AF_MODE || !_uiState.value.manualControlsState.isManualFocus) {
                    updateCamera2Settings { settings ->
                        settings.withOverride(event.control, settings[event.control].copy(enabled = event.enabled))
                    }
                }
            }
            is CameraUiEvent.SetCamera2Mode -> {
                val state = _uiState.value
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

            is CameraUiEvent.SetPreviewFrameRate -> {
                if (event.range == null || event.range in _uiState.value.previewOptions.frameRates) {
                    updateSettings(requiresHardRebind = true) {
                        it.copy(previewSettings = it.previewSettings.copy(frameRate = event.range))
                    }
                }
            }
            is CameraUiEvent.SetPreviewDynamicRange -> {
                val state = _uiState.value
                if (event.dynamicRange != null && event.dynamicRange !in state.previewOptions.dynamicRanges) return
                val requested = state.copy(previewSettings = state.previewSettings.copy(dynamicRange = event.dynamicRange))
                val error = if (cameraRuntime.usesCameraHardware) {
                    cameraManager.previewDynamicRangeError(requested.toCaptureConfiguration())
                } else null
                if (error == null) {
                    updateSettings(requiresHardRebind = true) { requested }
                }
            }

            is CameraUiEvent.SetRespectDisplayCutouts -> {
                if (!isRecordingBusy()) {
                    _uiState.update { it.copy(isRespectDisplayCutoutsEnabled = event.enabled) }
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
                _uiState.update { it.copy(storageUri = event.uri) }
            }

            is CameraUiEvent.StartMetering -> controlCoordinator.startMetering(
                event.point, event.anchor, event.mode, event.generation
            )
            is CameraUiEvent.RemapMetering -> controlCoordinator.remapMetering(
                event.point, event.generation, event.requestId
            )
            is CameraUiEvent.CancelMetering -> controlCoordinator.cancelMetering(event.generation)
            is CameraUiEvent.OpenSettings -> _uiState.update { it.copy(isSettingsSheetVisible = true) }
            is CameraUiEvent.CloseSettings -> {
                _uiState.update { it.copy(isSettingsSheetVisible = false) }
                val hardRebind = pendingHardRebind
                val softRebind = pendingSoftRebind
                pendingHardRebind = false
                pendingSoftRebind = false
                if (hardRebind) {
                    scheduleCameraRebind(debounce = false)
                } else if (softRebind) {
                    applyInterop()
                }
            }
            is CameraUiEvent.TogglePanel -> Unit // Handled before the preset and recording gates.
        }
    }

    private fun runPresetOperation(operation: suspend () -> Unit) {
        if (_uiState.value.isPresetBusy || isRecordingBusy()) return
        _uiState.update { it.copy(isPresetBusy = true) }
        viewModelScope.launch {
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Refresh after partial operations; retain secondary failures on the original report.
                // A bulk deletion may have removed some files before failing.
                try {
                    refreshPresets()
                    if (_uiState.value.arePresetsInitialized) selectAfterDeletion()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (refreshError: Exception) {
                    error.addSuppressed(refreshError)
                }
                reporter.report(IssueCode.PRESET_OPERATION, "presetOperation", error)
            } finally {
                _uiState.update { it.copy(isPresetBusy = false) }
            }
        }
    }

    private fun initializePresets() {
        runPresetOperation {
            refreshPresets()
            if (presetDocuments.isEmpty()) createDefaultPreset()
            val preset = presetDocuments.firstOrNull { it.id == preferencesManager.selectedPresetId }
                ?: presetDocuments.first()
            applyPreset(preset)
            selectSavedPreset(preset)
            _uiState.update { it.copy(arePresetsInitialized = true) }
            scheduleCameraRebind(debounce = false)
        }
    }

    private suspend fun refreshPresets() {
        presetDocuments = presetManager.list()
        _uiState.update { it.copy(presetsList = presetDocuments.map { preset -> preset.name }) }
    }

    private fun selectSavedPreset(preset: CameraPreset) {
        preferencesManager.selectedPresetId = preset.id
        _uiState.update { it.copy(currentPresetId = preset.id, currentPresetName = preset.name) }
    }

    private suspend fun createDefaultPreset(): CameraPreset {
        val preset = CameraPreset(PresetManager.newId(), "Default", PresetSettings())
        presetManager.save(preset, create = true)
        refreshPresets()
        return preset
    }

    private suspend fun selectAfterDeletion() {
        if (presetDocuments.any { it.id == _uiState.value.currentPresetId }) return
        val reset = presetDocuments.isEmpty()
        val preset = if (reset) createDefaultPreset() else
            presetDocuments.firstOrNull { it.name == "Default" } ?: presetDocuments.first()
        if (reset) {
            _uiState.update { it.copy(
                selectedFps = 30,
                selectedResolution = Resolution.FHD,
                selectedAspectRatio = RecordingAspectRatio.FOUR_THREE,
                manualControlsState = ManualControlsState(),
                zoomState = it.zoomState.copy(ratio = 1f)
            ) }
        }
        applyPreset(preset)
        selectSavedPreset(preset)
        requestRebind()
    }

    private suspend fun applyPreset(preset: CameraPreset) {
        var destination = _uiState.value.storageUri
        if (preset.saveStorageLocation) {
            val saved = preset.storageUri
            val usable = saved?.let { recordingStorage.checkPresetDestination(it) } ?: Result.success(true)
            if (usable.getOrDefault(false)) destination = saved
            else reporter.report(IssueCode.PRESET_FOLDER, "applyPreset", usable.exceptionOrNull(),
                mapOf("recovery" to "current folder kept"))
        }
        exposureTransitionJob?.cancel()
        exposureTransitionGeneration++
        controlCoordinator.invalidateLowLightBoostRequest()
        _uiState.update { it.withPreset(preset).copy(storageUri = destination, isExposureTransitioning = false) }
        preferencesManager.storageUri = destination
        statsManager.updateStorageUri(destination)
        normalizeManualControls()
        if (!cameraRuntime.usesCameraHardware) {
            _uiState.update { it.copy(zoomState = it.zoomState.let { zoom ->
                zoom.copy(ratio = zoom.ratio.coerceIn(zoom.minRatio, zoom.maxRatio))
            }) }
        }
    }

    private fun normalizeManualControls() {
        val caps = _uiState.value.cameraCapabilities ?: return
        _uiState.update {
            it.copy(manualControlsState = ManualControlStateUpdater.normalize(it.manualControlsState, caps, it.selectedFps, it.previewSettings.frameRate))
        }
    }

    private fun updateCamera2Settings(update: (Camera2Settings) -> Camera2Settings) {
        if (isRecordingBusy()) return
        val capabilities = _uiState.value.cameraCapabilities?.camera2Modes ?: return
        _uiState.update { state ->
            state.copy(camera2Settings = update(state.camera2Settings).reconcile(capabilities))
        }
        // Camera2 overrides apply immediately, including while the settings screen is open.
        applyInterop()
    }

    /**
     * Keeps CameraX aligned with the rotation of the Activity's actual display.
     * The Activity itself is sensor-driven by Android (fullSensor), so this callback
     * never decides or requests a screen orientation and never rebinds the camera.
     * Capture rotation stays frozen for the duration of a recording.
     */
    fun onDisplayRotationChanged(rotation: Int) {
        if (rotation != lastDisplayRotation) controlCoordinator.cancelMetering()
        lastDisplayRotation = rotation
        if (!cameraRuntime.usesCameraHardware || _uiState.value.isRecording) return
        cameraManager.updateRotation(rotation)
    }

    private fun toggleRecording() {
        if (!cameraRuntime.usesCameraHardware) return
        if (_uiState.value.isRecording) {
            recordingManager.stopRecording()
            return
        }
        // A scheduled or deferred rebind would interrupt the recording, so it must finish first.
        if (isRecordingBusy() || cameraRebindJob?.isActive == true || pendingHardRebind) return
        val state = _uiState.value
        if (!state.isCameraReady || state.isSettingsSheetVisible) return
        val videoCapture = cameraManager.videoCapture ?: return
        recordingStartJob = viewModelScope.launch {
            val result = recordingManager.startRecording(
                videoCapture, RecordingDestination.fromStorageUri(state.storageUri)
            )
            if (result !is RecordingManager.StartResult.Started) return@launch
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

    fun updateManualControls(updater: (ManualControlsState) -> ManualControlsState) {
        val currentUi = _uiState.value
        if (currentUi.isExposureTransitioning || currentUi.isPresetBusy || !currentUi.arePresetsInitialized) return
        val caps = currentUi.cameraCapabilities
        val telemetry = telemetryCoordinator.currentCameraTelemetry()
        val oldControls = currentUi.manualControlsState
        val exposureDefaults = ManualControlStateUpdater.ExposureDefaults(
            isoValue = telemetry.iso,
            shutterNanos = telemetry.shutterNanos,
            postRawSensitivityBoost = telemetry.postRawSensitivityBoost ?: 100
        )
        val (newState, triggersRebind) = ManualControlStateUpdater.calculateNextState(
            oldControls,
            caps,
            exposureDefaults,
            currentUi.selectedFps,
            currentUi.previewSettings.frameRate,
            updater
        )
        if (!cameraRuntime.usesCameraHardware) {
            _uiState.update { it.copy(manualControlsState = newState) }
            return
        }

        if (
            caps != null &&
            ExposureMode.resolve(oldControls, caps) != ExposureMode.resolve(newState, caps)
        ) {
            beginExposureTransition(
                oldControls = oldControls,
                targetControls = newState,
                defaults = exposureDefaults,
                triggersRebind = triggersRebind
            )
            return
        }

        _uiState.update { it.copy(manualControlsState = newState) }

        if (triggersRebind) requestRebind()
        applyInterop(coalesce = true)
    }

    private fun beginExposureTransition(
        oldControls: ManualControlsState,
        targetControls: ManualControlsState,
        defaults: ManualControlStateUpdater.ExposureDefaults,
        triggersRebind: Boolean
    ) {
        exposureTransitionJob?.cancel()
        val generation = ++exposureTransitionGeneration
        _uiState.update {
            it.copy(
                manualControlsState = it.manualControlsState.copy(isLowLightBoostEnabled = targetControls.isLowLightBoostEnabled),
                isExposureTransitioning = true
            )
        }

        exposureTransitionJob = viewModelScope.launch {
            var resolved: ManualControlsState? = null
            try {
                resolved = controlCoordinator.transition(
                    previous = oldControls.copy(
                        isoValue = oldControls.isoValue ?: defaults.isoValue,
                        ssValueNanos = oldControls.ssValueNanos ?: defaults.shutterNanos,
                        postRawSensitivityBoost = oldControls.postRawSensitivityBoost ?: defaults.postRawSensitivityBoost
                    ),
                    target = desiredState(targetControls),
                    waitToLockAe = _uiState.value.isWaitToLockAeEnabled
                )
            } catch (_: CameraRequestSupersededException) {
                // A rebind or newer transition owns the camera now.
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                reporter.report(IssueCode.CAMERA_CONTROL, "exposureTransition", error)
            } finally {
                if (generation == exposureTransitionGeneration) {
                    _uiState.update {
                        it.copy(
                            manualControlsState = resolved ?: it.manualControlsState,
                            isExposureTransitioning = false
                        )
                    }
                }
            }
            if (resolved == null || generation != exposureTransitionGeneration) return@launch
            if (triggersRebind) requestRebind()
        }
    }

    /** Attaches the current lifecycle and starts the initial bind without an arbitrary delay. */
    fun attachCamera(lifecycleOwner: LifecycleOwner, displayRotation: Int) {
        if (!cameraRuntime.usesCameraHardware) return
        val lifecycleChanged = cameraLifecycleOwner !== lifecycleOwner
        if (lifecycleChanged) {
            // The owner is the Activity; this ViewModel outlives it and must not retain it.
            lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    if (cameraLifecycleOwner !== owner) return
                    cameraLifecycleOwner = null
                    cameraRebindJob?.cancel()
                }
            })
        }
        cameraLifecycleOwner = lifecycleOwner
        lastDisplayRotation = displayRotation
        scheduleCameraRebind(debounce = !lifecycleChanged)
    }

    /**
     * Coalesces quick changes (such as 24 -> 30 -> 60 FPS) but never waits for a guessed camera
     * startup time. CameraManager binds only after the ProcessCameraProvider is actually ready.
     * While the debounce is pending, recording cannot start; a bind that finds a recording in
     * progress is deferred until the recording finalizes.
     */
    private fun scheduleCameraRebind(debounce: Boolean) {
        if (!cameraRuntime.usesCameraHardware || !_uiState.value.arePresetsInitialized) return
        val lifecycleOwner = cameraLifecycleOwner ?: return
        cameraRebindJob?.cancel()
        exposureTransitionJob?.cancel()
        exposureTransitionGeneration++
        _uiState.update { it.copy(isExposureTransitioning = false) }

        fun bind() {
            if (isRecordingBusy()) {
                pendingHardRebind = true
                return
            }
            _uiState.update { it.copy(isCameraReady = false) }
            cameraManager.requestBindWhenReady(
                lifecycleOwner = lifecycleOwner,
                onSurfaceRequest = { request -> _surfaceRequest.value = request },
                configuration = _uiState.value.toCaptureConfiguration(),
                displayRotation = lastDisplayRotation,
                onResult = ::onBindResult
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

    private fun onBindResult(outcome: BindOutcome) {
        when (outcome) {
            is BindOutcome.Bound -> {
                val caps = outcome.capabilities
                _uiState.update {
                    it.copy(
                        cameraCapabilities = caps,
                        previewOptions = outcome.previewOptions,
                        previewSettings = outcome.previewSettings,
                        camera2Settings = it.camera2Settings.reconcile(caps.camera2Modes),
                        activePanel = it.activePanel?.takeIf { panel -> panel in availableControlPanels(caps) },
                        manualControlsState = ManualControlStateUpdater.normalize(
                            it.manualControlsState, caps, it.selectedFps, outcome.previewSettings.frameRate
                        ),
                        isCameraReady = true
                    )
                }
                applyInterop()
            }
            // The rejected settings never reached the camera; show what is actually bound.
            is BindOutcome.Retained -> {
                _uiState.update {
                    it.withCaptureConfiguration(outcome.configuration).normalizeLowLightBoost()
                        .copy(isCameraReady = it.zoomState.isReady)
                }
                applyInterop()
            }
            BindOutcome.Failed -> Unit // Nothing is bound, so the camera remains unready.
        }
    }

    private fun requestedFpsForStats(): Int? = _uiState.value.selectedFps
        .takeUnless { _uiState.value.manualControlsState.isManualFps }

    private fun CameraUiState.normalizeLowLightBoost(): CameraUiState {
        val caps = cameraCapabilities ?: return this
        return copy(manualControlsState = LowLightBoostPolicy.normalize(
            manualControlsState, caps.supportsLowLightBoost, selectedFps, previewSettings.frameRate
        ))
    }

    private fun setLowLightBoostRequested(enabled: Boolean) {
        val current = _uiState.value
        if (enabled && (!current.isLowLightBoostEligible || !current.isCameraReady || current.isExposureTransitioning)) return
        if (current.manualControlsState.isLowLightBoostEnabled == enabled) return
        _uiState.update { it.copy(manualControlsState = it.manualControlsState.copy(isLowLightBoostEnabled = enabled)) }
        applyInterop()
    }

    private fun desiredState(controls: ManualControlsState = _uiState.value.manualControlsState): DesiredCameraState {
        val state = _uiState.value
        return DesiredCameraState(
            controls = controls,
            settings = InteropSettings(
                gamma = state.gammaSettings.takeIf { state.encoding.supportsGammaCurveSelection },
                camera2 = state.camera2Settings
            ),
            capabilities = state.cameraCapabilities,
            selectedFps = state.selectedFps,
            previewFpsRange = state.previewSettings.frameRate
        )
    }

    private fun applyInterop(coalesce: Boolean = false) {
        if (cameraRuntime.usesCameraHardware) controlCoordinator.update(desiredState(), coalesce)
    }

    private enum class RecordingSetting { CODEC, BIT_DEPTH, ENCODING }

    private var recordingPriority = listOf(RecordingSetting.CODEC, RecordingSetting.BIT_DEPTH, RecordingSetting.ENCODING)

    private fun selectVideoConfiguration(touched: RecordingSetting, requested: CameraUiState) {
        val state = _uiState.value
        val options = state.recordingOptions
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
            it.copy(
                videoCodec = selected.first,
                videoBitDepth = selected.second.bitDepth,
                encoding = selected.second.encoding,
            )
        }
    }

    fun finishManualControlInteraction() = applyInterop()

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
                    if (pendingHardRebind && !state.isSettingsSheetVisible) {
                        pendingHardRebind = false
                        scheduleCameraRebind(debounce = false)
                    }
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

    private companion object {
        const val CAMERA_REBIND_DEBOUNCE_MS = 300L
    }
}
