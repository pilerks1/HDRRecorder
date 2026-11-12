package com.pilerks1.hdrrecorder.ui.camera.state

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.pilerks1.hdrrecorder.camera.camerax.BindOutcome
import com.pilerks1.hdrrecorder.camera.camerax.CameraControlCoordinator
import com.pilerks1.hdrrecorder.camera.camerax.CameraXSessionManager
import com.pilerks1.hdrrecorder.ui.camera.CameraRuntime
import com.pilerks1.hdrrecorder.ui.camera.controls.availableControlPanels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Schedules camera attachment and deferred settings changes within the screen scope. */
internal class CameraBindingController(
    private val uiState: MutableStateFlow<CameraUiState>,
    private val scope: CoroutineScope,
    private val surfaceRequests: MutableStateFlow<SurfaceRequest?>,
    private val cameraManager: CameraXSessionManager,
    private val controlCoordinator: CameraControlCoordinator,
    private val cameraRuntime: CameraRuntime,
    private val isRecordingBusy: () -> Boolean,
    private val cancelExposureTransition: () -> Unit,
    private val applyCameraControls: () -> Unit
) {
    private var pendingHardRebind = false
    private var pendingSoftRebind = false
    private var cameraLifecycleOwner: LifecycleOwner? = null
    private var lastDisplayRotation = 0
    private var cameraRebindJob: Job? = null

    val isRebindPending: Boolean
        get() = cameraRebindJob?.isActive == true || pendingHardRebind

    fun bindImmediately() = scheduleCameraRebind(debounce = false)

    fun deferSoftRebind() {
        pendingSoftRebind = true
    }

    fun onSettingsClosed() {
        uiState.update { it.copy(isSettingsSheetVisible = false) }
        val hardRebind = pendingHardRebind
        val softRebind = pendingSoftRebind
        pendingHardRebind = false
        pendingSoftRebind = false
        if (hardRebind) {
            scheduleCameraRebind(debounce = false)
        } else if (softRebind) {
            applyCameraControls()
        }
    }

    fun onRecordingFinalized() {
        val state = uiState.value
        if (pendingHardRebind && !state.isSettingsSheetVisible) {
            pendingHardRebind = false
            scheduleCameraRebind(debounce = false)
        }
    }

    /** Rebinds now (debounced), or after Settings closes when it is open. */
    fun requestRebind() {
        if (!cameraRuntime.usesCameraHardware) return
        if (uiState.value.isSettingsSheetVisible) pendingHardRebind = true
        else scheduleCameraRebind(debounce = true)
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
        if (!cameraRuntime.usesCameraHardware || uiState.value.isRecording) return
        cameraManager.updateRotation(rotation)
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
     * startup time. CameraXSessionManager binds only after the ProcessCameraProvider is actually ready.
     * While the debounce is pending, recording cannot start; a bind that finds a recording in
     * progress is deferred until the recording finalizes.
     */
    private fun scheduleCameraRebind(debounce: Boolean) {
        if (!cameraRuntime.usesCameraHardware || !uiState.value.arePresetsInitialized) return
        val lifecycleOwner = cameraLifecycleOwner ?: return
        cameraRebindJob?.cancel()
        cancelExposureTransition()
        uiState.update { it.copy(isExposureTransitioning = false) }

        fun bind() {
            if (isRecordingBusy()) {
                pendingHardRebind = true
                return
            }
            uiState.update { it.copy(isCameraReady = false) }
            cameraManager.requestBindWhenReady(
                lifecycleOwner = lifecycleOwner,
                onSurfaceRequest = { request -> surfaceRequests.value = request },
                configuration = uiState.value.toCaptureConfiguration(),
                displayRotation = lastDisplayRotation,
                onResult = ::onBindResult
            )
        }

        if (debounce && uiState.value.isCameraReady) {
            cameraRebindJob = scope.launch {
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
                uiState.update {
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
                applyCameraControls()
            }
            // The rejected settings never reached the camera; show what is actually bound.
            is BindOutcome.Retained -> {
                uiState.update {
                    it.withCaptureConfiguration(outcome.configuration).normalizeLowLightBoost()
                        .copy(isCameraReady = it.zoomState.isReady)
                }
                applyCameraControls()
            }
            BindOutcome.Failed -> Unit // Nothing is bound, so the camera remains unready.
        }
    }

    private companion object {
        const val CAMERA_REBIND_DEBOUNCE_MS = 300L
    }
}
