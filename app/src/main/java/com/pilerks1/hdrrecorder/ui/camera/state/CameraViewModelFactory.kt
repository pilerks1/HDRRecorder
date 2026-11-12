package com.pilerks1.hdrrecorder.ui.camera.state

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pilerks1.hdrrecorder.camera.camera2.CameraResultReader
import com.pilerks1.hdrrecorder.camera.camerax.CameraControlCoordinator
import com.pilerks1.hdrrecorder.camera.camerax.CameraXRecordingManager
import com.pilerks1.hdrrecorder.camera.camerax.CameraXSessionManager
import com.pilerks1.hdrrecorder.data.presets.PresetManager
import com.pilerks1.hdrrecorder.data.storage.AppPreferences
import com.pilerks1.hdrrecorder.data.storage.StorageManager
import com.pilerks1.hdrrecorder.data.telemetry.StatsManager
import com.pilerks1.hdrrecorder.data.telemetry.TelemetryCoordinator
import com.pilerks1.hdrrecorder.data.telemetry.TelemetryStore
import com.pilerks1.hdrrecorder.errorReporter
import com.pilerks1.hdrrecorder.ui.camera.CameraRuntime

/**
 * Constructs CameraViewModel with its managers injected. A plain ViewModelProvider.Factory
 * is used deliberately (no Hilt) to keep the dependency graph light for an app this size.
 * Because the ViewModel survives configuration changes, the camera/preview is not rebuilt
 * on rotation.
 */
internal class CameraViewModelFactory(
    private val application: Application,
    private val cameraRuntime: CameraRuntime
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CameraViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        val telemetryStore = TelemetryStore()
        val reporter = application.errorReporter
        val storageManager = StorageManager(application, reporter)
        val statsManager = StatsManager(application, telemetryStore, storageManager.recordings, reporter)
        val resultReader = CameraResultReader()
        val telemetryCoordinator = TelemetryCoordinator(telemetryStore, statsManager, resultReader)
        val controlCoordinator = CameraControlCoordinator(resultReader, reporter)
        val preferencesManager = AppPreferences(application)
        val cameraManager = CameraXSessionManager(application, telemetryCoordinator, controlCoordinator, reporter)
        val recordingManager = CameraXRecordingManager(application, statsManager, storageManager.recordings, reporter)

        @Suppress("UNCHECKED_CAST")
        return CameraViewModel(
            statsManager = statsManager,
            telemetryCoordinator = telemetryCoordinator,
            preferencesManager = preferencesManager,
            presetManager = PresetManager(storageManager.presets, reporter),
            cameraManager = cameraManager,
            recordingManager = recordingManager,
            recordingStorage = storageManager.recordings,
            controlCoordinator = controlCoordinator,
            cameraRuntime = cameraRuntime,
            reporter = reporter
        ) as T
    }
}
