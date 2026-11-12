package com.pilerks1.hdrrecorder.ui.camera

import android.util.Range
import android.util.Rational
import com.pilerks1.hdrrecorder.camera.model.CameraCapabilities
import com.pilerks1.hdrrecorder.camera.model.CameraZoomState
import com.pilerks1.hdrrecorder.camera.model.DefaultCameraInventory
import com.pilerks1.hdrrecorder.camera.model.ZoomShortcut
import com.pilerks1.hdrrecorder.ui.camera.state.CameraUiEvent
import com.pilerks1.hdrrecorder.ui.camera.state.CameraUiState

/** Simulated capabilities and lenses for the debug UI testbed. */
private object UiTestbedCameraData {

    val testbedZoomState: CameraZoomState = run {
        val factors = listOf(0.5f, 1f, 3f)
        val lenses = factors.map { factor ->
            ZoomShortcut("testbed-$factor", factor)
        }
        CameraZoomState(DefaultCameraInventory("testbed-main", lenses), 1f, 0.5f, 10f, true)
    }

    val allControlsCapabilities = CameraCapabilities(
        isoRange = Range(50, 12_800),
        postRawSensitivityBoostRange = Range(100, 400),
        ssRangeNanos = Range(100_000L, 30_000_000_000L),
        evRange = Range(-12, 12),
        evStep = Rational(1, 3),
        focusMinDistanceDiopters = 10f,
        fpsRanges = listOf(
            Range(15, 30),
            Range(24, 24),
            Range(30, 30),
            Range(60, 60),
            Range(120, 120)
        ),
        maxFrameDurationNanos = 30_000_000_000L,
        supportsFullManualExposure = true,
        hasManualIsoControl = true,
        hasManualShutterControl = true,
        hasExposureCompensationControl = true,
        hasManualFocusControl = true,
        supportsCCT = true,
        cctTemperatureRange = Range(2_000, 10_000),
        supportsShutterPriorityAe = true,
        supportsIsoPriorityAe = true,
        supportsAeLock = true,
        supportsPostRawSensitivityBoost = true,
        supportsLowLightBoost = true
    )
}

/**
 * No-camera runtime used to exercise the real UI with simulated capabilities.
 */
internal class UiTestbedCameraRuntime : CameraRuntime {
    override val usesCameraHardware: Boolean = false

    override fun initialUiState(): CameraUiState = newTestbedState()

    override fun handleEvent(
        event: CameraUiEvent,
        currentState: CameraUiState
    ): CameraRuntimeEventResult {
        val replacement = when (event) {
            is CameraUiEvent.SelectZoomLens -> currentState.zoomState.shortcuts
                .firstOrNull { it.cameraId == event.cameraId }?.zoomRatio
                ?.let { currentState.copy(zoomState = currentState.zoomState.copy(ratio = it)) }
            is CameraUiEvent.SetZoomRatio -> currentState.copy(zoomState = currentState.zoomState.let {
                it.copy(ratio = event.ratio.coerceIn(it.minRatio, it.maxRatio))
            })
            is CameraUiEvent.StartMetering,
            is CameraUiEvent.RemapMetering,
            is CameraUiEvent.CancelMetering,
            is CameraUiEvent.ToggleRecording,
            is CameraUiEvent.TogglePause -> null
            else -> return CameraRuntimeEventResult.NotHandled
        }
        return CameraRuntimeEventResult.Handled(replacement)
    }

    private fun newTestbedState(): CameraUiState = CameraUiState(
        previewPlaceholderLabel = "UI TESTBED — NO CAMERA",
        cameraCapabilities = UiTestbedCameraData.allControlsCapabilities,
        zoomState = UiTestbedCameraData.testbedZoomState
    )
}
