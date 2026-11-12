package com.pilerks1.hdrrecorder.ui.camera.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
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
import com.pilerks1.hdrrecorder.ui.style.SettingsDimensions
import com.pilerks1.hdrrecorder.ui.style.UiTextStyles

/** Maximum text width per line, excluding dropdown padding. */

@Composable
fun SettingsHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Settings",
            fontSize = UiTextStyles.Settings.headerSize,
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
    Spacer(modifier = Modifier.height(SettingsDimensions.sectionSpacing))
}

/** Primary-colored title shown above each settings section. */
@Composable
fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = UiTextStyles.Settings.sectionTitleSize,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = SettingsDimensions.sectionTitleBottomPadding)
    )
}

@Composable
fun settingsSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.primary,
    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
    uncheckedThumbColor = Color.LightGray,
    uncheckedTrackColor = Color.DarkGray,
    uncheckedBorderColor = Color.LightGray
)

@Composable
fun settingsTextFieldColors(unfocusedBorderColor: Color = Color.DarkGray): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = unfocusedBorderColor,
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        cursorColor = MaterialTheme.colorScheme.primary
    )

@Composable
fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(vertical = SettingsDimensions.dividerVerticalPadding), color = Color.DarkGray)
}

@Composable
fun <T> SettingsChoiceDropdown(
    value: String,
    choices: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isCompatible: (T) -> Boolean = { true },
    textMaxWidth: Dp = SettingsDimensions.dropdownMaxWidth
) {
    val available = enabled && choices.isNotEmpty()
    var expanded by remember(available, choices) { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scrollbarColor = MaterialTheme.colorScheme.onSurface
    Box(modifier) {
        Button(
            onClick = { expanded = true },
            enabled = available,
            modifier = if (value.contains(' ')) Modifier.widthIn(max = textMaxWidth + SettingsDimensions.dropdownExtraWidth) else Modifier,
            contentPadding = PaddingValues(horizontal = SettingsDimensions.dropdownHorizontalPadding, vertical = SettingsDimensions.dropdownVerticalPadding),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledContainerColor = Color.DarkGray,
                disabledContentColor = Color.Gray
            )
        ) {
            Text(value, modifier = if (value.contains(' ')) Modifier.widthIn(max = textMaxWidth) else Modifier,
                fontSize = UiTextStyles.Settings.choiceLabelSize, lineHeight = UiTextStyles.Settings.choiceLineHeight, textAlign = TextAlign.Center,
                softWrap = value.contains(' '), overflow = TextOverflow.Clip)
        }
        DropdownMenu(
            expanded = expanded && available,
            onDismissRequest = { expanded = false },
            scrollState = scrollState,
            modifier = Modifier.heightIn(max = SettingsDimensions.dropdownMaxHeight).drawWithContent {
                drawContent()
                if (scrollState.maxValue > 0) {
                    val inset = SettingsDimensions.scrollbarInset.toPx()
                    val trackHeight = (size.height - 2 * inset).coerceAtLeast(0f)
                    val viewportHeight = scrollState.viewportSize.toFloat()
                    val thumbHeight = (trackHeight * viewportHeight / (viewportHeight + scrollState.maxValue))
                        .coerceIn(SettingsDimensions.scrollbarMinThumbHeight.toPx().coerceAtMost(trackHeight), trackHeight)
                    val thumbTop = inset + (trackHeight - thumbHeight) * scrollState.value / scrollState.maxValue
                    val width = SettingsDimensions.scrollbarWidth.toPx()
                    val left = size.width - SettingsDimensions.scrollbarEdgeGap.toPx() - width
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
                                textAlign = TextAlign.Center, lineHeight = UiTextStyles.Settings.menuLineHeight,
                                softWrap = name.contains(' '), overflow = TextOverflow.Clip)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = SettingsDimensions.dropdownHorizontalPadding),
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
            Text(text = label, fontSize = UiTextStyles.Settings.rowTitleSize, color = Color.White)
            if (description != null) {
                Text(text = description, fontSize = UiTextStyles.Settings.descriptionSize, lineHeight = UiTextStyles.Settings.descriptionLineHeight, color = Color.Gray)
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = settingsSwitchColors()
        )
    }
}
