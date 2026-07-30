package com.pilerks1.hdrrecorder.ui.viewmodels

import android.app.Application
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pilerks1.hdrrecorder.data.CameraManager
import com.pilerks1.hdrrecorder.data.PreferencesManager
import com.pilerks1.hdrrecorder.data.RecordingManager
import com.pilerks1.hdrrecorder.data.StatsManager
import com.pilerks1.hdrrecorder.data.TelemetryStore
import com.pilerks1.hdrrecorder.data.camera.CameraRuntime
import com.pilerks1.hdrrecorder.data.camera.TelemetryCoordinator

/**
 * Constructs CameraViewModel with its managers injected. A plain ViewModelProvider.Factory
 * is used deliberately (no Hilt) to keep the dependency graph light for an app this size.
 * Because the ViewModel survives configuration changes, the camera/preview is not rebuilt
 * on rotation.
 */
@ExperimentalCamera2Interop
internal class CameraViewModelFactory(
    private val application: Application,
    private val cameraRuntime: CameraRuntime
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CameraViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        val telemetryStore = TelemetryStore()
        val statsManager = StatsManager(application, telemetryStore)
        val telemetryCoordinator = TelemetryCoordinator(telemetryStore, statsManager)
        val preferencesManager = PreferencesManager(application)
        val cameraManager = CameraManager(application, telemetryCoordinator)
        val recordingManager = RecordingManager(application, statsManager)

        @Suppress("UNCHECKED_CAST")
        return CameraViewModel(
            application = application,
            statsManager = statsManager,
            telemetryCoordinator = telemetryCoordinator,
            preferencesManager = preferencesManager,
            cameraManager = cameraManager,
            recordingManager = recordingManager,
            cameraRuntime = cameraRuntime
        ) as T
    }
}
