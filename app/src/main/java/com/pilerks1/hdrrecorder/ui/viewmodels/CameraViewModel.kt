package com.pilerks1.hdrrecorder.ui.viewmodels

import android.app.Application
import android.content.Intent
import android.hardware.camera2.CameraMetadata
import android.os.SystemClock
import android.util.Log
import android.util.Range
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.CameraControl
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
import com.pilerks1.hdrrecorder.data.camera.CameraRuntime
import com.pilerks1.hdrrecorder.data.camera.CameraRuntimeEventResult
import com.pilerks1.hdrrecorder.data.camera.InteropSettings
import com.pilerks1.hdrrecorder.data.camera.SliderTelemetryField
import com.pilerks1.hdrrecorder.data.camera.TelemetryCoordinator
import com.pilerks1.hdrrecorder.data.camera.TelemetryDemand
import com.pilerks1.hdrrecorder.model.GammaCurve
import com.pilerks1.hdrrecorder.model.ExposureCaptureResult
import com.pilerks1.hdrrecorder.ui.CameraUiEvent
import com.pilerks1.hdrrecorder.ui.CameraUiState
import com.pilerks1.hdrrecorder.ui.ManualControl
import com.pilerks1.hdrrecorder.ui.ManualControlsState
import com.pilerks1.hdrrecorder.ui.manualcontrols.FpsRangeBound
import com.pilerks1.hdrrecorder.ui.manualcontrols.FpsRangeMath
import com.pilerks1.hdrrecorder.ui.resetForTapToMeter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Managers are constructor-injected (via CameraViewModelFactory in MainActivity) so the
 * ViewModel no longer news up its own Android dependencies. Application is retained only
 * for the content resolver used when persisting a storage URI permission.
 */
@ExperimentalCamera2Interop
internal class CameraViewModel(
    application: Application,
    private val statsManager: StatsManager,
    private val telemetryCoordinator: TelemetryCoordinator,
    private val preferencesManager: PreferencesManager,
    private val cameraManager: CameraManager,
    private val recordingManager: RecordingManager,
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
    private val desiredCameraState = MutableStateFlow<DesiredCameraState?>(null)
    private val pendingImmediateInteropWrite = AtomicBoolean(false)
    private val interopWriteSignal = Channel<Unit>(Channel.CONFLATED)
    private val interopWriteMutex = Mutex()
    private var interopWriterJob: Job? = null
    private var appliedInteropControl: CameraControl? = null
    private var appliedCameraState: DesiredCameraState? = null
    private var appliedExposureCompensationIndex: Int? = null
    private var lastInteropSubmissionElapsedMs = 0L

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
        collectTelemetryDemand()
        startInteropWriter()
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
                    interopWriteMutex.withLock {
                        cameraManager.tapToMeter(event.meteringPoint)?.await()
                    }
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
        val caps = _uiState.value.cameraCapabilities ?: return
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
                val oldMode = exposureMode(oldControls, caps)
                val targetMode = exposureMode(targetControls, caps)
                var resolvedTarget = targetControls

                if (oldMode == ExposureMode.FULL_MANUAL) {
                    if (oldControls.isManualFps) {
                        val lastManualFps = oldControls.fpsRange?.upper
                        resolvedTarget = resolvedTarget.copy(
                            fpsRange = lastManualFps?.let { Range(it, it) }
                                ?: resolvedTarget.fpsRange
                        )
                    }
                    val locked = lockAndAwait(resolvedTarget)
                    if (targetMode == ExposureMode.SENSITIVITY_PRIORITY) {
                        resolvedTarget = resolvedTarget.copy(
                            isoValue = requestedOrLocked(
                                requested = targetControls.isoValue,
                                previousAutomatic = defaults.isoValue,
                                locked = locked.sensorSensitivityIso
                            )
                        )
                    } else if (targetMode == ExposureMode.SHUTTER_PRIORITY) {
                        resolvedTarget = resolvedTarget.copy(
                            ssValueNanos = requestedOrLocked(
                                requested = targetControls.ssValueNanos,
                                previousAutomatic = defaults.shutterNanos,
                                locked = locked.shutterNanos
                            )
                        )
                    }
                } else {
                    val locked = lockAndAwait(oldControls)
                    resolvedTarget = when (targetMode) {
                        ExposureMode.FULL_MANUAL -> {
                            val resolvedShutterNanos = requestedOrLocked(
                                requested = targetControls.ssValueNanos,
                                previousAutomatic = defaults.shutterNanos,
                                locked = locked.shutterNanos
                            )
                            targetControls.copy(
                                isoValue = requestedOrLocked(
                                    requested = targetControls.isoValue,
                                    previousAutomatic = defaults.isoValue,
                                    locked = locked.sensorSensitivityIso
                                ),
                                ssValueNanos = resolvedShutterNanos,
                                isManualPostRawSensitivity =
                                    caps.supportsPostRawSensitivityBoost,
                                postRawSensitivityBoost =
                                    if (caps.supportsPostRawSensitivityBoost) {
                                        requestedOrLocked(
                                            requested =
                                                targetControls.postRawSensitivityBoost,
                                            previousAutomatic =
                                                defaults.postRawSensitivityBoost,
                                            locked = locked.postRawSensitivityBoost
                                        )
                                    } else {
                                        null
                                    },
                                fpsRange = manualFpsRangeForShutter(
                                    controls = targetControls,
                                    shutterNanos = resolvedShutterNanos,
                                    caps = caps
                                )
                            )
                        }
                        ExposureMode.SENSITIVITY_PRIORITY -> targetControls.copy(
                            isoValue = requestedOrLocked(
                                requested = targetControls.isoValue,
                                previousAutomatic = defaults.isoValue,
                                locked = locked.sensorSensitivityIso
                            )
                        )
                        ExposureMode.SHUTTER_PRIORITY -> targetControls.copy(
                            ssValueNanos = requestedOrLocked(
                                requested = targetControls.ssValueNanos,
                                previousAutomatic = defaults.shutterNanos,
                                locked = locked.shutterNanos
                            )
                        )
                        ExposureMode.AUTO -> targetControls
                    }
                }

                if (generation != exposureTransitionGeneration) return@launch
                submitTransitionRequest(resolvedTarget, aeLocked = false)
                _uiState.update {
                    it.copy(
                        manualControlsState = resolvedTarget,
                        isExposureTransitioning = false
                    )
                }
                appliedCameraState = null

                if (triggersRebind && _uiState.value.isSettingsSheetVisible) {
                    pendingHardRebind = true
                } else if (triggersRebind) {
                    scheduleCameraRebind(debounce = true)
                }
                if (lowLightBoostChanged) applyLowLightBoostPreference()
            } catch (cancelled: CancellationException) {
                if (generation == exposureTransitionGeneration) {
                    _uiState.update { it.copy(isExposureTransitioning = false) }
                    appliedCameraState = null
                    applyInterop()
                }
                throw cancelled
            } catch (error: Exception) {
                Log.w("CameraViewModel", "Exposure transition failed", error)
                restoreAutoAfterTransitionFailure(generation)
            }
        }
    }

    private suspend fun lockAndAwait(
        controls: ManualControlsState
    ): ExposureCaptureResult {
        val caps = _uiState.value.cameraCapabilities
        if (caps?.supportsAeLock != true) {
            return telemetryCoordinator.currentExposureResult()
                ?: throw IllegalStateException("No exposure result available")
        }

        telemetryCoordinator.setExposureAcknowledgementDemand(true)
        return try {
            withTimeout(EXPOSURE_TRANSITION_TIMEOUT_MS) {
                val startingFrame =
                    telemetryCoordinator.currentExposureResult()?.frameNumber ?: -1L
                submitTransitionRequest(controls, aeLocked = true)
                telemetryCoordinator.exposureAcknowledgements
                    .filterNotNull()
                    .first { result ->
                        result.frameNumber > startingFrame &&
                            result.aeState == CameraMetadata.CONTROL_AE_STATE_LOCKED &&
                            result.aeLocked != false
                    }
            }
        } finally {
            telemetryCoordinator.setExposureAcknowledgementDemand(false)
        }
    }

    private suspend fun submitTransitionRequest(
        controls: ManualControlsState,
        aeLocked: Boolean
    ) {
        val cameraControl = cameraManager.getCameraControl()
            ?: throw IllegalStateException("Camera control unavailable")
        val state = DesiredCameraState(
            controls = controls,
            settings = currentInteropSettings(),
            capabilities = _uiState.value.cameraCapabilities
        )
        interopWriteMutex.withLock {
            withTimeout(EXPOSURE_TRANSITION_TIMEOUT_MS) {
                CameraInteropApplier.apply(
                    cameraControl = cameraControl,
                    state = controls,
                    settings = state.settings,
                    capabilities = state.capabilities,
                    applyExposureCompensation = !aeLocked,
                    aeLockOverride = aeLocked
                )
            }
            lastInteropSubmissionElapsedMs = SystemClock.elapsedRealtime()
            if (!aeLocked && cameraManager.getCameraControl() === cameraControl) {
                desiredCameraState.value = state
                appliedInteropControl = cameraControl
                appliedCameraState = state
                appliedExposureCompensationIndex =
                    CameraInteropApplier.exposureCompensationIndex(
                        state.controls,
                        state.capabilities
                    )
            }
        }
    }

    private fun manualFpsRangeForShutter(
        controls: ManualControlsState,
        shutterNanos: Long?,
        caps: com.pilerks1.hdrrecorder.data.camera.CameraCapabilities
    ): Range<Int>? {
        if (!controls.isManualFps) return controls.fpsRange
        val fallback = controls.fpsRange?.upper ?: _uiState.value.selectedFps
        val legalFps = shutterNanos
            ?.let { selectedShutter ->
                FpsRangeMath.nearestFixedFrameRateForShutter(
                    shutterNanos = selectedShutter,
                    validRanges = caps.fpsRanges
                )
            }
            ?: FpsRangeMath.nearestFixedFrameRate(
                requestedFps = fallback,
                validRanges = caps.fpsRanges
            )
            ?: fallback
        return Range(legalFps, legalFps)
    }

    private fun <T> requestedOrLocked(
        requested: T?,
        previousAutomatic: T?,
        locked: T?
    ): T? = if (requested != null && requested != previousAutomatic) requested else locked ?: requested

    private fun restoreAutoAfterTransitionFailure(generation: Long) {
        if (generation != exposureTransitionGeneration) return
        val current = _uiState.value.manualControlsState
        val automatic = current.copy(
            isManualIso = false,
            isoValue = null,
            isManualPostRawSensitivity = false,
            postRawSensitivityBoost = null,
            isManualSs = false,
            ssValueNanos = null
        )
        _uiState.update {
            it.copy(
                manualControlsState = automatic,
                isExposureTransitioning = false,
                exposureTransitionMessage = "Exposure transition failed; Auto restored"
            )
        }
        appliedCameraState = null
        applyInterop()
        viewModelScope.launch {
            delay(EXPOSURE_MESSAGE_DURATION_MS)
            if (generation == exposureTransitionGeneration) {
                _uiState.update { it.copy(exposureTransitionMessage = null) }
            }
        }
    }

    private fun exposureMode(
        controls: ManualControlsState,
        caps: com.pilerks1.hdrrecorder.data.camera.CameraCapabilities
    ): ExposureMode = when {
        controls.isManualIso &&
            controls.isManualSs &&
            caps.supportsFullManualExposure -> ExposureMode.FULL_MANUAL
        controls.isManualIso &&
            caps.supportsIsoPriorityAe -> ExposureMode.SENSITIVITY_PRIORITY
        controls.isManualSs &&
            caps.supportsShutterPriorityAe -> ExposureMode.SHUTTER_PRIORITY
        else -> ExposureMode.AUTO
    }

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
                onCameraBound = { control, caps ->
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
                val wasApplied = interopWriteMutex.withLock {
                    val future = cameraManager.setLowLightBoostEnabled(desired)
                        ?: return@withLock false
                    future.await()
                    true
                }
                if (!wasApplied) return@launch
                // Re-submit interop after CameraX changes AE so manual controls retain priority
                // when they were the action that disabled low-light boost.
                appliedCameraState = null
                applyInterop()
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

    /**
     * Single entry point for all Camera2 interop writes. Builds one request from the current
     * manual control state + non-manual interop settings and queues the newest complete state.
     */
    private fun applyInterop(coalesce: Boolean = false) {
        if (!cameraRuntime.usesCameraHardware) return
        val state = _uiState.value
        val desired = DesiredCameraState(
            controls = state.manualControlsState,
            settings = currentInteropSettings(),
            capabilities = state.cameraCapabilities
        )
        desiredCameraState.value = desired
        if (!coalesce) {
            pendingImmediateInteropWrite.set(true)
        }
        interopWriteSignal.trySend(Unit)
    }

    fun finishManualControlInteraction() {
        applyInterop(coalesce = false)
    }

    private fun startInteropWriter() {
        interopWriterJob = viewModelScope.launch {
            for (ignored in interopWriteSignal) {
                val immediate = pendingImmediateInteropWrite.getAndSet(false)
                if (!immediate) {
                    val remainingDelay = (
                        lastInteropSubmissionElapsedMs +
                            INTEROP_DRAG_SAMPLE_MS -
                            SystemClock.elapsedRealtime()
                        ).coerceAtLeast(0L)
                    delay(remainingDelay)
                }

                val desired = desiredCameraState.value ?: continue
                try {
                    submitInteropState(desired)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w("CameraViewModel", "Unable to apply Camera2 state", error)
                }
            }
        }
    }

    private suspend fun submitInteropState(desired: DesiredCameraState) {
        if (_uiState.value.isExposureTransitioning) return
        val cameraControl = cameraManager.getCameraControl() ?: return
        interopWriteMutex.withLock {
            if (_uiState.value.isExposureTransitioning) return
            if (
                appliedInteropControl === cameraControl &&
                appliedCameraState == desired
            ) return

            val exposureCompensationIndex =
                CameraInteropApplier.exposureCompensationIndex(
                    desired.controls,
                    desired.capabilities
                )
            val shouldApplyExposureCompensation =
                appliedInteropControl !== cameraControl ||
                    appliedExposureCompensationIndex != exposureCompensationIndex
            lastInteropSubmissionElapsedMs = SystemClock.elapsedRealtime()
            CameraInteropApplier.apply(
                cameraControl = cameraControl,
                state = desired.controls,
                settings = desired.settings,
                capabilities = desired.capabilities,
                applyExposureCompensation = shouldApplyExposureCompensation
            )

            if (cameraManager.getCameraControl() !== cameraControl) return
            appliedInteropControl = cameraControl
            appliedCameraState = desired
            appliedExposureCompensationIndex = exposureCompensationIndex
        }
    }

    private fun currentInteropSettings(): InteropSettings {
        val state = _uiState.value
        return InteropSettings(
            gammaCurve = state.gammaCurve,
            isNoiseReductionEnabled = state.isNoiseReductionEnabled
        )
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

    private fun collectTelemetryDemand() {
        viewModelScope.launch {
            uiState
                .map(::telemetryDemandFor)
                .distinctUntilChanged()
                .collect(telemetryCoordinator::updateDemand)
        }
    }

    private fun telemetryDemandFor(state: CameraUiState): TelemetryDemand {
        val controls = state.manualControlsState
        val field = when (controls.activePanel?.manualControl) {
            ManualControl.ISO -> if (
                !controls.isManualIso ||
                (
                    state.cameraCapabilities?.supportsPostRawSensitivityBoost == true &&
                        !controls.isManualPostRawSensitivity
                    )
            ) SliderTelemetryField.ISO else null
            ManualControl.SHUTTER ->
                if (!controls.isManualSs) SliderTelemetryField.SHUTTER else null
            ManualControl.FOCUS ->
                if (!controls.isManualFocus) SliderTelemetryField.FOCUS else null
            ManualControl.WHITE_BALANCE,
            ManualControl.TINT ->
                if (!controls.isManualWb) SliderTelemetryField.WHITE_BALANCE else null
            ManualControl.EXPOSURE_COMPENSATION -> null
            null -> null
        }
        return TelemetryDemand(
            statsVisible = true,
            automaticSliderField = field
        )
    }

    override fun onCleared() {
        cameraRebindJob?.cancel()
        interopWriterJob?.cancel()
        interopWriteSignal.close()
        lowLightBoostApplyJob?.cancel()
        exposureTransitionJob?.cancel()
        cameraManager.release()
        telemetryCoordinator.cleanup()
        statsManager.cleanup()
    }

    private companion object {
        const val CAMERA_REBIND_DEBOUNCE_MS = 300L
        const val INTEROP_DRAG_SAMPLE_MS = 33L
        const val LOW_LIGHT_BOOST_MAX_FPS = 30
        const val EXPOSURE_TRANSITION_TIMEOUT_MS = 2_000L
        const val EXPOSURE_MESSAGE_DURATION_MS = 3_000L
    }

    private data class DesiredCameraState(
        val controls: ManualControlsState,
        val settings: InteropSettings,
        val capabilities: com.pilerks1.hdrrecorder.data.camera.CameraCapabilities?
    )

    private enum class ExposureMode {
        AUTO,
        SHUTTER_PRIORITY,
        SENSITIVITY_PRIORITY,
        FULL_MANUAL
    }
}
