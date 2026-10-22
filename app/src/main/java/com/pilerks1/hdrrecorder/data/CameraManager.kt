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
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
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
import com.pilerks1.hdrrecorder.model.ColorFormat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt
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
        onCameraBound: (com.pilerks1.hdrrecorder.data.camera.CameraCapabilities) -> Unit
    ) {
        val request = BindRequest(lifecycleOwner, onSurfaceRequest, uiState, displayRotation, onCameraBound)
        currentBind = request
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
        invalidateAttachment()
        clearLowLightBoostObserver()
        cameraProvider.unbindAll()
        val cameraGeneration = telemetryCoordinator.beginCameraGeneration()
        activeCameraGeneration = cameraGeneration

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        // --- Dynamic Range Resolution ---
        // Map the Color Format UI selection to CameraX's exact 10-bit HDR Encodings
        val baseDynamicRange = when (request.uiState.colorFormat) {
            ColorFormat.HDR10 -> DynamicRange.HDR10_10_BIT
            ColorFormat.HDR10_PLUS -> DynamicRange.HDR10_PLUS_10_BIT
            ColorFormat.UNSPECIFIED_10_BIT -> DynamicRange.HDR_UNSPECIFIED_10_BIT
            ColorFormat.DOLBY_VISION_84 -> DynamicRange.DOLBY_VISION_10_BIT
            ColorFormat.HLG -> DynamicRange.HLG_10_BIT
        }

        // The SDR UI hack explicitly forces standard dynamic range
        val dynamicRange = if (request.uiState.isSdrToneMapEnabled) DynamicRange.SDR else baseDynamicRange
        val requestedFrameRate = request.uiState.selectedFps
            .takeUnless { request.uiState.manualControlsState.isManualFps }
            ?.let { Range(it, it) }

        // --- Preview Use Case Configuration ---
        val previewResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                AspectRatioStrategy(
                    request.uiState.selectedAspectRatio.cameraXValue,
                    AspectRatioStrategy.FALLBACK_RULE_NONE
                )
            )
            .setResolutionStrategy(
                ResolutionStrategy(
                    android.util.Size(
                        1000,
                        (1000f / request.uiState.selectedAspectRatio.landscapeRatio).roundToInt()
                    ),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                )
            )
            .build()

        val previewBuilder = Preview.Builder()
            .setResolutionSelector(previewResolutionSelector)
            .setTargetRotation(request.displayRotation)
            .setDynamicRange(dynamicRange)
            .setPreviewStabilizationEnabled(request.uiState.isStabilizationEnabled)
            .apply { requestedFrameRate?.let(::setTargetFrameRate) }


        val newPreview = previewBuilder.build().also {
            it.setSurfaceProvider { surfaceRequest ->
                request.onSurfaceRequest(surfaceRequest)
            }
        }

        // --- Video Capture Use Case Configuration ---
        // Bitrate string conversion and fail-safe
        val bitrateMbps = Bitrate.parseOrDefault(request.uiState.bitrate)
        val bitrateBps = bitrateMbps * 1_000_000
        val qualitySelector = QualitySelector.from(request.uiState.selectedResolution.toCameraXQuality())

        val recorder = Recorder.Builder()
            .setExecutor(cameraExecutor)
            .setQualitySelector(qualitySelector)
            .setAspectRatio(request.uiState.selectedAspectRatio.cameraXValue)
            .setTargetVideoEncodingBitRate(bitrateBps)
            .build()

        val videoCaptureBuilder = VideoCapture.Builder(recorder)
            .setVideoStabilizationEnabled(request.uiState.isStabilizationEnabled)
            .setTargetRotation(request.displayRotation)
            .setDynamicRange(dynamicRange)
            .apply { requestedFrameRate?.let(::setTargetFrameRate) }

        val newVideoCapture = videoCaptureBuilder.build()

        try {
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
            val caps = CameraCapabilitiesManager.extractCapabilities(metadata, boundCamera.cameraInfo.isLowLightBoostSupported)
            val attachment = CameraXRequestBackend(cameraId, cameraGeneration, boundCamera.cameraControl)
            controls.attach(attachment, attachment)
            request.onCameraBound(caps)
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
        }
    }

    fun updateRotation(rotation: Int) {
        preview?.targetRotation = rotation
        videoCapture?.targetRotation = rotation
    }

    private fun invalidateAttachment() {
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
        val onCameraBound: (com.pilerks1.hdrrecorder.data.camera.CameraCapabilities) -> Unit
    )
}
