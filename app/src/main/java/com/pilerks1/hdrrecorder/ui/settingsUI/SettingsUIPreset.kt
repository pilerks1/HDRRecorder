package com.pilerks1.hdrrecorder.ui.settingsUI

import androidx.compose.foundation.background
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
fun PresetSection() {
    val focusManager = LocalFocusManager.current
    val mockPresets = remember { mutableStateListOf("Default", "Cinematic", "Low Light") }
    var currentPreset by remember { mutableStateOf(mockPresets.firstOrNull() ?: "None") }
    var dropdownExpanded by remember { mutableStateOf(false) }

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

    // Current Preset Dropdown
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Current Preset", fontSize = 16.sp, color = Color.White)
        Box {
            Button(
                onClick = { dropdownExpanded = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(text = currentPreset)
            }
            DropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false },
                modifier = Modifier
                    .background(Color.DarkGray)
                    .heightIn(max = 300.dp)
            ) {
                if (mockPresets.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No Presets", color = Color.Gray) },
                        onClick = { dropdownExpanded = false }
                    )
                } else {
                    mockPresets.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset, color = Color.White) },
                            onClick = {
                                currentPreset = preset
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Preset Action Buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            Text("Add", fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1)
        }
        Button(
            onClick = { if (mockPresets.isNotEmpty()) showDeleteCurrentDialog = true },
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
            onClick = { if (mockPresets.isNotEmpty()) showDeleteAllDialog = true },
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

    // --- Dialogs ---

    if (showAddDialog) {
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
                TextButton(onClick = {
                    if (newPresetName.isNotBlank()) {
                        mockPresets.add(newPresetName)
                        currentPreset = newPresetName
                    }
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
                    mockPresets.remove(currentPreset)
                    currentPreset = mockPresets.firstOrNull() ?: "None"
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
                    mockPresets.clear()
                    currentPreset = "None"
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