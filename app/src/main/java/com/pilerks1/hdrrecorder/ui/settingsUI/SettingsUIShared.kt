package com.pilerks1.hdrrecorder.ui.settingsUI

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Settings",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White
            )
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.DarkGray)
}

@Composable
fun <T> SettingsChoiceDropdown(
    value: String,
    choices: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isCompatible: (T) -> Boolean = { true }
) {
    val available = enabled && choices.isNotEmpty()
    var expanded by remember(available, choices) { mutableStateOf(false) }
    Box(modifier) {
        Button(
            onClick = { expanded = true },
            enabled = available,
            modifier = Modifier.widthIn(max = 112.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledContainerColor = Color.DarkGray,
                disabledContentColor = Color.Gray
            )
        ) {
            Text(value.replace(' ', '\n'), fontSize = 14.sp, lineHeight = 16.sp,
                textAlign = TextAlign.Center)
        }
        DropdownMenu(
            expanded = expanded && available,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 300.dp)
        ) {
            choices.forEach { (choice, name) ->
                DropdownMenuItem(
                    text = {
                        Text(name.replace(' ', '\n'), modifier = Modifier.fillMaxWidth(),
                            color = if (isCompatible(choice)) LocalContentColor.current else Color.Gray,
                            textAlign = TextAlign.Center, lineHeight = 18.sp)
                    },
                    onClick = {
                        expanded = false
                        onSelect(choice)
                    }
                )
            }
        }
    }
}

@Composable
fun HdrSettingsSwitchRow(
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 16.sp, color = Color.White)
            if (description != null) {
                Text(text = description, fontSize = 12.sp, lineHeight = 12.sp, color = Color.Gray)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                uncheckedThumbColor = Color.LightGray,
                uncheckedTrackColor = Color.DarkGray,
                uncheckedBorderColor = Color.LightGray
            )
        )
    }
}
