package com.pilerks1.hdrrecorder.compatibility

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.ExperimentalSessionConfig
import androidx.camera.core.SessionConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoCapabilities
import com.pilerks1.hdrrecorder.model.Resolution
import kotlinx.coroutines.guava.await
import android.media.CamcorderProfile
import android.os.Build
import android.media.EncoderProfiles
import java.util.Locale

/**
 * A manager that performs a detailed compatibility check of the device's camera capabilities.
 * It iterates through various resolutions, HDR profiles, and stabilization settings
 * to build a comprehensive support table.
 */
class CcManager(private val context: Context) {

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraInfo: CameraInfo
    private lateinit var videoCapabilities: VideoCapabilities

    /**
     * Asynchronously initializes the CameraProvider and gets the default back camera info.
     * This must be called before getCompatibilityData.
     */
    private suspend fun initializeCamera() {
        cameraProvider = ProcessCameraProvider.getInstance(context).await()
        // Get the CameraInfo for the first available back camera.
        cameraInfo = cameraProvider.availableCameraInfos.first {
            it.lensFacing == CameraSelector.LENS_FACING_BACK
        }
        videoCapabilities = Recorder.getVideoCapabilities(cameraInfo)
    }

    /**
     * Performs the full compatibility scan and returns the results.
     */
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    suspend fun getCompatibilityData(): CompatibilityResult {
        initializeCamera()

        // 1. Define all possible HDR profiles we want to check
        val candidateHdrProfiles = setOf(
            DynamicRange.HLG_10_BIT,
            DynamicRange.HDR10_10_BIT,
            DynamicRange.HDR10_PLUS_10_BIT,
            DynamicRange.DOLBY_VISION_10_BIT
        )

        // 2. Query the device for which of these are actually supported
        val supportedHdrProfiles = cameraInfo.querySupportedDynamicRanges(candidateHdrProfiles)

        val resolutions = listOf(Resolution.HIGHEST, Resolution.UHD, Resolution.FHD)
        val aspectRatios = mapOf(
            "4:3" to AspectRatio.RATIO_4_3,
            "16:9" to AspectRatio.RATIO_16_9
        )

        val tableRows4by3 = mutableListOf<CompatibilityResult.TableRow>()
        val tableRows16by9 = mutableListOf<CompatibilityResult.TableRow>()

        // Get general device info first using the native Camera2 API
        val androidCameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = Camera2CameraInfo.from(cameraInfo).cameraId
        val characteristics = androidCameraManager.getCameraCharacteristics(cameraId)
        val hardwareLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)


        // compute maxBitrate (human-readable string). try modern API first, fallback to legacy CamcorderProfile
        var maxBitrate = "N/A"

        for ((ratioName, ratioEnum) in aspectRatios) {
            for (resolution in resolutions) {
                // We will build the list of supported HDR formats for this specific resolution row
                val row = CompatibilityResult.TableRow(
                    quality = resolution.qualityName,
                    resolution = "N/A",
                    fps24 = checkFpsSupport(resolution.quality, 24, ratioEnum, supportedHdrProfiles),
                    fps30 = checkFpsSupport(resolution.quality, 30, ratioEnum, supportedHdrProfiles),
                    fps60 = checkFpsSupport(resolution.quality, 60, ratioEnum, supportedHdrProfiles)
                )


                if (ratioName == "4:3") {
                    tableRows4by3.add(row)
                } else {
                    tableRows16by9.add(row)
                }
            }
        }

        return CompatibilityResult(
            hardwareLevel = hardwareLevelToString(hardwareLevel),
            maxBitrate = maxBitrate,
            tableRows4by3 = tableRows4by3,
            tableRows16by9 = tableRows16by9
        )
    }

    /**
     * Checks which HDR profiles are supported for a given configuration.
     * @return A list of DynamicRangeSupport objects indicating stabilization support.
     */
    @OptIn(ExperimentalSessionConfig::class)
    private fun checkFpsSupport(
        quality: Quality,
        targetFps: Int,
        aspectRatio: Int,
        supportedHdrProfiles: Set<DynamicRange>
    ): List<CompatibilityResult.DynamicRangeSupport> {
        val supportedHdrForThisConfig = mutableListOf<CompatibilityResult.DynamicRangeSupport>()
        val hdrProfileNames = mapOf(
            DynamicRange.HLG_10_BIT to "HLG",
            DynamicRange.HDR10_10_BIT to "HDR10",
            DynamicRange.HDR10_PLUS_10_BIT to "HDR10+",
            DynamicRange.DOLBY_VISION_10_BIT to "DV10"
        )

        // Iterate through the profiles the device supports
        for (profile in supportedHdrProfiles) {
            // Get the qualities supported for THIS specific HDR profile
            val qualitiesForProfile = videoCapabilities.getSupportedQualities(profile)

            // If the current resolution (quality) is NOT supported for this HDR profile, skip to the next profile
            if (!qualitiesForProfile.contains(quality)) {
                continue
            }

            // If it is supported, now check the frame rates for stabilization
            val name = hdrProfileNames[profile] ?: "HDR"

            val stabilizedRanges = getFrameRatesForConfig(quality, profile, true, aspectRatio)
            val stabilizationSupported = stabilizedRanges.any { it.contains(targetFps) }

            if (stabilizationSupported) {
                supportedHdrForThisConfig.add(CompatibilityResult.DynamicRangeSupport(name, true))
            } else {
                val unstabilizedRanges = getFrameRatesForConfig(quality, profile, false, aspectRatio)
                if (unstabilizedRanges.any { it.contains(targetFps) }) {
                    supportedHdrForThisConfig.add(CompatibilityResult.DynamicRangeSupport(name, false))
                }
            }
        }
        return supportedHdrForThisConfig
    }

    /**
     * Creates a VideoCapture UseCase for a specific configuration and gets its supported frame rates.
     */
    @ExperimentalSessionConfig
    private fun getFrameRatesForConfig(
        quality: Quality,
        dynamicRange: DynamicRange,
        stabilization: Boolean,
        aspectRatio: Int
    ): Set<Range<Int>> {
        return try {
            val recorder = Recorder.Builder()
                .setAspectRatio(aspectRatio)
                .setQualitySelector(QualitySelector.from(quality, FallbackStrategy.higherQualityThan(quality)))     // This fallback strategy prevents false positives in the table
                .build()

            val videoCapture = VideoCapture.Builder(recorder)
                .setVideoStabilizationEnabled(stabilization)
                .setDynamicRange(dynamicRange)
                .build()

            // Build a PUBLIC SessionConfig from the use case
            val publicConfig = SessionConfig(videoCapture)
            return cameraInfo.getSupportedFrameRateRanges(publicConfig)
        } catch (e: Exception) {
            Log.e("CcManager", "Could not get frame rates for config ($quality, $dynamicRange, $stabilization, $aspectRatio)", e)
            emptySet()
        }
    }

    private fun hardwareLevelToString(level: Int?): String {
        return when (level) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN"
        }
    }
}
