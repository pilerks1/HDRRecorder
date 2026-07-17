package com.pilerks1.hdrrecorder.ui

import com.pilerks1.hdrrecorder.model.CameraTelemetry
import com.pilerks1.hdrrecorder.model.StatsSnapshot
import kotlinx.coroutines.flow.StateFlow

/**
 * Callback surface passed to control composables instead of the concrete CameraViewModel.
 * Keeps UI widgets decoupled from the ViewModel (no opt-in leakage, easier previews/testing).
 */
data class CameraActions(
    val onEvent: (CameraUiEvent) -> Unit,
    val onManualControlsChange: ((ManualControlsState) -> ManualControlsState) -> Unit,
    val stats: StateFlow<StatsSnapshot>,
    val cameraTelemetry: StateFlow<CameraTelemetry>
)
