package com.pilerks1.hdrrecorder.data.camera

import android.content.Intent
import android.util.Range
import android.util.Rational
import com.pilerks1.hdrrecorder.ui.CameraUiEvent
import com.pilerks1.hdrrecorder.ui.CameraUiState

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
 * Ephemeral, no-camera runtime used to exercise the real UI with simulated capabilities.
 */
private class UiTestbedCameraRuntime : CameraRuntime {
    override val usesCameraHardware: Boolean = false

    private val presets = linkedMapOf<String, CameraUiState>()

    override fun initialUiState(): CameraUiState {
        val initialState = newTestbedState()
        presets["Default"] = initialState
        return initialState
    }

    override fun handleEvent(
        event: CameraUiEvent,
        currentState: CameraUiState
    ): CameraRuntimeEventResult {
        val replacement = when (event) {
            is CameraUiEvent.SavePreset -> savePreset(event.name, currentState)
            is CameraUiEvent.LoadPreset -> loadPreset(event.name, currentState)
            is CameraUiEvent.DeletePreset -> deletePreset(event.name, currentState)
            is CameraUiEvent.DeleteAllPresets -> resetPresets(currentState)
            is CameraUiEvent.SetStorageUri -> currentState.copy(storageUri = event.uri)
            is CameraUiEvent.TapToMeter,
            is CameraUiEvent.ToggleRecording,
            is CameraUiEvent.TogglePause -> null
            else -> return CameraRuntimeEventResult.NotHandled
        }
        return CameraRuntimeEventResult.Handled(replacement)
    }

    private fun savePreset(name: String, currentState: CameraUiState): CameraUiState {
        presets[name] = currentState.copy(
            currentPresetName = name,
            isSettingsSheetVisible = false
        )
        return currentState.copy(
            presetsList = presets.keys.toList(),
            currentPresetName = name
        )
    }

    private fun loadPreset(name: String, currentState: CameraUiState): CameraUiState =
        presets[name]?.copy(
            currentPresetName = name,
            presetsList = presets.keys.toList(),
            isSettingsSheetVisible = currentState.isSettingsSheetVisible
        ) ?: currentState

    private fun deletePreset(name: String, currentState: CameraUiState): CameraUiState {
        presets.remove(name)
        if (presets.isEmpty()) presets["Default"] = newTestbedState()

        if (currentState.currentPresetName != name) {
            return currentState.copy(presetsList = presets.keys.toList())
        }

        val nextName = presets.keys.first()
        return presets.getValue(nextName).copy(
            currentPresetName = nextName,
            presetsList = presets.keys.toList(),
            isSettingsSheetVisible = currentState.isSettingsSheetVisible
        )
    }

    private fun resetPresets(currentState: CameraUiState): CameraUiState {
        val defaultState = newTestbedState()
        presets.clear()
        presets["Default"] = defaultState
        return defaultState.copy(
            isSettingsSheetVisible = currentState.isSettingsSheetVisible
        )
    }

    private fun newTestbedState(): CameraUiState = CameraUiState(
        isCameraHardwareEnabled = false,
        previewPlaceholderLabel = "UI TESTBED — NO CAMERA",
        cameraCapabilities = allControlsCapabilities
    )

    companion object {
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
