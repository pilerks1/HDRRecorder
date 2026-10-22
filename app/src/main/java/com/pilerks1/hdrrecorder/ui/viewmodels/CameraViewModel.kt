package com.pilerks1.hdrrecorder.ui.viewmodels

import android.app.Application
import android.content.Intent
import android.util.Log
import android.util.Range
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
import com.pilerks1.hdrrecorder.data.camera.CameraControlCoordinator
import com.pilerks1.hdrrecorder.data.camera.DesiredCameraState
import com.pilerks1.hdrrecorder.data.camera.ExposureMode
import com.pilerks1.hdrrecorder.data.camera.core.CameraControlValues
import com.pilerks1.hdrrecorder.data.camera.core.CameraRequestSupersededException
import com.pilerks1.hdrrecorder.data.camera.CameraRuntime
import com.pilerks1.hdrrecorder.data.camera.CameraRuntimeEventResult
import com.pilerks1.hdrrecorder.data.camera.core.InteropSettings
import com.pilerks1.hdrrecorder.data.camera.TelemetryCoordinator
import com.pilerks1.hdrrecorder.model.GammaCurve
import com.pilerks1.hdrrecorder.ui.CameraUiEvent
import com.pilerks1.hdrrecorder.ui.CameraUiState
import com.pilerks1.hdrrecorder.ui.ManualControl
import com.pilerks1.hdrrecorder.ui.ManualControlsState
import com.pilerks1.hdrrecorder.ui.manualcontrols.FpsRangeBound
import com.pilerks1.hdrrecorder.ui.manualcontrols.FpsRangeMath
import com.pilerks1.hdrrecorder.ui.resetForTapToMeter
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
 * ViewModel no longer news up its own Android dependencies. Application is retained only
 * for the content resolver used when persisting a storage URI permission.
 */
internal class CameraViewModel(
    application: Application,
    private val statsManager: StatsManager,
    private val telemetryCoordinator: TelemetryCoordinator,
    private val preferencesManager: PreferencesManager,
    private val cameraManager: CameraManager,
    private val recordingManager: RecordingManager,
    private val controlCoordinator: CameraControlCoordinator,
    private val cameraRuntime: CameraRuntime
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

    init {
        val initialState = cameraRuntime.initialUiState()
        if (initialState != null) {
            _uiState.value = initialState
        } else {
            val globalStorageUri = preferencesManager.storageUri
            if (!preferencesManager.hasPreset("Default")) {
                preferencesManager.savePreset("Default", _uiState.value)
            }

            val names = preferencesManager.getPresetNames().toList()
            val currentPreset = if (names.contains(preferencesManager.currentPresetName)) {
                preferencesManager.currentPresetName
            } else {
                "Default"
            }
            val loadedState = preferencesManager.loadPreset(currentPreset, _uiState.value)

            _uiState.update {
                loadedState.copy(presetsList = names, storageUri = globalStorageUri)
            }
        }

        collectRecordingState()
        collectLowLightBoostState()
        collectControlErrors()
        statsManager.setRecordingState(false, requestedFpsForStats(), Bitrate.parseOrDefault(_uiState.value.bitrate), _uiState.value.storageUri)
    }

    /**
     * Helper to automatically apply state whenever a setting changes.
     * Defer camera rebinds if the Settings UI is currently open.
     */
    private fun updateSettingsAndSave(requiresHardRebind: Boolean = false, update: (CameraUiState) -> CameraUiState) {
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
        when (val runtimeResult = cameraRuntime.handleEvent(event, _uiState.value)) {
            CameraRuntimeEventResult.NotHandled -> Unit
            is CameraRuntimeEventResult.Handled -> {
                runtimeResult.uiState?.let { _uiState.value = it }
                return
            }
        }

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
                var disabledLowLightBoost = false
                updateSettingsAndSave(requiresHardRebind = true) { state ->
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
                updateSettingsAndSave(requiresHardRebind = true) { state ->
                    state.copy(selectedResolution = state.selectedResolution.next())
                }
            }
            is CameraUiEvent.CycleAspectRatio -> {
                updateSettingsAndSave(requiresHardRebind = true) { state ->
                    state.copy(selectedAspectRatio = state.selectedAspectRatio.next())
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
            is CameraUiEvent.SetLowLightBoost -> setLowLightBoostRequested(event.enabled)

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
                updateManualControls { it.resetForTapToMeter() }
                viewModelScope.launch {
                    exposureTransitionJob?.join()
                    controlCoordinator.tapToMeter(event.meteringPoint)
                }
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
        if (!cameraRuntime.usesCameraHardware) return
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
        if (!cameraRuntime.usesCameraHardware) return
        CameraRotationPolicy.targetRotation(rotation, _uiState.value.isRecording)
            ?.let(cameraManager::updateRotation)
    }

    private fun toggleRecording() {
        if (!cameraRuntime.usesCameraHardware) return
        if (_uiState.value.isRecording) {
            recordingManager.stopRecording()
        } else {
            val videoCapture = cameraManager.videoCapture ?: return
            val targetBitrate = Bitrate.parseOrDefault(_uiState.value.bitrate)
            if (!recordingManager.startRecording(videoCapture, _uiState.value.storageUri)) return
            val requestedFps = requestedFpsForStats()
            telemetryCoordinator.startFpsCalculation()
            statsManager.setRecordingState(true, requestedFps, targetBitrate, _uiState.value.storageUri)
        }
    }

    private fun togglePause() {
        if (!cameraRuntime.usesCameraHardware) return
        if (_uiState.value.isPaused) {
            recordingManager.resumeRecording()
        } else {
            recordingManager.pauseRecording()
        }
        _uiState.update { it.copy(isPaused = !it.isPaused) }
    }

    fun updateManualControls(updater: (ManualControlsState) -> ManualControlsState) {
        val currentUi = _uiState.value
        if (currentUi.isExposureTransitioning) return
        val caps = currentUi.cameraCapabilities
        val telemetry = telemetryCoordinator.currentCameraTelemetry()
        val oldControls = currentUi.manualControlsState
        val exposureDefaults = ManualControlStateUpdater.ExposureDefaults(
            isoValue = telemetry.iso,
            shutterNanos = telemetry.shutterNanos,
            postRawSensitivityBoost = telemetry.postRawSensitivityBoost
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
                isExposureTransitioning = true,
                exposureTransitionMessage = null
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
                    target = desiredState(targetControls)
                )
                if (generation != exposureTransitionGeneration) return@launch
                _uiState.update {
                    it.copy(
                        manualControlsState = it.manualControlsState.withCameraValues(result.controls),
                        isExposureTransitioning = false,
                        exposureTransitionMessage = result.message
                    )
                }
                if (result.usedUnlockedHandoff) Log.d("CameraViewModel", "Exposure handoff used an unlocked camera result")
                if (triggersRebind && _uiState.value.isSettingsSheetVisible) pendingHardRebind = true
                else if (triggersRebind) scheduleCameraRebind(debounce = true)
                if (lowLightBoostChanged) applyLowLightBoostPreference()
                if (result.message != null) {
                    delay(EXPOSURE_MESSAGE_DURATION_MS)
                    if (generation == exposureTransitionGeneration) {
                        _uiState.update { it.copy(exposureTransitionMessage = null) }
                    }
                }
            } catch (_: CameraRequestSupersededException) {
                if (generation == exposureTransitionGeneration) {
                    _uiState.update { it.copy(isExposureTransitioning = false) }
                }
            } catch (cancelled: CancellationException) {
                if (generation == exposureTransitionGeneration) _uiState.update { it.copy(isExposureTransitioning = false) }
                throw cancelled
            } catch (error: Exception) {
                Log.w("CameraViewModel", "Exposure transition failed", error)
                if (generation == exposureTransitionGeneration) {
                    _uiState.update { it.copy(isExposureTransitioning = false, exposureTransitionMessage = error.message) }
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
        if (!cameraRuntime.usesCameraHardware) return
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
                onCameraBound = { caps ->
                    _uiState.update {
                        it.copy(
                            cameraCapabilities = caps,
                            manualControlsState = ManualControlStateUpdater.adaptToCapabilities(
                                it.manualControlsState,
                                caps
                            ),
                            isCameraReady = true
                        )
                    }
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
                Log.w("CameraViewModel", "Unable to set low-light boost to $desired", error)
            }
        }
    }

    private fun desiredState(controls: ManualControlsState = _uiState.value.manualControlsState): DesiredCameraState {
        val state = _uiState.value
        return DesiredCameraState(
            controls = controls.toCameraValues(),
            settings = InteropSettings(state.gammaCurve, state.isNoiseReductionEnabled),
            capabilities = state.cameraCapabilities,
            selectedFps = state.selectedFps
        )
    }

    private fun applyInterop(coalesce: Boolean = false) {
        if (cameraRuntime.usesCameraHardware) controlCoordinator.update(desiredState(), coalesce)
    }

    fun finishManualControlInteraction() = applyInterop()

    private fun collectControlErrors() {
        viewModelScope.launch {
            controlCoordinator.error.collect { error ->
                if (error != null) {
                    Log.w("CameraViewModel", error)
                    _uiState.update { it.copy(exposureTransitionMessage = error) }
                }
            }
        }
    }

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
                        targetBitrate = Bitrate.parseOrDefault(state.bitrate),
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

    override fun onCleared() {
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
        const val EXPOSURE_MESSAGE_DURATION_MS = 3_000L
    }

}
