package com.pilerks1.hdrrecorder.data.camera

import android.content.Intent
import android.util.Range
import android.util.Rational
import com.pilerks1.hdrrecorder.ui.CameraUiEvent
import com.pilerks1.hdrrecorder.ui.CameraUiState
import com.pilerks1.hdrrecorder.data.camera.core.CameraZoomState
import com.pilerks1.hdrrecorder.data.camera.core.ZoomShortcut
import com.pilerks1.hdrrecorder.data.camera.core.DefaultCameraInventory
import com.pilerks1.hdrrecorder.data.camera.core.ZoomMapping

/** Selects the optional UI testbed for debuggable builds. */
internal object CameraRuntimeProvider {
    private const val UI_TESTBED_EXTRA = "ui_testbed"

    fun fromIntent(intent: Intent): CameraRuntime =
        if (intent.getBooleanExtra(UI_TESTBED_EXTRA, false)) {
            UiTestbedCameraRuntime()
        } else {
            RealCameraRuntime
        }
}

/**
 * No-camera runtime used to exercise the real UI with simulated capabilities.
 */
private class UiTestbedCameraRuntime : CameraRuntime {
    override val usesCameraHardware: Boolean = false

    override fun initialUiState(): CameraUiState = newTestbedState()

    override fun handleEvent(
        event: CameraUiEvent,
        currentState: CameraUiState
    ): CameraRuntimeEventResult {
        val replacement = when (event) {
            is CameraUiEvent.SetLinearZoom -> currentState.copy(zoomState = currentState.zoomState.let {
                it.copy(ratio = ZoomMapping.ratio(event.progress, it.minRatio, it.maxRatio))
            })
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
        isCameraHardwareEnabled = false,
        previewPlaceholderLabel = "UI TESTBED — NO CAMERA",
        cameraCapabilities = allControlsCapabilities,
        zoomState = testbedZoomState
    )

    companion object {
        private val testbedZoomState: CameraZoomState = run {
            val factors = listOf(0.5f, 1f, 3f)
            val lenses = factors.map { factor ->
                ZoomShortcut("testbed-$factor", factor)
            }
            CameraZoomState(DefaultCameraInventory("testbed-main", lenses.map { it.cameraId }.toSet(), lenses), 1f, 0.5f, 10f, true)
        }

        private val allControlsCapabilities = CameraCapabilities(
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
            supportsManualSensor = true,
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
}
