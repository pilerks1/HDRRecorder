package com.pilerks1.hdrrecorder.ui.camera.state

import com.pilerks1.hdrrecorder.camera.camerax.CameraControlCoordinator
import com.pilerks1.hdrrecorder.camera.model.RecordingAspectRatio
import com.pilerks1.hdrrecorder.camera.model.Resolution
import com.pilerks1.hdrrecorder.data.diagnostics.ErrorReporter
import com.pilerks1.hdrrecorder.data.diagnostics.IssueCode
import com.pilerks1.hdrrecorder.data.presets.CameraPreset
import com.pilerks1.hdrrecorder.data.presets.PresetManager
import com.pilerks1.hdrrecorder.data.presets.PresetSettings
import com.pilerks1.hdrrecorder.data.presets.presetNameError
import com.pilerks1.hdrrecorder.data.storage.AppPreferences
import com.pilerks1.hdrrecorder.data.storage.RecordingStorageManager
import com.pilerks1.hdrrecorder.data.telemetry.StatsManager
import com.pilerks1.hdrrecorder.ui.camera.CameraRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Coordinates persisted presets using the existing screen busy gate. */
internal class CameraPresetController(
    private val uiState: MutableStateFlow<CameraUiState>,
    private val scope: CoroutineScope,
    private val presetManager: PresetManager,
    private val preferencesManager: AppPreferences,
    private val recordingStorage: RecordingStorageManager,
    private val statsManager: StatsManager,
    private val controlCoordinator: CameraControlCoordinator,
    private val cameraRuntime: CameraRuntime,
    private val reporter: ErrorReporter,
    private val isRecordingBusy: () -> Boolean,
    private val requestRebind: () -> Unit,
    private val bindImmediately: () -> Unit,
    private val cancelExposureTransition: () -> Unit,
    private val normalizeManualControls: () -> Unit
) {
    private var presetDocuments: List<CameraPreset> = emptyList()

    fun onEvent(event: CameraUiEvent) {
        when (event) {
            // All document operations share a single busy gate, including recording startup.
            CameraUiEvent.RetryPresets -> if (!uiState.value.arePresetsInitialized) initializePresets()
            is CameraUiEvent.AddPreset -> runPresetOperation {
                val name = event.name.trim()
                presetNameError(name, presetDocuments.map { it.name })?.let { throw IllegalArgumentException(it) }
                val preset = uiState.value.toPreset(PresetManager.newId(), name)
                presetManager.save(preset, create = true)
                refreshPresets()
                selectSavedPreset(preset)
            }
            CameraUiEvent.SavePreset -> runPresetOperation {
                val state = uiState.value
                val preset = state.toPreset(requireNotNull(state.currentPresetId), state.currentPresetName)
                presetManager.save(preset)
                refreshPresets()
                selectSavedPreset(preset)
            }
            is CameraUiEvent.LoadPreset -> runPresetOperation {
                val id = presetDocuments.first { it.name == event.name }.id
                val preset = presetManager.read(id)
                applyPreset(preset)
                selectSavedPreset(preset)
                requestRebind()
            }
            is CameraUiEvent.DeletePreset -> runPresetOperation {
                val id = presetDocuments.first { it.name == event.name }.id
                presetManager.delete(id)
                refreshPresets()
                selectAfterDeletion()
            }
            CameraUiEvent.DeleteAllPresets -> runPresetOperation {
                for (preset in presetDocuments) presetManager.delete(preset.id)
                refreshPresets()
                selectAfterDeletion()
            }
            is CameraUiEvent.SetSaveRecordingOptions -> uiState.update { it.copy(saveRecordingOptions = event.enabled) }
            is CameraUiEvent.SetSaveStorageLocation -> uiState.update { it.copy(saveStorageLocation = event.enabled) }

            else -> Unit
        }
    }

    private fun runPresetOperation(operation: suspend () -> Unit) {
        if (uiState.value.isPresetBusy || isRecordingBusy()) return
        uiState.update { it.copy(isPresetBusy = true) }
        scope.launch {
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Refresh after partial operations; retain secondary failures on the original report.
                // A bulk deletion may have removed some files before failing.
                try {
                    refreshPresets()
                    if (uiState.value.arePresetsInitialized) selectAfterDeletion()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (refreshError: Exception) {
                    error.addSuppressed(refreshError)
                }
                reporter.report(IssueCode.PRESET_OPERATION, "presetOperation", error)
            } finally {
                uiState.update { it.copy(isPresetBusy = false) }
            }
        }
    }

    fun initializePresets() {
        runPresetOperation {
            refreshPresets()
            if (presetDocuments.isEmpty()) createDefaultPreset()
            val preset = presetDocuments.firstOrNull { it.id == preferencesManager.selectedPresetId }
                ?: presetDocuments.first()
            applyPreset(preset)
            selectSavedPreset(preset)
            uiState.update { it.copy(arePresetsInitialized = true) }
            bindImmediately()
        }
    }

    private suspend fun refreshPresets() {
        presetDocuments = presetManager.list()
        uiState.update { it.copy(presetsList = presetDocuments.map { preset -> preset.name }) }
    }

    private fun selectSavedPreset(preset: CameraPreset) {
        preferencesManager.selectedPresetId = preset.id
        uiState.update { it.copy(currentPresetId = preset.id, currentPresetName = preset.name) }
    }

    private suspend fun createDefaultPreset(): CameraPreset {
        val preset = CameraPreset(PresetManager.newId(), "Default", PresetSettings())
        presetManager.save(preset, create = true)
        refreshPresets()
        return preset
    }

    private suspend fun selectAfterDeletion() {
        if (presetDocuments.any { it.id == uiState.value.currentPresetId }) return
        val reset = presetDocuments.isEmpty()
        val preset = if (reset) createDefaultPreset() else
            presetDocuments.firstOrNull { it.name == "Default" } ?: presetDocuments.first()
        if (reset) {
            uiState.update { it.copy(
                selectedFps = 30,
                selectedResolution = Resolution.FHD,
                selectedAspectRatio = RecordingAspectRatio.FOUR_THREE,
                manualControlsState = ManualControlsState(),
                zoomState = it.zoomState.copy(ratio = 1f)
            ) }
        }
        applyPreset(preset)
        selectSavedPreset(preset)
        requestRebind()
    }

    private suspend fun applyPreset(preset: CameraPreset) {
        var destination = uiState.value.storageUri
        if (preset.saveStorageLocation) {
            val saved = preset.storageUri
            val usable = saved?.let { recordingStorage.checkPresetDestination(it) } ?: Result.success(true)
            if (usable.getOrDefault(false)) destination = saved
            else reporter.report(IssueCode.PRESET_FOLDER, "applyPreset", usable.exceptionOrNull(),
                mapOf("recovery" to "current folder kept"))
        }
        cancelExposureTransition()
        controlCoordinator.invalidateLowLightBoostRequest()
        uiState.update { it.withPreset(preset).copy(storageUri = destination, isExposureTransitioning = false) }
        preferencesManager.storageUri = destination
        statsManager.updateStorageUri(destination)
        normalizeManualControls()
        if (!cameraRuntime.usesCameraHardware) {
            uiState.update { it.copy(zoomState = it.zoomState.let { zoom ->
                zoom.copy(ratio = zoom.ratio.coerceIn(zoom.minRatio, zoom.maxRatio))
            }) }
        }
    }
}
