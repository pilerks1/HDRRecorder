package com.pilerks1.hdrrecorder.data

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.util.Range
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
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
import com.pilerks1.hdrrecorder.ui.CameraUiState
import com.pilerks1.hdrrecorder.data.camera.CameraProviderRepository
import com.pilerks1.hdrrecorder.data.camera.toCameraXQuality
import com.pilerks1.hdrrecorder.model.ColorFormat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@ExperimentalCamera2Interop
@SuppressLint("MissingPermission")
class CameraManager(
    private val context: Context,
    private val statsManager: StatsManager
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    private var preview: Preview? = null
    var videoCapture: VideoCapture<Recorder>? = null
        private set

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
        onCameraBound: (CameraControl, com.pilerks1.hdrrecorder.data.camera.CameraCapabilities) -> Unit
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

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bindUseCases(cameraProvider: ProcessCameraProvider, request: BindRequest) {
        cameraProvider.unbindAll()

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
            .setAspectRatioStrategy(AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO))
            .setResolutionStrategy(
                ResolutionStrategy(
                    android.util.Size(1000, 750),
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

        Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(statsManager.previewStatsCallback)

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
            .setAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetVideoEncodingBitRate(bitrateBps)
            .build()

        val videoCaptureBuilder = VideoCapture.Builder(recorder)
            .setVideoStabilizationEnabled(request.uiState.isStabilizationEnabled)
            .setTargetRotation(request.displayRotation)
            .setDynamicRange(dynamicRange)
            .apply { requestedFrameRate?.let(::setTargetFrameRate) }

        Camera2Interop.Extender(videoCaptureBuilder).setSessionCaptureCallback(statsManager.videoStatsCallback)
        val newVideoCapture = videoCaptureBuilder.build()

        try {
            val boundCamera = cameraProvider.bindToLifecycle(
                request.lifecycleOwner,
                cameraSelector,
                newPreview,
                newVideoCapture
            )
            camera = boundCamera
            preview = newPreview
            videoCapture = newVideoCapture
            
            // Extract characteristics and pass back to ViewModel
            val caps = com.pilerks1.hdrrecorder.data.camera.CameraCapabilitiesManager.extractCapabilities(boundCamera.cameraInfo)
            request.onCameraBound(boundCamera.cameraControl, caps)
            
            Log.d("CameraManager", "Use cases bound successfully.")
        } catch (exc: Exception) {
            camera = null
            preview = null
            videoCapture = null
            Log.e("CameraManager", "Use case binding failed", exc)
        }
    }

    fun updateRotation(rotation: Int) {
        preview?.targetRotation = rotation
        videoCapture?.targetRotation = rotation
    }

    fun getCameraControl(): CameraControl? {
        return camera?.cameraControl
    }

    fun tapToMeter(meteringPoint: MeteringPoint) {
        val action = FocusMeteringAction.Builder(
            meteringPoint,
            FocusMeteringAction.FLAG_AF or
                FocusMeteringAction.FLAG_AE or
                FocusMeteringAction.FLAG_AWB
        ).build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    fun release() {
        cameraExecutor.shutdown()
        pendingBind = null
        camera = null
        preview = null
        videoCapture = null
        cameraProvider?.unbindAll()
    }

    private data class BindRequest(
        val lifecycleOwner: LifecycleOwner,
        val onSurfaceRequest: (SurfaceRequest) -> Unit,
        val uiState: CameraUiState,
        val displayRotation: Int,
        val onCameraBound: (CameraControl, com.pilerks1.hdrrecorder.data.camera.CameraCapabilities) -> Unit
    )
}
