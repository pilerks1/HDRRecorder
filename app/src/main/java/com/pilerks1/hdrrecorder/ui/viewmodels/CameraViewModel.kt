package com.pilerks1.hdrrecorder.ui.viewmodels

import com.pilerks1.hdrrecorder.data.diagnostics.ErrorReporter
import com.pilerks1.hdrrecorder.data.diagnostics.IssueCode

import android.app.Application
import android.util.Range
import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.core.net.toUri
import com.pilerks1.hdrrecorder.data.CameraManager
import com.pilerks1.hdrrecorder.data.Bitrate
import com.pilerks1.hdrrecorder.data.PresetManager
import com.pilerks1.hdrrecorder.model.CameraPreset
import com.pilerks1.hdrrecorder.model.PresetSettings
import com.pilerks1.hdrrecorder.data.PreferencesManager
import com.pilerks1.hdrrecorder.data.RecordingManager
import com.pilerks1.hdrrecorder.data.RecordingConfiguration
import com.pilerks1.hdrrecorder.data.StatsManager
import com.pilerks1.hdrrecorder.data.storage.RecordingDestination
import com.pilerks1.hdrrecorder.data.storage.RecordingStorageManager
import com.pilerks1.hdrrecorder.data.camera.CameraControlCoordinator
import com.pilerks1.hdrrecorder.data.camera.DesiredCameraState
import com.pilerks1.hdrrecorder.data.camera.ExposureMode
import com.pilerks1.hdrrecorder.data.camera.core.CameraControlValues
import com.pilerks1.hdrrecorder.data.camera.core.CameraRequestSupersededException
import com.pilerks1.hdrrecorder.data.camera.CameraRuntime
import com.pilerks1.hdrrecorder.data.camera.CameraRuntimeEventResult
import com.pilerks1.hdrrecorder.data.camera.core.InteropSettings
import com.pilerks1.hdrrecorder.data.camera.core.Camera2Control
import com.pilerks1.hdrrecorder.data.camera.core.Camera2Settings
import com.pilerks1.hdrrecorder.data.camera.TelemetryCoordinator
import com.pilerks1.hdrrecorder.model.VideoColorOption
import com.pilerks1.hdrrecorder.ui.CameraUiEvent
import com.pilerks1.hdrrecorder.ui.CameraUiState
import com.pilerks1.hdrrecorder.ui.ManualControl
import com.pilerks1.hdrrecorder.ui.ManualControlsState
import com.pilerks1.hdrrecorder.ui.resetManualSlidersToAuto
import com.pilerks1.hdrrecorder.ui.manualcontrols.FpsRangeBound
import com.pilerks1.hdrrecorder.ui.manualcontrols.FpsRangeMath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Managers are constructor-injected (via CameraViewModelFactory in MainActivity) so the
 * ViewModel no longer constructs its own Android dependencies.
 */
internal class CameraViewModel(
    application: Application,
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
) : AndroidViewModel(application) {

    // --- Deferred settings and camera binding state ---
    private var pendingHardRebind = false
    private var pendingSoftRebind = false
    private var cameraLifecycleOwner: LifecycleOwner? = null
    private var lastDisplayRotation = 0
    private var cameraRebindJob: kotlinx.coroutines.Job? = null
    private var lowLightBoostApplyJob: Job? = null
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
            RecordingConfiguration.diagnosticContext(state) +
                mapOf("cameraId" to (state.zoomState.inventory?.defaultCameraId ?: "unavailable"),
                "recording" to state.isRecording.toString(),
                "destination" to if (state.storageUri == null) "default" else "custom")
        }
        initializePresets()

        collectRecordingState()
        collectLowLightBoostState()

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
        statsManager.setRecordingState(false, requestedFpsForStats(), (Bitrate.parse(_uiState.value.bitrate) ?: 0), _uiState.value.storageUri)
    }

    /**
     * Helper to automatically apply state whenever a setting changes.
     * Defer camera rebinds if the Settings UI is currently open.
     */
    private fun updateSettings(requiresHardRebind: Boolean = false, update: (CameraUiState) -> CameraUiState) {
        if (recordingManager.isRecording.value) return
        _uiState.update { oldState -> update(oldState) }
        if (!cameraRuntime.usesCameraHardware) return

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
        if (_uiState.value.isPresetBusy) return
        if (!_uiState.value.arePresetsInitialized && event != CameraUiEvent.RetryPresets &&
            event != CameraUiEvent.OpenSettings && event != CameraUiEvent.CloseSettings) return
        // isRecording stays true until Finalize, including after stop was requested.
        if (recordingManager.isRecording.value && when (event) {
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
                require(name.isNotEmpty()) { "Enter a preset name" }
                require(presetDocuments.none { it.name.equals(name, ignoreCase = true) }) { "A preset with that name already exists" }
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
            is CameraUiEvent.CycleFps -> {
                var disabledLowLightBoost = false
                updateSettings(requiresHardRebind = true) { state ->
                    val newFps = when (state.selectedFps) { 24 -> 30; 30 -> 60; else -> 24 }
                    disabledLowLightBoost =
                        state.manualControlsState.isLowLightBoostEnabled &&
                            newFps > LOW_LIGHT_BOOST_MAX_FPS
                    state.copy(
                        selectedFps = newFps,
                        manualControlsState = if (disabledLowLightBoost) {
                            state.manualControlsState.copy(isLowLightBoostEnabled = false)
                        } else {
                            state.manualControlsState
                        }
                    )
                }
                if (disabledLowLightBoost) applyLowLightBoostPreference()
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
            is CameraUiEvent.SetAudioBitrate -> if (event.bitrate.isEmpty() || Bitrate.parseAudio(event.bitrate) != null) {
                updateSettings(requiresHardRebind = true) { it.copy(audioBitrate = event.bitrate) }
            }
            is CameraUiEvent.SetGammaSettings -> if (_uiState.value.encoding.supportsGammaCurveSelection &&
                event.settings.mode in _uiState.value.cameraCapabilities?.gammaModes.orEmpty()) {
                updateSettings(requiresHardRebind = false) { state ->
                    state.copy(gammaSettings = event.settings)
                }
            }

            is CameraUiEvent.SetBitrate -> {
                if (event.bitrate.isEmpty() || Bitrate.parse(event.bitrate) != null) {
                    updateSettings(requiresHardRebind = true) {
                        it.copy(bitrate = event.bitrate)
                    }
                }
            }
            is CameraUiEvent.SetStabilization -> updateSettings(requiresHardRebind = true) { it.copy(isStabilizationEnabled = event.enabled) }
            is CameraUiEvent.SetLowLightBoost -> setLowLightBoostRequested(event.enabled)
            is CameraUiEvent.SetLinearZoom -> {
                if (cameraRuntime.usesCameraHardware) {
                    cameraManager.setLinearZoom(event.progress)
                }
            }
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
                if (recordingManager.isRecording.value) return
                val state = _uiState.value
                if (event.dynamicRange != null && event.dynamicRange !in state.previewOptions.dynamicRanges) return
                val requested = state.copy(previewSettings = state.previewSettings.copy(dynamicRange = event.dynamicRange))
                val error = if (cameraRuntime.usesCameraHardware) cameraManager.previewDynamicRangeError(requested) else null
                if (error == null) {
                    updateSettings(requiresHardRebind = true) { requested }
                }
            }

            is CameraUiEvent.SetRespectDisplayCutouts -> {
                if (!recordingManager.isRecording.value) {
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

    private fun runPresetOperation(operation: suspend () -> Unit) {
        if (_uiState.value.isPresetBusy || recordingManager.isRecording.value) return
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
        _uiState.update { it.copy(isPresetBusy = false) }
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
        val listing = presetManager.list()
        presetDocuments = listing.presets
        _uiState.update { it.copy(presetsList = listing.presets.map { preset -> preset.name }) }
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
                selectedResolution = com.pilerks1.hdrrecorder.model.Resolution.FHD,
                selectedAspectRatio = com.pilerks1.hdrrecorder.model.RecordingAspectRatio.FOUR_THREE,
                manualControlsState = ManualControlsState()
            ) }
            _uiState.update { it.copy(zoomState = it.zoomState.copy(ratio = 1f)) }
        }
        applyPreset(preset)
        selectSavedPreset(preset)
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
        _uiState.update { it.withPreset(preset).copy(storageUri = destination, isExposureTransitioning = false) }
        preferencesManager.storageUri = destination
        statsManager.updateStorageUri(destination)
        reconcilePresetControls()
        if (!cameraRuntime.usesCameraHardware) {
            _uiState.update { it.copy(zoomState = it.zoomState.let { zoom ->
                zoom.copy(ratio = zoom.ratio.coerceIn(zoom.minRatio, zoom.maxRatio))
            }) }
        }
        triggerRebindForPresets()
    }

    private fun reconcilePresetControls() {
        val state = _uiState.value
        val caps = state.cameraCapabilities ?: return
        val adapted = ManualControlStateUpdater.adaptToCapabilities(state.manualControlsState, caps)
        val calculated = ManualControlStateUpdater.calculateNextState(
            ManualControlsState(), caps,
            ManualControlStateUpdater.ExposureDefaults(adapted.isoValue, adapted.ssValueNanos,
                adapted.postRawSensitivityBoost ?: 100)
        ) { adapted }.first
        val controls = if (calculated.isLowLightBoostEnabled &&
            ((if (calculated.isManualFps) calculated.fpsRange?.upper ?: state.selectedFps else state.selectedFps) > LOW_LIGHT_BOOST_MAX_FPS ||
                calculated.isManualIso || calculated.isManualSs)) {
            calculated.copy(isLowLightBoostEnabled = false)
        } else calculated
        _uiState.update { it.copy(manualControlsState = controls) }
    }

    private fun triggerRebindForPresets() {
        if (!cameraRuntime.usesCameraHardware) return
        if (_uiState.value.isSettingsSheetVisible) pendingHardRebind = true
        else scheduleCameraRebind(debounce = true)
    }

    private fun updateCamera2Settings(update: (Camera2Settings) -> Camera2Settings) {
        if (recordingManager.isRecording.value) return
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
     */
    fun onDisplayRotationChanged(rotation: Int) {
        if (rotation != lastDisplayRotation) controlCoordinator.cancelMetering()
        lastDisplayRotation = rotation
        if (!cameraRuntime.usesCameraHardware) return
        CameraRotationPolicy.targetRotation(rotation, _uiState.value.isRecording)
            ?.let(cameraManager::updateRotation)
    }

    private fun toggleRecording() {
        if (!cameraRuntime.usesCameraHardware) return
        if (_uiState.value.isRecording) {
            recordingManager.stopRecording()
        } else {
            if (!_uiState.value.isCameraReady || _uiState.value.isSettingsSheetVisible || pendingHardRebind) return
            val videoCapture = cameraManager.videoCapture ?: return
            val targetBitrate = (Bitrate.parse(_uiState.value.bitrate) ?: 0)
            val result = recordingManager.startRecording(
                videoCapture, RecordingDestination.fromStorageUri(_uiState.value.storageUri)
            )
            if (result !is RecordingManager.StartResult.Started) return
            val requestedFps = requestedFpsForStats()
            telemetryCoordinator.startFpsCalculation()
            statsManager.setRecordingState(true, requestedFps, targetBitrate, result.destination.toStorageUri())
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
        val (calculatedState, triggersRebind) = ManualControlStateUpdater.calculateNextState(
            oldControls,
            caps,
            exposureDefaults,
            updater
        )
        val newState = if (
            oldControls.isLowLightBoostEnabled &&
            calculatedState.isLowLightBoostEnabled &&
            oldControls.isManualFps &&
            !calculatedState.isManualFps &&
            currentUi.selectedFps > LOW_LIGHT_BOOST_MAX_FPS
        ) {
            calculatedState.copy(isLowLightBoostEnabled = false)
        } else {
            calculatedState
        }
        val lowLightBoostChanged =
            oldControls.isLowLightBoostEnabled != newState.isLowLightBoostEnabled

        if (!cameraRuntime.usesCameraHardware) {
            _uiState.update { it.copy(manualControlsState = newState) }
            return
        }

        if (
            caps != null &&
            exposureMode(oldControls, caps) != exposureMode(newState, caps)
        ) {
            beginExposureTransition(
                oldControls = oldControls,
                targetControls = newState,
                defaults = exposureDefaults,
                triggersRebind = triggersRebind,
                lowLightBoostChanged = lowLightBoostChanged
            )
            return
        }

        _uiState.update { it.copy(manualControlsState = newState) }

        if (triggersRebind && _uiState.value.isSettingsSheetVisible) pendingHardRebind = true
        else if (triggersRebind) scheduleCameraRebind(debounce = true)
        applyInterop(coalesce = true)
        if (lowLightBoostChanged) applyLowLightBoostPreference()
    }

    private fun beginExposureTransition(
        oldControls: ManualControlsState,
        targetControls: ManualControlsState,
        defaults: ManualControlStateUpdater.ExposureDefaults,
        triggersRebind: Boolean,
        lowLightBoostChanged: Boolean
    ) {
        if (_uiState.value.cameraCapabilities == null) return
        exposureTransitionJob?.cancel()
        val generation = ++exposureTransitionGeneration
        _uiState.update {
            it.copy(
                isExposureTransitioning = true
            )
        }

        exposureTransitionJob = viewModelScope.launch {
            try {
                val result = controlCoordinator.transition(
                    previous = oldControls.toCameraValues().copy(
                        isoValue = oldControls.isoValue ?: defaults.isoValue,
                        ssValueNanos = oldControls.ssValueNanos ?: defaults.shutterNanos,
                        postRawSensitivityBoost = oldControls.postRawSensitivityBoost ?: defaults.postRawSensitivityBoost
                    ),
                    target = desiredState(targetControls),
                    waitToLockAe = _uiState.value.isWaitToLockAeEnabled
                )
                if (generation != exposureTransitionGeneration) return@launch
                _uiState.update {
                    it.copy(
                        manualControlsState = it.manualControlsState.withCameraValues(result.controls),
                        isExposureTransitioning = false
                    )
                }
                if (triggersRebind && _uiState.value.isSettingsSheetVisible) pendingHardRebind = true
                else if (triggersRebind) scheduleCameraRebind(debounce = true)
                if (lowLightBoostChanged) applyLowLightBoostPreference()
            } catch (_: CameraRequestSupersededException) {
                if (generation == exposureTransitionGeneration) {
                    _uiState.update { it.copy(isExposureTransitioning = false) }
                }
            } catch (cancelled: CancellationException) {
                if (generation == exposureTransitionGeneration) _uiState.update { it.copy(isExposureTransitioning = false) }
                throw cancelled
            } catch (error: Exception) {
                reporter.report(IssueCode.CAMERA_CONTROL, "exposureTransition", error)
                if (generation == exposureTransitionGeneration) {
                    _uiState.update { it.copy(isExposureTransitioning = false) }
                }
            }
        }
    }

    private fun exposureMode(
        controls: ManualControlsState,
        caps: com.pilerks1.hdrrecorder.data.camera.CameraCapabilities
    ): ExposureMode = controlCoordinator.exposureMode(controls.toCameraValues(), caps)

    /** Attaches the current lifecycle and starts the initial bind without an arbitrary delay. */
    fun attachCamera(lifecycleOwner: LifecycleOwner, displayRotation: Int) {
        if (!cameraRuntime.usesCameraHardware) return
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
        if (!cameraRuntime.usesCameraHardware || !_uiState.value.arePresetsInitialized) return
        val lifecycleOwner = cameraLifecycleOwner ?: return
        cameraRebindJob?.cancel()
        exposureTransitionJob?.cancel()
        exposureTransitionGeneration++
        _uiState.update { it.copy(isExposureTransitioning = false) }

        val bind = {
            lowLightBoostApplyJob?.cancel()
            _uiState.update { it.copy(isCameraReady = false) }
            cameraManager.requestBindWhenReady(
                lifecycleOwner = lifecycleOwner,
                onSurfaceRequest = { request -> _surfaceRequest.value = request },
                uiState = _uiState.value,
                displayRotation = lastDisplayRotation,
                onCameraBound = { caps, previewOptions, previewSettings ->
                    _uiState.update {
                        it.copy(
                            cameraCapabilities = caps,
                            previewOptions = previewOptions,
                            previewSettings = previewSettings,
                            camera2Settings = it.camera2Settings.reconcile(caps.camera2Modes),
                            manualControlsState = ManualControlStateUpdater.adaptToCapabilities(
                                it.manualControlsState,
                                caps
                            ),
                            isCameraReady = true
                        )
                    }
                    reconcilePresetControls()
                    applyInterop()
                    applyLowLightBoostPreference()
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

    private fun setLowLightBoostRequested(enabled: Boolean) {
        val current = _uiState.value
        if (enabled && current.cameraCapabilities?.supportsLowLightBoost != true) return

        val controls = current.manualControlsState
        val caps = current.cameraCapabilities
        if (
            enabled &&
            caps != null &&
            exposureMode(controls, caps) != ExposureMode.AUTO
        ) {
            updateManualControls {
                it.copy(
                    isManualIso = false,
                    isoValue = null,
                    isManualPostRawSensitivity = false,
                    postRawSensitivityBoost = null,
                    isManualSs = false,
                    ssValueNanos = null
                )
            }
            viewModelScope.launch {
                exposureTransitionJob?.join()
                if (
                    !_uiState.value.isExposureTransitioning &&
                    _uiState.value.manualControlsState.isLowLightBoostEnabled.not()
                ) {
                    setLowLightBoostRequested(true)
                }
            }
            return
        }

        val effectiveUpperFps = if (controls.isManualFps) {
            controls.fpsRange?.upper ?: current.selectedFps
        } else {
            current.selectedFps
        }
        if (
            enabled &&
            current.isRecording &&
            effectiveUpperFps > LOW_LIGHT_BOOST_MAX_FPS
        ) return

        val adjustedRange = if (
            enabled &&
            controls.isManualFps &&
            (controls.fpsRange?.upper ?: 0) > LOW_LIGHT_BOOST_MAX_FPS
        ) {
            clampManualFpsForLowLightBoost(controls.fpsRange, current.cameraCapabilities)
        } else {
            controls.fpsRange
        }
        val needsHardRebind = enabled &&
            !controls.isManualFps &&
            current.selectedFps > LOW_LIGHT_BOOST_MAX_FPS

        _uiState.update { state ->
            state.copy(
                selectedFps = if (needsHardRebind) LOW_LIGHT_BOOST_MAX_FPS else state.selectedFps,
                manualControlsState = state.manualControlsState.copy(
                    isLowLightBoostEnabled = enabled,
                    isManualIso = if (enabled) false else state.manualControlsState.isManualIso,
                    isoValue = if (enabled) null else state.manualControlsState.isoValue,
                    isManualPostRawSensitivity = if (enabled) {
                        false
                    } else {
                        state.manualControlsState.isManualPostRawSensitivity
                    },
                    postRawSensitivityBoost = if (enabled) {
                        null
                    } else {
                        state.manualControlsState.postRawSensitivityBoost
                    },
                    isManualSs = if (enabled) false else state.manualControlsState.isManualSs,
                    ssValueNanos = if (enabled) null else state.manualControlsState.ssValueNanos,
                    fpsRange = adjustedRange
                )
            )
        }

        applyInterop()
        if (needsHardRebind) {
            scheduleCameraRebind(debounce = true)
        } else {
            applyLowLightBoostPreference()
        }
    }

    private fun clampManualFpsForLowLightBoost(
        currentRange: Range<Int>?,
        capabilities: com.pilerks1.hdrrecorder.data.camera.CameraCapabilities?
    ): Range<Int> {
        val oldRange = currentRange ?: Range(
            LOW_LIGHT_BOOST_MAX_FPS,
            LOW_LIGHT_BOOST_MAX_FPS
        )
        val compatibleRanges = capabilities?.fpsRanges
            ?.filter { it.upper <= LOW_LIGHT_BOOST_MAX_FPS }
            .orEmpty()
        if (compatibleRanges.isEmpty()) {
            return Range(LOW_LIGHT_BOOST_MAX_FPS, LOW_LIGHT_BOOST_MAX_FPS)
        }

        val snapped = FpsRangeMath.clampToSupportedRange(
            newRange = oldRange.lower.toFloat()..LOW_LIGHT_BOOST_MAX_FPS.toFloat(),
            oldRange = oldRange.lower.toFloat()..oldRange.upper.toFloat(),
            draggedBound = FpsRangeBound.UPPER,
            dragDirection = -1f,
            validRanges = compatibleRanges
        )
        return Range(snapped.start.roundToInt(), snapped.endInclusive.roundToInt())
    }

    private fun applyLowLightBoostPreference() {
        if (!cameraRuntime.usesCameraHardware) return
        if (_uiState.value.cameraCapabilities?.supportsLowLightBoost != true) return
        val desired = _uiState.value.manualControlsState.isLowLightBoostEnabled
        lowLightBoostApplyJob?.cancel()
        lowLightBoostApplyJob = viewModelScope.launch {
            try {
                controlCoordinator.setLowLightBoost(desired)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (_uiState.value.manualControlsState.isLowLightBoostEnabled == desired) {
                    _uiState.update {
                        it.copy(
                            manualControlsState = it.manualControlsState.copy(
                                isLowLightBoostEnabled = false
                            )
                        )
                    }
                }
                reporter.report(IssueCode.CAMERA_CONTROL, "lowLightBoost", error)
            }
        }
    }

    private fun desiredState(controls: ManualControlsState = _uiState.value.manualControlsState): DesiredCameraState {
        val state = _uiState.value
        return DesiredCameraState(
            controls = controls.toCameraValues(),
            settings = InteropSettings(
                gamma = state.gammaSettings.takeIf { state.encoding.supportsGammaCurveSelection },
                camera2 = state.camera2Settings
            ),
            capabilities = state.cameraCapabilities,
            selectedFps = state.selectedFps
        )
    }

    private fun applyInterop(coalesce: Boolean = false) {
        if (cameraRuntime.usesCameraHardware) controlCoordinator.update(desiredState(), coalesce)
    }

    private enum class RecordingSetting { CODEC, BIT_DEPTH, ENCODING }

    private var recordingPriority = listOf(RecordingSetting.CODEC, RecordingSetting.BIT_DEPTH, RecordingSetting.ENCODING)

    private fun selectVideoConfiguration(touched: RecordingSetting, requested: CameraUiState) {
        if (recordingManager.isRecording.value) return
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
                }
            }
        }
    }

    private fun collectLowLightBoostState() {
        viewModelScope.launch {
            cameraManager.isLowLightBoostActive.collect { active ->
                _uiState.update { it.copy(isLowLightBoostActive = active) }
            }
        }
    }

    private fun collectZoomState() {
        if (!cameraRuntime.usesCameraHardware) return
        viewModelScope.launch {
            cameraManager.zoomState.collect { zoom ->
                if (zoom.ratio != _uiState.value.zoomState.ratio) controlCoordinator.onZoomChanged()
                _uiState.update { it.copy(zoomState = zoom, isCameraReady = zoom.isReady) }
            }
        }
    }

    override fun onCleared() {
        reporter.contextProvider = null
        cameraRebindJob?.cancel()
        lowLightBoostApplyJob?.cancel()
        exposureTransitionJob?.cancel()
        cameraManager.release()
        controlCoordinator.close()
        telemetryCoordinator.cleanup()
        statsManager.cleanup()
    }

    private companion object {
        const val CAMERA_REBIND_DEBOUNCE_MS = 300L
        const val LOW_LIGHT_BOOST_MAX_FPS = 30

    }

}
