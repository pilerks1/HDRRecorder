package com.pilerks1.hdrrecorder.data

import android.util.Range
import androidx.camera.core.Preview
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import com.pilerks1.hdrrecorder.data.camera.toCameraXQuality

/** Builds unbound use cases shared by recording and capability queries. */
object RecordingSessionConfiguration {
    fun preview(config: CaptureConfiguration, rotation: Int): Preview =
        PreviewConfiguration.build(config.previewSettings, rotation, config.isStabilizationEnabled)

    /** The Recorder keeps CameraX's default executor, separate from capture-result processing. */
    fun video(config: CaptureConfiguration, rotation: Int): VideoCapture<Recorder> {
        val recorder = RecordingConfiguration.configure(
            Recorder.Builder()
                .setQualitySelector(QualitySelector.from(config.resolution.toCameraXQuality()))
                .setAspectRatio(config.aspectRatio.cameraXValue),
            config
        ).build()
        return VideoCapture.Builder(recorder)
            .setVideoStabilizationEnabled(config.isStabilizationEnabled)
            .setTargetRotation(rotation)
            .setDynamicRange(RecordingConfiguration.dynamicRange(config))
            .apply {
                if (config.manualFpsRange == null) setTargetFrameRate(Range(config.fps, config.fps))
            }.build()
    }
}
