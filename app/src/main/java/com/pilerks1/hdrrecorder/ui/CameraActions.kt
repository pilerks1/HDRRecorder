package com.pilerks1.hdrrecorder.ui

/**
 * Callback surface passed to control composables instead of the concrete CameraViewModel.
 * Keeps UI widgets decoupled from the ViewModel (no opt-in leakage, easier previews/testing).
 */
data class CameraActions(
    val onEvent: (CameraUiEvent) -> Unit,
    val onManualControlsChange: ((ManualControlsState) -> ManualControlsState) -> Unit
)
