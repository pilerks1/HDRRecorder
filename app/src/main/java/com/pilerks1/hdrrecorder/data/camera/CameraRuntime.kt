package com.pilerks1.hdrrecorder.data.camera

import com.pilerks1.hdrrecorder.ui.CameraUiEvent
import com.pilerks1.hdrrecorder.ui.CameraUiState

/**
 * Build-specific camera behavior exposed to the shared camera screen.
 *
 * Debug builds may supply a no-hardware runtime. Release builds only supply [RealCameraRuntime].
 */
internal interface CameraRuntime {
    val usesCameraHardware: Boolean

    /** Returns an alternate initial state, or null to load normal persisted camera state. */
    fun initialUiState(): CameraUiState? = null

    /**
     * Handles runtime-specific events before the shared ViewModel processes them.
     *
     * A handled event may return the replacement state, or null when it should be ignored.
     */
    fun handleEvent(
        event: CameraUiEvent,
        currentState: CameraUiState
    ): CameraRuntimeEventResult = CameraRuntimeEventResult.NotHandled
}

internal object RealCameraRuntime : CameraRuntime {
    override val usesCameraHardware: Boolean = true
}

internal sealed interface CameraRuntimeEventResult {
    data object NotHandled : CameraRuntimeEventResult
    data class Handled(val uiState: CameraUiState?) : CameraRuntimeEventResult
}
