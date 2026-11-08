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
    presetError: String?,
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

    Text(
        text = "Preset",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    val enabled = presetsEnabled && !presetBusy
    if (presetBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    presetError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onSavePreset,
            enabled = enabled && presetsList.isNotEmpty(),
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            Text("Save", fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1)
        }
        Button(
            onClick = { showDeleteCurrentDialog = true },
            enabled = enabled && presetsList.isNotEmpty(),
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            Text("Delete Current", fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1)
        }
        Button(
            onClick = { showDeleteAllDialog = true },
            enabled = enabled && presetsList.isNotEmpty(),
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            Text("Delete All", fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1)
        }
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
        val nameError = when {
            newPresetName.isBlank() -> "Enter a preset name"
            presetsList.any { it.equals(newPresetName.trim(), ignoreCase = true) } -> "A preset with that name already exists"
            else -> null
        }
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                newPresetName = ""
                focusManager.clearFocus()
            },
            title = { Text("Add Preset", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    label = { Text("Preset Name", color = Color.LightGray) },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = { nameError?.let { Text(it) } },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            confirmButton = {
                TextButton(enabled = enabled && nameError == null, onClick = {
                    onAddPreset(newPresetName.trim())
                    showAddDialog = false
                    newPresetName = ""
                    focusManager.clearFocus()
                }) {
                    Text("OK", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    newPresetName = ""
                    focusManager.clearFocus()
                }) {
                    Text("Cancel", color = Color.LightGray)
                }
            },
            containerColor = Color.DarkGray
        )
    }

    if (showDeleteCurrentDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteCurrentDialog = false },
            title = { Text("Delete Preset", color = Color.White) },
            text = { Text("Are you sure you want to delete the preset '$currentPreset'?", color = Color.White) },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePreset(currentPreset)
                    showDeleteCurrentDialog = false
                }) {
                    Text("OK", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCurrentDialog = false }) {
                    Text("Cancel", color = Color.LightGray)
                }
            },
            containerColor = Color.DarkGray
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete All Presets", color = Color.White) },
            text = { Text("Are you sure you want to delete ALL presets? This cannot be undone.", color = Color.White) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteAllPresets()
                    showDeleteAllDialog = false
                }) {
                    Text("OK", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel", color = Color.LightGray)
                }
            },
            containerColor = Color.DarkGray
        )
    }
}
