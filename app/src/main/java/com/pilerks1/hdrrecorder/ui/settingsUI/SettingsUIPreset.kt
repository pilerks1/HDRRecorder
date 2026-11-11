package com.pilerks1.hdrrecorder.ui.settingsUI

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pilerks1.hdrrecorder.model.presetNameError

@Composable
fun PresetSection(
    currentPreset: String,
    presetsList: List<String>,
    onAddPreset: (String) -> Unit,
    onSavePreset: () -> Unit,
    saveRecordingOptions: Boolean,
    onSaveRecordingOptionsChange: (Boolean) -> Unit,
    saveStorageLocation: Boolean,
    onSaveStorageLocationChange: (Boolean) -> Unit,
    presetsEnabled: Boolean,
    presetBusy: Boolean,
    onRetryPresets: () -> Unit,
    onLoadPreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onDeleteAllPresets: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    // Dialog States
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteCurrentDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

    SectionTitle("Preset")

    val enabled = presetsEnabled && !presetBusy
    if (presetBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    if (!presetsEnabled && !presetBusy) {
        TextButton(onClick = onRetryPresets) { Text("Retry loading presets") }
    }

    // Current Preset Dropdown
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Current Preset", fontSize = 16.sp, color = Color.White)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showAddDialog = true },
                enabled = enabled,
                colors = presetButtonColors(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("Add")
            }
            SettingsChoiceDropdown(
                value = if (presetsList.isEmpty()) "No Presets" else currentPreset,
                choices = presetsList.map { it to it },
                enabled = enabled,
                onSelect = onLoadPreset
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Preset Action Buttons
    val actionsEnabled = enabled && presetsList.isNotEmpty()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PresetActionButton("Save", actionsEnabled, onSavePreset)
        PresetActionButton("Delete Current", actionsEnabled) { showDeleteCurrentDialog = true }
        PresetActionButton("Delete All", actionsEnabled) { showDeleteAllDialog = true }
    }

    Spacer(modifier = Modifier.height(16.dp))

    HdrSettingsSwitchRow(
        label = "Save Recording Options",
        description = "Include main camera controls, manual values, and zoom when saving this preset.",
        checked = saveRecordingOptions,
        enabled = enabled,
        onCheckedChange = onSaveRecordingOptionsChange
    )

    Spacer(modifier = Modifier.height(16.dp))

    HdrSettingsSwitchRow(
        label = "Save Storage Location",
        description = "Include the currently selected storage folder when saving this preset.",
        checked = saveStorageLocation,
        enabled = enabled,
        onCheckedChange = onSaveStorageLocationChange
    )

    // --- Dialogs ---

    if (showAddDialog) {
        val nameError = presetNameError(newPresetName, presetsList)
        val dismissAdd = {
            showAddDialog = false
            newPresetName = ""
            focusManager.clearFocus()
        }
        AlertDialog(
            onDismissRequest = dismissAdd,
            title = { Text("Add Preset", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    label = { Text("Preset Name", color = Color.LightGray) },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = { nameError?.let { Text(it) } },
                    colors = settingsTextFieldColors(unfocusedBorderColor = Color.Gray)
                )
            },
            confirmButton = {
                TextButton(enabled = enabled && nameError == null, onClick = {
                    onAddPreset(newPresetName.trim())
                    dismissAdd()
                }) {
                    DialogConfirmText()
                }
            },
            dismissButton = {
                TextButton(onClick = dismissAdd) { DialogCancelText() }
            },
            containerColor = Color.DarkGray
        )
    }

    if (showDeleteCurrentDialog) {
        ConfirmationDialog(
            title = "Delete Preset",
            message = "Are you sure you want to delete the preset '$currentPreset'?",
            onConfirm = { onDeletePreset(currentPreset) },
            onDismiss = { showDeleteCurrentDialog = false }
        )
    }

    if (showDeleteAllDialog) {
        ConfirmationDialog(
            title = "Delete All Presets",
            message = "Are you sure you want to delete ALL presets? This cannot be undone.",
            onConfirm = onDeleteAllPresets,
            onDismiss = { showDeleteAllDialog = false }
        )
    }
}

@Composable
private fun presetButtonColors() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun RowScope.PresetActionButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.weight(1f),
        colors = presetButtonColors(),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        Text(text, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun DialogConfirmText() {
    Text("OK", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
}

@Composable
private fun DialogCancelText() {
    Text("Cancel", color = Color.LightGray)
}

/** OK confirms and dismisses; Cancel or outside taps only dismiss. */
@Composable
private fun ConfirmationDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White) },
        text = { Text(message, color = Color.White) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                DialogConfirmText()
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { DialogCancelText() }
        },
        containerColor = Color.DarkGray
    )
}
