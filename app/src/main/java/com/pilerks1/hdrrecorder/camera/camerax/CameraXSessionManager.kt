package com.pilerks1.hdrrecorder.camera.camerax

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import androidx.camera.camera2.interop.camera2Interop
import androidx.camera.camera2.interop.cameraId
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.compose.runtime.State
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.pilerks1.hdrrecorder.camera.camera2.CameraCapabilitiesReader
import com.pilerks1.hdrrecorder.camera.camera2.CameraCatalog
import com.pilerks1.hdrrecorder.camera.camera2.CameraFrameMetadata
import com.pilerks1.hdrrecorder.camera.camera2.CameraRequestSupersededException
import com.pilerks1.hdrrecorder.camera.model.CameraCapabilities
import com.pilerks1.hdrrecorder.camera.model.CameraZoomState
import com.pilerks1.hdrrecorder.camera.model.CaptureConfiguration
import com.pilerks1.hdrrecorder.camera.model.DefaultCameraInventory
import com.pilerks1.hdrrecorder.camera.model.LowLightBoostObservation
import com.pilerks1.hdrrecorder.camera.model.LowLightBoostPolicy
import com.pilerks1.hdrrecorder.camera.model.LowLightBoostRuntimeState
import com.pilerks1.hdrrecorder.camera.model.PreviewOptions
import com.pilerks1.hdrrecorder.camera.model.PreviewSettings
import com.pilerks1.hdrrecorder.camera.model.RecordingOptions
import com.pilerks1.hdrrecorder.data.diagnostics.ErrorReporter
import com.pilerks1.hdrrecorder.data.diagnostics.IssueCode
import com.pilerks1.hdrrecorder.data.telemetry.TelemetryCoordinator
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The result of one bind request, delivered on the main thread. */
sealed interface BindOutcome {
    data class Bound(
        val capabilities: CameraCapabilities,
        val previewOptions: PreviewOptions,
        val previewSettings: PreviewSettings
    ) : BindOutcome

    /** The request was rejected before teardown; the previous session (and its configuration) remains. */
    data class Retained(val configuration: CaptureConfiguration) : BindOutcome

    /** No session is bound. */
    data object Failed : BindOutcome
}

@SuppressLint("MissingPermission")
class CameraXSessionManager(
    private val context: Context,
    private val telemetryCoordinator: TelemetryCoordinator,
    private val controls: CameraControlCoordinator,
    private val reporter: ErrorReporter
) {

    /** One LiveData registration; removing it is the only cleanup each observation needs. */
    private class Observation<T>(private val source: LiveData<T>, private val observer: Observer<T>) {
        init { source.observeForever(observer) }
        fun remove() = source.removeObserver(observer)
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var activeCameraGeneration: Long? = null
    private var currentBind: BindRequest? = null
    private val defaultBackCamera = DefaultBackCameraRepository(context)
    private val _zoomState = MutableStateFlow(CameraZoomState())
    val zoomState = _zoomState.asStateFlow()
    private val _recordingOptions = MutableStateFlow(RecordingOptions())
    val recordingOptions = _recordingOptions.asStateFlow()
    private var zoomObservation: Observation<ZoomState>? = null
    private var cameraStateObservation: Observation<CameraState>? = null
    private var lowLightBoostObservation: Observation<Int>? = null
    private var zoomRevision = 0L
    private var confirmedZoomRatio = 1f
    private var observedLifecycle: Lifecycle? = null
    private val lifecycleObserver: LifecycleEventObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> invalidateAttachment()
            Lifecycle.Event.ON_START -> {
                if (activeCameraGeneration == null) {
                    val provider = cameraProvider
                    val request = currentBind
                    if (provider != null && request != null) bindUseCases(provider, request)
                }
            }
            // Bind requests hold the Activity as lifecycle owner; never retain it past destruction.
            Lifecycle.Event.ON_DESTROY -> {
                observedLifecycle?.removeObserver(lifecycleObserver)
                observedLifecycle = null
                currentBind = null
                pendingBind = null
            }
            else -> Unit
        }
    }

    private var preview: Preview? = null
    var videoCapture: VideoCapture<Recorder>? = null
        private set
    private val _lowLightBoostState = MutableStateFlow(LowLightBoostObservation())
    val lowLightBoostState = _lowLightBoostState.asStateFlow()

    private var pendingBind: BindRequest? = null
    private var isWaitingForProvider = false

    // Only capture-result processing runs here; the Recorder uses CameraX's own executor.
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * Queues the newest configuration until CameraX's process provider is ready. Repeated
     * requests before readiness collapse into one bind, so no stale intermediate configuration
     * can reach the camera.
     */
    fun requestBindWhenReady(
        lifecycleOwner: LifecycleOwner,
        onSurfaceRequest: (SurfaceRequest) -> Unit,
        configuration: CaptureConfiguration,
        displayRotation: Int,
        onResult: (BindOutcome) -> Unit
    ) {
        val request = BindRequest(lifecycleOwner, onSurfaceRequest, configuration, displayRotation, onResult)
        val readyProvider = cameraProvider
        if (readyProvider != null) {
            bindUseCases(readyProvider, request)
            return
        }

        pendingBind = request
        if (isWaitingForProvider) return
        isWaitingForProvider = true

        // CameraX owns the provider singleton; a failed future is not cached here, so a later
        // request retries initialization.
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            isWaitingForProvider = false
            val pending = pendingBind
            pendingBind = null
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                pending?.let { bindUseCases(provider, it) }
            } catch (e: Exception) {
                reporter.report(IssueCode.CAMERA_START, "cameraProvider", e)
                pending?.onResult?.invoke(BindOutcome.Failed)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** A request rejected before teardown leaves the previous session (if any) in place. */
    private fun rejectBeforeTeardown(request: BindRequest) {
        request.onResult(currentBind?.let { BindOutcome.Retained(it.configuration) } ?: BindOutcome.Failed)
    }

    private fun bindUseCases(cameraProvider: ProcessCameraProvider, request: BindRequest) {
        val cameraInfo = try {
            defaultBackCamera.cameraInfo(cameraProvider)
        } catch (error: Exception) {
            reporter.report(IssueCode.CAMERA_START, "resolveCamera", error)
            rejectBeforeTeardown(request)
            return
        }
        val cameraSelector = cameraInfo.cameraSelector
        val prepared = try {
            CameraXSessionPreparation.prepare(
                cameraInfo = cameraInfo,
                requested = request.configuration,
                displayRotation = request.displayRotation,
                onRecordingOptions = { _recordingOptions.value = it }
            )
        } catch (error: Exception) {
            reporter.report(IssueCode.CAMERA_CONFIGURATION,
                "prepareCamera", error, CameraXRecordingConfiguration.diagnosticContext(request.configuration))
            rejectBeforeTeardown(request)
            return
        }
        val previewOptions = prepared.previewOptions
        val config = prepared.configuration
        val newPreview = prepared.preview
        val newVideoCapture = prepared.videoCapture
        invalidateAttachment()
        val cameraGeneration = telemetryCoordinator.beginCameraGeneration()
        activeCameraGeneration = cameraGeneration

        var bindingCompleted = false
        try {
            cameraProvider.unbindAll()
            newPreview.setSurfaceProvider { surfaceRequest ->
                request.onSurfaceRequest(surfaceRequest)
            }

            val boundCamera = cameraProvider.bindToLifecycle(
                request.lifecycleOwner,
                cameraSelector,
                SessionConfig.Builder(listOf(newPreview, newVideoCapture))
                    .camera2Interop {
                        setRepeatingCaptureCallback(cameraExecutor, object : CameraCaptureSession.CaptureCallback() {
                            override fun onCaptureCompleted(
                                session: CameraCaptureSession,
                                request: CaptureRequest,
                                result: TotalCaptureResult
                            ) {
                                telemetryCoordinator.acceptFrame(CameraFrameMetadata(cameraGeneration, result))
                            }
                        })
                    }
                    .build()
            )
            bindingCompleted = true
            preview = newPreview
            videoCapture = newVideoCapture
            observeCameraErrors(boundCamera.cameraInfo)
            observeLowLightBoostState(boundCamera.cameraInfo, cameraGeneration)

            // Extract characteristics and pass back to ViewModel
            val cameraId = boundCamera.cameraInfo.cameraId
            val metadata = CameraCatalog.get(context).camera(cameraId).metadata.getOrThrow()
            val caps = CameraCapabilitiesReader.extractCapabilities(
                metadata,
                LowLightBoostPolicy.isSupported(boundCamera.cameraInfo.isLowLightBoostSupported),
                extendManualRanges = config.isExtendManualRangesEnabled
            ).copy(
                camera2Modes = CameraCapabilitiesReader.extractCamera2Modes(metadata)
            )
            controls.attach(CameraXRequestBackend(cameraGeneration, boundCamera.cameraControl))
            observeZoom(boundCamera.cameraInfo, defaultBackCamera.zoomInventory(cameraInfo))
            val savedZoom = config.zoomRatio.coerceIn(_zoomState.value.minRatio, _zoomState.value.maxRatio)
            confirmedZoomRatio = _zoomState.value.ratio
            currentBind = request.copy(configuration = config.copy(zoomRatio = savedZoom))
            request.onResult(BindOutcome.Bound(caps, previewOptions, config.previewSettings))
            setZoomRatio(savedZoom)
            if (observedLifecycle !== request.lifecycleOwner.lifecycle) {
                observedLifecycle?.removeObserver(lifecycleObserver)
                observedLifecycle = request.lifecycleOwner.lifecycle
                observedLifecycle?.addObserver(lifecycleObserver)
            }
            if (!request.lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                invalidateAttachment()
            }

        } catch (exc: Exception) {
            try {
                cameraProvider.unbindAll()
            } catch (cleanupError: Exception) {
                exc.addSuppressed(cleanupError)
            } finally {
                preview = null
                videoCapture = null
                // Nothing is bound, so a later ON_START must not resurrect an older configuration.
                currentBind = null
                invalidateAttachment()
            }
            reporter.report(if (bindingCompleted) IssueCode.CAMERA_SETUP else IssueCode.CAMERA_CONFIGURATION,
                "bindCamera", exc, CameraXRecordingConfiguration.diagnosticContext(config))
            request.onResult(BindOutcome.Failed)
        }
    }

    private fun observeZoom(info: CameraInfo, inventory: DefaultCameraInventory) {
        zoomObservation = Observation(info.zoomState) { state ->
            _zoomState.value = CameraZoomState(
                inventory = inventory,
                ratio = state.zoomRatio,
                minRatio = state.minZoomRatio,
                maxRatio = state.maxZoomRatio,
                isReady = true
            )
        }
    }

    fun setZoomRatio(value: Float) {
        val state = _zoomState.value
        if (!state.isReady || !value.isFinite()) return
        val ratio = value.coerceIn(state.minRatio, state.maxRatio)
        val revision = ++zoomRevision
        val generation = activeCameraGeneration
        val future = controls.setZoomRatio(ratio) ?: return
        fun rememberRatio(ratio: Float) {
            currentBind = currentBind?.let { it.copy(configuration = it.configuration.copy(zoomRatio = ratio)) }
        }
        rememberRatio(ratio)
        future.addListener({
            if (revision == zoomRevision && generation == activeCameraGeneration) {
                try {
                    future.get()
                    confirmedZoomRatio = ratio
                } catch (error: Exception) {
                    val cause = error.cause ?: error
                    if (cause !is CameraControl.OperationCanceledException && cause !is CameraRequestSupersededException) {
                        val restored = confirmedZoomRatio.coerceIn(state.minRatio, state.maxRatio)
                        _zoomState.value = _zoomState.value.copy(ratio = restored)
                        rememberRatio(restored)
                        reporter.report(IssueCode.CAMERA_CONTROL, "zoom", cause, mapOf("recovery" to "previous zoom restored"))
                    }
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun updateRotation(rotation: Int) {
        preview?.targetRotation = rotation
        videoCapture?.targetRotation = rotation
    }

    private fun invalidateAttachment() {
        clearLowLightBoostObserver()
        cameraStateObservation?.remove()
        cameraStateObservation = null
        zoomRevision++
        zoomObservation?.remove()
        zoomObservation = null
        _zoomState.value = _zoomState.value.copy(isReady = false)
        controls.detach()
        activeCameraGeneration?.let(telemetryCoordinator::endCameraGeneration)
        activeCameraGeneration = null
    }

    private fun observeCameraErrors(info: CameraInfo) {
        var previousCode: Int? = null
        cameraStateObservation = Observation(info.cameraState) { state ->
            val error = state.error
            if (error != null && error.code != previousCode) {
                reporter.report(IssueCode.CAMERA_RUNTIME, "cameraState", error.cause,
                    mapOf("platformCode" to error.code.toString(), "cameraId" to info.cameraId))
            }
            previousCode = error?.code
        }
    }

    fun release() {
        observedLifecycle?.removeObserver(lifecycleObserver)
        observedLifecycle = null
        currentBind = null
        pendingBind = null
        try {
            cameraProvider?.unbindAll()
        } catch (error: Exception) {
            reporter.report(IssueCode.CAMERA_CONTROL, "releaseCamera", error, notify = false)
        }
        preview = null
        videoCapture = null
        invalidateAttachment()
        // Teardown above may still deliver capture callbacks to this executor.
        cameraExecutor.shutdown()
    }

    private fun observeLowLightBoostState(cameraInfo: CameraInfo, generation: Long) {
        if (!LowLightBoostPolicy.isSupported(cameraInfo.isLowLightBoostSupported)) return
        lowLightBoostObservation = Observation(cameraInfo.lowLightBoostState) { state ->
            if (activeCameraGeneration == generation) {
                _lowLightBoostState.value = LowLightBoostObservation(generation, when (state) {
                    LowLightBoostState.ACTIVE -> LowLightBoostRuntimeState.ACTIVE
                    LowLightBoostState.INACTIVE -> LowLightBoostRuntimeState.INACTIVE
                    else -> LowLightBoostRuntimeState.OFF
                })
            }
        }
    }

    private fun clearLowLightBoostObserver() {
        lowLightBoostObservation?.remove()
        lowLightBoostObservation = null
        _lowLightBoostState.value = LowLightBoostObservation()
    }

    private data class BindRequest(
        val lifecycleOwner: LifecycleOwner,
        val onSurfaceRequest: (SurfaceRequest) -> Unit,
        val configuration: CaptureConfiguration,
        val displayRotation: Int,
        val onResult: (BindOutcome) -> Unit
    )
}
