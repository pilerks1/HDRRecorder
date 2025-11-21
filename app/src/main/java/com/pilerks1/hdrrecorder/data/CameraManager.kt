package com.pilerks1.hdrrecorder.data

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.util.Range
import android.view.Display
import android.view.WindowManager
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
import com.google.common.util.concurrent.ListenableFuture
import com.pilerks1.hdrrecorder.model.Resolution
import com.pilerks1.hdrrecorder.ui.CameraUiState
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Manages the core camera session lifecycle.
 * This class is responsible for initializing the camera, binding use cases (Preview, VideoCapture)
 * to the lifecycle, handling taps for metering, and releasing the camera resources.
 */
@ExperimentalCamera2Interop
@SuppressLint("MissingPermission")
class CameraManager(
    private val context: Context,
    private val statsManager: StatsManager
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    // Use cases need to be accessible to update rotation dynamically
    private var preview: Preview? = null
    var videoCapture: VideoCapture<Recorder>? = null
        private set

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        onSurfaceRequest: (SurfaceRequest) -> Unit,
        uiState: CameraUiState
    ) {
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindUseCases(lifecycleOwner, onSurfaceRequest, uiState)
            } catch (e: Exception) {
                Log.e("CameraManager", "Error starting camera", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun bindUseCases(
        lifecycleOwner: LifecycleOwner,
        onSurfaceRequest: (SurfaceRequest) -> Unit,
        uiState: CameraUiState
    ) {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")
        cameraProvider.unbindAll()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        // Get current display rotation to set initial target rotation
        val displayRotation = getDisplayRotation()

        // --- Preview Use Case Configuration ---

        // 1. Resolution & Aspect Ratio
        val previewResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO))
            .setResolutionStrategy(
                ResolutionStrategy(
                    android.util.Size(1000, 750),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                )
            )
            .build()

        // 2. Configure Builder
        val previewBuilder = Preview.Builder()
            .setResolutionSelector(previewResolutionSelector)
            .setTargetFrameRate(Range(uiState.selectedFps, uiState.selectedFps))
            .setTargetRotation(displayRotation) // Set initial rotation

        // 3. Dynamic Range Logic
        // Only force SDR if the toggle is explicitly enabled.
        // Otherwise, do nothing (no dynamic range specified), letting CameraX decide.
        if (uiState.isSdrToneMapEnabled) {
            previewBuilder.setDynamicRange(DynamicRange.SDR)
        }

        // Hook up stats
        Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(statsManager.previewStatsCallback)

        preview = previewBuilder.build().also {
            it.setSurfaceProvider { request ->
                onSurfaceRequest(request)
            }
        }


        // --- Video Capture Use Case ---
        val bitrate = calculateBitrate(uiState.selectedFps)
        val qualitySelector = QualitySelector.from(uiState.selectedResolution.quality)
        val recorder = Recorder.Builder()
            .setExecutor(cameraExecutor)
            .setQualitySelector(qualitySelector)
            .setAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetVideoEncodingBitRate(bitrate)
            .build()

        val videoCaptureBuilder = VideoCapture.Builder(recorder)
            .setVideoStabilizationEnabled(true)
            .setTargetRotation(displayRotation) // Set initial rotation
            .setDynamicRange(
                when (uiState.gammaMode) {
                    "Device" -> DynamicRange.HLG_10_BIT
                    else -> DynamicRange.HDR_UNSPECIFIED_10_BIT
                }
            )

        Camera2Interop.Extender(videoCaptureBuilder).setSessionCaptureCallback(statsManager.videoStatsCallback)
        videoCapture = videoCaptureBuilder.build()

        try {
            camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, videoCapture)
            Log.d("CameraManager", "Use cases bound successfully.")
        } catch (exc: Exception) {
            Log.e("CameraManager", "Use case binding failed", exc)
        }
    }

    /**
     * Updates the target rotation for active use cases.
     * This is called when the physical device orientation changes,
     * ensuring video is upright even if the UI doesn't rotate (e.g., 180 degrees).
     */
    fun updateRotation(rotation: Int) {
        // Logging to verify rotation updates are being received
        Log.d("CameraManager", "Updating CameraX rotation to: $rotation")
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

    private fun calculateBitrate(fps: Int): Int {
        return when (fps) {
            60 -> 60_000_000
            30 -> 30_000_000
            24 -> 24_000_000
            else -> 30_000_000
        }
    }

    private fun getDisplayRotation(): Int {
        return try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        } catch (e: Exception) {
            android.view.Surface.ROTATION_0
        }
    }

    fun release() {
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
    }
}