package com.pilerks1.hdrrecorder.ui.camera.state

import com.pilerks1.hdrrecorder.camera.camera2.CameraRequestSupersededException
import com.pilerks1.hdrrecorder.camera.camera2.InteropSettings
import com.pilerks1.hdrrecorder.camera.camerax.CameraControlCoordinator
import com.pilerks1.hdrrecorder.camera.camerax.DesiredCameraState
import com.pilerks1.hdrrecorder.camera.model.ExposureMode
import com.pilerks1.hdrrecorder.data.diagnostics.ErrorReporter
import com.pilerks1.hdrrecorder.data.diagnostics.IssueCode
import com.pilerks1.hdrrecorder.data.telemetry.TelemetryCoordinator
import com.pilerks1.hdrrecorder.ui.camera.CameraRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Applies manual controls and owns the screen exposure-transition job. */
internal class ManualControlsController(
    private val uiState: MutableStateFlow<CameraUiState>,
    private val scope: CoroutineScope,
    private val telemetryCoordinator: TelemetryCoordinator,
    private val controlCoordinator: CameraControlCoordinator,
    private val cameraRuntime: CameraRuntime,
    private val reporter: ErrorReporter,
    private val requestRebind: () -> Unit
) {
    private var exposureTransitionJob: Job? = null
    private var exposureTransitionGeneration = 0L

    /** Supersede a handoff without changing UI state before its caller applies a new snapshot. */
    fun cancelTransition() {
        exposureTransitionJob?.cancel()
        exposureTransitionGeneration++
    }

    fun normalizeManualControls() {
        val caps = uiState.value.cameraCapabilities ?: return
        uiState.update {
            it.copy(manualControlsState = ManualControlStateUpdater.normalize(it.manualControlsState, caps, it.selectedFps, it.previewSettings.frameRate))
        }
    }

    fun updateManualControls(updater: (ManualControlsState) -> ManualControlsState) {
        val currentUi = uiState.value
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
            uiState.update { it.copy(manualControlsState = newState) }
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

        uiState.update { it.copy(manualControlsState = newState) }

        if (triggersRebind) requestRebind()
        applyCameraControls(coalesce = true)
    }

    private fun beginExposureTransition(
        oldControls: ManualControlsState,
        targetControls: ManualControlsState,
        defaults: ManualControlStateUpdater.ExposureDefaults,
        triggersRebind: Boolean
    ) {
        exposureTransitionJob?.cancel()
        val generation = ++exposureTransitionGeneration
        uiState.update {
            it.copy(
                manualControlsState = it.manualControlsState.copy(isLowLightBoostEnabled = targetControls.isLowLightBoostEnabled),
                isExposureTransitioning = true
            )
        }

        exposureTransitionJob = scope.launch {
            var resolved: ManualControlsState? = null
            try {
                resolved = controlCoordinator.transition(
                    previous = oldControls.copy(
                        isoValue = oldControls.isoValue ?: defaults.isoValue,
                        ssValueNanos = oldControls.ssValueNanos ?: defaults.shutterNanos,
                        postRawSensitivityBoost = oldControls.postRawSensitivityBoost ?: defaults.postRawSensitivityBoost
                    ),
                    target = buildDesiredCameraState(targetControls),
                    waitToLockAe = uiState.value.isWaitToLockAeEnabled
                )
            } catch (_: CameraRequestSupersededException) {
                // A rebind or newer transition owns the camera now.
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                reporter.report(IssueCode.CAMERA_CONTROL, "exposureTransition", error)
            } finally {
                if (generation == exposureTransitionGeneration) {
                    uiState.update {
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

    fun setLowLightBoostRequested(enabled: Boolean) {
        val current = uiState.value
        if (enabled && (!current.isLowLightBoostEligible || !current.isCameraReady || current.isExposureTransitioning)) return
        if (current.manualControlsState.isLowLightBoostEnabled == enabled) return
        uiState.update { it.copy(manualControlsState = it.manualControlsState.copy(isLowLightBoostEnabled = enabled)) }
        applyCameraControls()
    }

    private fun buildDesiredCameraState(controls: ManualControlsState = uiState.value.manualControlsState): DesiredCameraState {
        val state = uiState.value
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

    fun applyCameraControls(coalesce: Boolean = false) {
        if (cameraRuntime.usesCameraHardware) controlCoordinator.update(buildDesiredCameraState(), coalesce)
    }
}
