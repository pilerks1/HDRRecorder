package com.pilerks1.hdrrecorder.ui.settingsUI

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Maximum text width per line, excluding dropdown padding. */
val SettingsDropdownTextMaxWidth = 96.dp

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
    isCompatible: (T) -> Boolean = { true },
    textMaxWidth: Dp = SettingsDropdownTextMaxWidth
) {
    val available = enabled && choices.isNotEmpty()
    var expanded by remember(available, choices) { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scrollbarColor = MaterialTheme.colorScheme.onSurface
    Box(modifier) {
        Button(
            onClick = { expanded = true },
            enabled = available,
            modifier = if (value.contains(' ')) Modifier.widthIn(max = textMaxWidth + 24.dp) else Modifier,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledContainerColor = Color.DarkGray,
                disabledContentColor = Color.Gray
            )
        ) {
            Text(value, modifier = if (value.contains(' ')) Modifier.widthIn(max = textMaxWidth) else Modifier,
                fontSize = 14.sp, lineHeight = 16.sp, textAlign = TextAlign.Center,
                softWrap = value.contains(' '), overflow = TextOverflow.Clip)
        }
        DropdownMenu(
            expanded = expanded && available,
            onDismissRequest = { expanded = false },
            scrollState = scrollState,
            modifier = Modifier.heightIn(max = 300.dp).drawWithContent {
                drawContent()
                if (scrollState.maxValue > 0) {
                    val inset = 8.dp.toPx()
                    val trackHeight = (size.height - 2 * inset).coerceAtLeast(0f)
                    val viewportHeight = scrollState.viewportSize.toFloat()
                    val thumbHeight = (trackHeight * viewportHeight / (viewportHeight + scrollState.maxValue))
                        .coerceIn(18.dp.toPx().coerceAtMost(trackHeight), trackHeight)
                    val thumbTop = inset + (trackHeight - thumbHeight) * scrollState.value / scrollState.maxValue
                    val width = 2.dp.toPx()
                    val left = size.width - 4.dp.toPx() - width
                    drawRoundRect(scrollbarColor.copy(alpha = 0.15f), Offset(left, inset),
                        Size(width, trackHeight), CornerRadius(width))
                    drawRoundRect(scrollbarColor.copy(alpha = 0.65f), Offset(left, thumbTop),
                        Size(width, thumbHeight), CornerRadius(width))
                }
            }
        ) {
            choices.forEach { (choice, name) ->
                DropdownMenuItem(
                    text = {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(name, modifier = if (name.contains(' ')) Modifier.widthIn(max = textMaxWidth) else Modifier,
                                color = if (isCompatible(choice)) LocalContentColor.current else Color.Gray,
                                textAlign = TextAlign.Center, lineHeight = 18.sp,
                                softWrap = name.contains(' '), overflow = TextOverflow.Clip)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp),
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
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
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
            enabled = enabled,
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
