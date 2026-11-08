package com.pilerks1.hdrrecorder.data

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.util.Range
import androidx.camera.camera2.interop.camera2Interop
import androidx.camera.camera2.interop.cameraId
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import com.pilerks1.hdrrecorder.data.camera.CameraControlCoordinator
import com.pilerks1.hdrrecorder.data.camera.CameraCapabilitiesManager
import com.pilerks1.hdrrecorder.data.camera.camerax.CameraXRequestBackend
import com.pilerks1.hdrrecorder.data.camera.core.CameraCatalog
import com.pilerks1.hdrrecorder.data.camera.core.CameraFrameMetadata
import com.pilerks1.hdrrecorder.data.camera.core.CameraZoomState
import com.pilerks1.hdrrecorder.data.camera.core.CameraRequestSupersededException
import com.pilerks1.hdrrecorder.data.camera.core.ZoomMapping
import com.pilerks1.hdrrecorder.data.camera.DefaultBackCameraRepository
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pilerks1.hdrrecorder.ui.CameraUiState
import com.pilerks1.hdrrecorder.data.camera.CameraProviderRepository
import com.pilerks1.hdrrecorder.data.camera.TelemetryCoordinator
import com.pilerks1.hdrrecorder.data.camera.toCameraXQuality
import com.pilerks1.hdrrecorder.model.RecordingOptions
import com.pilerks1.hdrrecorder.model.PreviewOptions
import com.pilerks1.hdrrecorder.model.PreviewSettings
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@SuppressLint("MissingPermission")
class CameraManager(
    private val context: Context,
    private val telemetryCoordinator: TelemetryCoordinator,
    private val controls: CameraControlCoordinator
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var activeCameraGeneration: Long? = null
    private var currentBind: BindRequest? = null
    private val defaultBackCamera = DefaultBackCameraRepository(context)
    private val _zoomState = MutableStateFlow(CameraZoomState())
    val zoomState = _zoomState.asStateFlow()
    private val _recordingOptions = MutableStateFlow(RecordingOptions())
    val recordingOptions = _recordingOptions.asStateFlow()
    private var zoomSource: LiveData<ZoomState>? = null
    private var zoomObserver: Observer<ZoomState>? = null
    private var zoomRevision = 0L
    private var confirmedZoomRatio = 1f
    private var observedLifecycle: Lifecycle? = null
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> invalidateAttachment()
            Lifecycle.Event.ON_START -> {
                if (activeCameraGeneration == null) {
                    val provider = cameraProvider
                    val request = currentBind
                    if (provider != null && request != null) bindUseCases(provider, request)
                }
            }
            else -> Unit
        }
    }

    private var preview: Preview? = null
    var videoCapture: VideoCapture<Recorder>? = null
        private set
    private val _isLowLightBoostActive = MutableStateFlow(false)
    val isLowLightBoostActive = _isLowLightBoostActive.asStateFlow()
    private var lowLightBoostStateSource: LiveData<Int>? = null
    private var lowLightBoostStateObserver: Observer<Int>? = null

    private var pendingBind: BindRequest? = null
    private var isWaitingForProvider = false

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * Queues the newest configuration until CameraX's process provider is ready. Repeated
     * requests before readiness collapse into one bind, so no stale intermediate configuration
     * can reach the camera.
     */
    fun requestBindWhenReady(
        lifecycleOwner: LifecycleOwner,
        onSurfaceRequest: (SurfaceRequest) -> Unit,
        uiState: CameraUiState,
        displayRotation: Int,
        onCameraBound: (com.pilerks1.hdrrecorder.data.camera.CameraCapabilities, PreviewOptions, PreviewSettings, String?) -> Unit
    ) {
        val request = BindRequest(lifecycleOwner, onSurfaceRequest, uiState, displayRotation, onCameraBound)
        val readyProvider = cameraProvider
        if (readyProvider != null) {
            bindUseCases(readyProvider, request)
            return
        }

        pendingBind = request
        if (isWaitingForProvider) return
        isWaitingForProvider = true

        val cameraProviderFuture = CameraProviderRepository.getFuture(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                pendingBind?.let { pending ->
                    pendingBind = null
                    bindUseCases(cameraProvider!!, pending)
                }
            } catch (e: Exception) {
                Log.e("CameraManager", "Error starting camera", e)
            } finally {
                isWaitingForProvider = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindUseCases(cameraProvider: ProcessCameraProvider, request: BindRequest) {
        val cameraInfo = try {
            defaultBackCamera.cameraInfo(cameraProvider)
        } catch (error: Exception) {
            Log.e("CameraManager", "Unable to resolve the default back camera", error)
            return
        }
        val cameraSelector = cameraInfo.cameraSelector
        // Resolve preview preferences before tearing down the working session.
        val previewOptions: PreviewOptions
        var state: CameraUiState
        var previewError: String? = null
        val newPreview: Preview
        val newVideoCapture: VideoCapture<Recorder>
        try {
            previewOptions = PreviewConfiguration.queryOptions(cameraInfo)
            state = request.uiState.copy(previewSettings = previewOptions.reconcile(request.uiState.previewSettings))
            val options = RecordingConfiguration.queryOptions(cameraInfo)
            _recordingOptions.value = options
            RecordingConfiguration.validate(state, options)
            newVideoCapture = buildVideoCapture(state, request.displayRotation)
            var candidate = PreviewConfiguration.build(state.previewSettings, request.displayRotation, state.isStabilizationEnabled)
            if (state.previewSettings.dynamicRange != null &&
                !supportsPreviewDynamicRange(cameraInfo, candidate, newVideoCapture)
            ) {
                val previous = currentBind?.uiState?.previewSettings?.dynamicRange
                    ?.takeIf { it in previewOptions.dynamicRanges }
                state = state.copy(previewSettings = state.previewSettings.copy(dynamicRange = previous))
                candidate = PreviewConfiguration.build(state.previewSettings, request.displayRotation, state.isStabilizationEnabled)
                if (previous != null && !supportsPreviewDynamicRange(cameraInfo, candidate, newVideoCapture)) {
                    state = state.copy(previewSettings = state.previewSettings.copy(dynamicRange = null))
                    candidate = PreviewConfiguration.build(state.previewSettings, request.displayRotation, state.isStabilizationEnabled)
                }
                previewError = "Preview dynamic range is incompatible with the current recording configuration. " +
                    "Restored ${state.previewSettings.dynamicRange?.displayName ?: "Auto"}."
            }
            newPreview = candidate
        } catch (error: Exception) {
            Log.e("CameraManager", "Unable to prepare camera configuration", error)
            return
        }
        invalidateAttachment()
        clearLowLightBoostObserver()
        cameraProvider.unbindAll()
        val cameraGeneration = telemetryCoordinator.beginCameraGeneration()
        activeCameraGeneration = cameraGeneration

        try {
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
            preview = newPreview
            videoCapture = newVideoCapture
            observeLowLightBoostState(boundCamera.cameraInfo)
            
            // Extract characteristics and pass back to ViewModel
            val cameraId = boundCamera.cameraInfo.cameraId
            val metadata = CameraCatalog.get(context).camera(cameraId).metadata.getOrThrow()
            val caps = CameraCapabilitiesManager.extractCapabilities(
                metadata,
                boundCamera.cameraInfo.isLowLightBoostSupported,
                extendManualRanges = request.uiState.isExtendManualRangesEnabled
            ).copy(
                camera2Modes = CameraCapabilitiesManager.extractCamera2Modes(metadata)
            )
            val attachment = CameraXRequestBackend(cameraId, cameraGeneration, boundCamera.cameraControl)
            controls.attach(attachment, attachment)
            observeZoom(boundCamera.cameraInfo, defaultBackCamera.zoomInventory(cameraInfo))
            val savedZoom = request.uiState.zoomState.ratio.coerceIn(_zoomState.value.minRatio, _zoomState.value.maxRatio)
            confirmedZoomRatio = _zoomState.value.ratio
            currentBind = request.copy(uiState = state.copy(zoomState = _zoomState.value.copy(ratio = savedZoom)))
            request.onCameraBound(caps, previewOptions, state.previewSettings, previewError)
            setZoomRatio(savedZoom)
            if (observedLifecycle !== request.lifecycleOwner.lifecycle) {
                observedLifecycle?.removeObserver(lifecycleObserver)
                observedLifecycle = request.lifecycleOwner.lifecycle
                observedLifecycle?.addObserver(lifecycleObserver)
            }
            if (!request.lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                invalidateAttachment()
            }
            
            Log.d("CameraManager", "Use cases bound successfully.")
        } catch (exc: Exception) {
            controls.detach()
            cameraProvider.unbindAll()
            telemetryCoordinator.endCameraGeneration(cameraGeneration)
            activeCameraGeneration = null
            preview = null
            videoCapture = null
            Log.e("CameraManager", "Use case binding failed", exc)
            invalidateAttachment()
        }
    }

    /** Only explicit preview dynamic ranges need a compatibility preflight. */
    fun previewDynamicRangeError(state: CameraUiState): String? {
        if (state.previewSettings.dynamicRange == null) return null
        val provider = cameraProvider ?: return "Camera capabilities are not available yet."
        return try {
            val info = defaultBackCamera.cameraInfo(provider)
            val candidate = PreviewConfiguration.build(state.previewSettings, 0, state.isStabilizationEnabled)
            if (supportsPreviewDynamicRange(info, candidate, buildVideoCapture(state, 0))) null
            else "Preview dynamic range is incompatible with the current recording configuration."
        } catch (error: Exception) {
            Log.w("CameraManager", "Unable to check preview dynamic range", error)
            "Unable to verify this preview dynamic range with the current recording configuration."
        }
    }

    private fun supportsPreviewDynamicRange(info: CameraInfo, preview: Preview, video: VideoCapture<Recorder>): Boolean =
        try {
            info.isSessionConfigSupported(SessionConfig.Builder(listOf(preview, video)).build())
        } catch (error: IllegalArgumentException) {
            Log.w("CameraManager", "Conflicting preview and recording configuration", error)
            false
        }

    private fun buildVideoCapture(state: CameraUiState, rotation: Int): VideoCapture<Recorder> {
        val recorder = RecordingConfiguration.configure(
            Recorder.Builder()
                .setExecutor(cameraExecutor)
                .setQualitySelector(QualitySelector.from(state.selectedResolution.toCameraXQuality()))
                .setAspectRatio(state.selectedAspectRatio.cameraXValue),
            state
        ).build()
        return VideoCapture.Builder(recorder)
            .setVideoStabilizationEnabled(state.isStabilizationEnabled)
            .setTargetRotation(rotation)
            .setDynamicRange(RecordingConfiguration.dynamicRange(state))
            .apply {
                if (!state.manualControlsState.isManualFps) {
                    setTargetFrameRate(Range(state.selectedFps, state.selectedFps))
                }
            }
            .build()
    }

    private fun observeZoom(
        info: CameraInfo,
        inventory: com.pilerks1.hdrrecorder.data.camera.core.DefaultCameraInventory
    ) {
        val source = info.zoomState
        val observer = Observer<ZoomState> { state ->
            _zoomState.value = CameraZoomState(
                inventory = inventory,
                ratio = state.zoomRatio,
                minRatio = state.minZoomRatio,
                maxRatio = state.maxZoomRatio,
                isReady = true
            )
        }
        zoomSource = source
        zoomObserver = observer
        source.observeForever(observer)
    }

    fun setLinearZoom(progress: Float) = submitZoom(progress, linear = true)

    fun setZoomRatio(ratio: Float) = submitZoom(ratio, linear = false)

    private fun submitZoom(value: Float, linear: Boolean) {
        val state = _zoomState.value
        if (!state.isReady || !value.isFinite()) return
        val ratio = if (linear) ZoomMapping.ratio(value, state.minRatio, state.maxRatio)
            else value.coerceIn(state.minRatio, state.maxRatio)
        val revision = ++zoomRevision
        val generation = activeCameraGeneration
        val future = if (linear) controls.setLinearZoom(value.coerceIn(0f, 1f))
            else controls.setZoomRatio(ratio)
        future ?: return
        fun rememberRatio(ratio: Float) {
            currentBind = currentBind?.let { it.copy(uiState = it.uiState.copy(zoomState = state.copy(ratio = ratio))) }
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
                        Log.w("CameraManager", "Unable to apply zoom", cause)
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
        zoomRevision++
        zoomObserver?.let { observer -> zoomSource?.removeObserver(observer) }
        zoomSource = null
        zoomObserver = null
        _zoomState.value = _zoomState.value.copy(isReady = false)
        controls.detach()
        activeCameraGeneration?.let(telemetryCoordinator::endCameraGeneration)
        activeCameraGeneration = null
    }

    fun release() {
        observedLifecycle?.removeObserver(lifecycleObserver)
        observedLifecycle = null
        currentBind = null
        invalidateAttachment()
        clearLowLightBoostObserver()
        cameraExecutor.shutdown()
        pendingBind = null
        preview = null
        videoCapture = null
        cameraProvider?.unbindAll()
    }

    private fun observeLowLightBoostState(cameraInfo: CameraInfo) {
        clearLowLightBoostObserver()
        if (!cameraInfo.isLowLightBoostSupported) return

        val source = cameraInfo.lowLightBoostState
        val observer = Observer<Int> { state ->
            _isLowLightBoostActive.value = state == LowLightBoostState.ACTIVE
        }
        lowLightBoostStateSource = source
        lowLightBoostStateObserver = observer
        source.observeForever(observer)
    }

    private fun clearLowLightBoostObserver() {
        val source = lowLightBoostStateSource
        val observer = lowLightBoostStateObserver
        if (source != null && observer != null) source.removeObserver(observer)
        lowLightBoostStateSource = null
        lowLightBoostStateObserver = null
        _isLowLightBoostActive.value = false
    }

    private data class BindRequest(
        val lifecycleOwner: LifecycleOwner,
        val onSurfaceRequest: (SurfaceRequest) -> Unit,
        val uiState: CameraUiState,
        val displayRotation: Int,
        val onCameraBound: (com.pilerks1.hdrrecorder.data.camera.CameraCapabilities, PreviewOptions, PreviewSettings, String?) -> Unit
    )
}
