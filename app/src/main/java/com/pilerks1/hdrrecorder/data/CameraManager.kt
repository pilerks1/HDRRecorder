package com.pilerks1.hdrrecorder.data

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
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

    var videoCapture: VideoCapture<Recorder>? = null
        private set

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        uiState: CameraUiState
    ) {
        val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> =
            ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindUseCases(lifecycleOwner, surfaceProvider, uiState)
            } catch (e: Exception) {
                Log.e("CameraManager", "Error starting camera", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun bindUseCases(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        uiState: CameraUiState
    ) {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")
        cameraProvider.unbindAll()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        // --- Preview Use Case ---
        val previewResolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO))
            .setResolutionStrategy(
                ResolutionStrategy(
                    android.util.Size(1000, 750),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                )
            )
            .build()
        val previewBuilder = Preview.Builder().setResolutionSelector(previewResolutionSelector)
        Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(statsManager.previewStatsCallback)
        val preview = previewBuilder.build().also { it.surfaceProvider = surfaceProvider }


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

    fun release() {
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
    }
}
