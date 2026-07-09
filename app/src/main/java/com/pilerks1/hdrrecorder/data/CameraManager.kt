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

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        onSurfaceRequest: (SurfaceRequest) -> Unit,
        uiState: CameraUiState,
        displayRotation: Int,
        onCameraBound: (CameraControl, com.pilerks1.hdrrecorder.data.camera.CameraCapabilities) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindUseCases(lifecycleOwner, onSurfaceRequest, uiState, displayRotation, onCameraBound)
            } catch (e: Exception) {
                Log.e("CameraManager", "Error starting camera", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun bindUseCases(
        lifecycleOwner: LifecycleOwner,
        onSurfaceRequest: (SurfaceRequest) -> Unit,
        uiState: CameraUiState,
        displayRotation: Int,
        onCameraBound: (CameraControl, com.pilerks1.hdrrecorder.data.camera.CameraCapabilities) -> Unit
    ) {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")
        cameraProvider.unbindAll()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        // --- Dynamic Range Resolution ---
        // Map the Color Format UI selection to CameraX's exact 10-bit HDR Encodings
        val baseDynamicRange = when (uiState.colorFormat) {
            "HDR10" -> DynamicRange.HDR10_10_BIT
            "HDR10+" -> DynamicRange.HDR10_PLUS_10_BIT
            "Unspec" -> DynamicRange.HDR_UNSPECIFIED_10_BIT
            "DB 8.4" -> DynamicRange.DOLBY_VISION_10_BIT
            "HLG" -> DynamicRange.HLG_10_BIT
            else -> DynamicRange.HLG_10_BIT
        }

        // The SDR UI hack explicitly forces standard dynamic range
        val dynamicRange = if (uiState.isSdrToneMapEnabled) DynamicRange.SDR else baseDynamicRange

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
            .setTargetFrameRate(Range(uiState.selectedFps, uiState.selectedFps))
            .setTargetRotation(displayRotation)
            .setDynamicRange(dynamicRange)
            .setPreviewStabilizationEnabled(uiState.isStabilizationEnabled)

        Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(statsManager.previewStatsCallback)

        preview = previewBuilder.build().also {
            it.setSurfaceProvider { request ->
                onSurfaceRequest(request)
            }
        }

        // --- Video Capture Use Case Configuration ---
        // Bitrate string conversion and fail-safe
        val bitrateMbps = uiState.bitrate.toIntOrNull() ?: 30
        val bitrateBps = bitrateMbps * 1_000_000
        val qualitySelector = QualitySelector.from(uiState.selectedResolution.quality)

        val recorder = Recorder.Builder()
            .setExecutor(cameraExecutor)
            .setQualitySelector(qualitySelector)
            .setAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetVideoEncodingBitRate(bitrateBps)
            .build()

        val videoCaptureBuilder = VideoCapture.Builder(recorder)
            .setVideoStabilizationEnabled(uiState.isStabilizationEnabled)
            .setTargetRotation(displayRotation)
            .setDynamicRange(dynamicRange)

        Camera2Interop.Extender(videoCaptureBuilder).setSessionCaptureCallback(statsManager.videoStatsCallback)
        videoCapture = videoCaptureBuilder.build()

        try {
            val boundCamera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, videoCapture)
            camera = boundCamera
            
            // Extract characteristics and pass back to ViewModel
            val caps = com.pilerks1.hdrrecorder.data.camera.CameraCapabilitiesManager.extractCapabilities(boundCamera.cameraInfo)
            onCameraBound(boundCamera.cameraControl, caps)
            
            Log.d("CameraManager", "Use cases bound successfully.")
        } catch (exc: Exception) {
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
        val action = FocusMeteringAction.Builder(meteringPoint, FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB).build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    fun release() {
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
    }
}