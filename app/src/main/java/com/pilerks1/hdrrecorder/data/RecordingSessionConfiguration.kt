package com.pilerks1.hdrrecorder.data

import android.util.Range
import androidx.camera.core.Preview
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import com.pilerks1.hdrrecorder.data.camera.toCameraXQuality
import com.pilerks1.hdrrecorder.ui.CameraUiState
import java.util.concurrent.Executor

/** Builds unbound use cases shared by recording and capability queries. */
object RecordingSessionConfiguration {
    fun preview(state: CameraUiState, rotation: Int): Preview =
        PreviewConfiguration.build(state.previewSettings, rotation, state.isStabilizationEnabled)

    fun video(state: CameraUiState, rotation: Int, executor: Executor? = null): VideoCapture<Recorder> {
        val recorder = RecordingConfiguration.configure(
            Recorder.Builder()
                .apply { executor?.let(::setExecutor) }
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
            }.build()
    }
}
