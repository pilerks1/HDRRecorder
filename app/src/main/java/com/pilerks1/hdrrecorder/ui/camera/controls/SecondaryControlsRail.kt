package com.pilerks1.hdrrecorder.ui.camera.controls

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import com.pilerks1.hdrrecorder.ui.camera.layout.ActionRailSpec
import com.pilerks1.hdrrecorder.ui.camera.layout.AxisSpec
import com.pilerks1.hdrrecorder.ui.style.UiTextStyles
import kotlin.math.roundToInt

@Composable
fun SecondaryControlsRail(
    panels: List<CameraControlPanel>,
    activePanel: CameraControlPanel?,
    axis: AxisSpec,
    railSpec: ActionRailSpec,
    onTogglePanel: (CameraControlPanel) -> Unit,
    modifier: Modifier = Modifier
) {
    Layout(
        modifier = modifier,
        content = {
            panels.forEach { panel ->
                ControlPanelTab(
                    label = panel.gridLabel,
                    isActive = activePanel == panel,
                    onClick = { onTogglePanel(panel) }
                )
            }
        }
    ) { measurables, constraints ->
        val buttonSize = railSpec.buttonSize.roundToInt().coerceAtLeast(1)
        val placeables = measurables.map { measurable ->
            measurable.measure(androidx.compose.ui.unit.Constraints.fixed(buttonSize, buttonSize))
        }
        val vertical = axis.usesVerticalTrack
        val availablePrimary = if (vertical) constraints.maxHeight else constraints.maxWidth
        val primaryOrigins = evenlySpacedOrigins(
            availableLength = availablePrimary,
            itemSize = buttonSize,
            itemCount = placeables.size
        )
        val crossOrigin = if (vertical) {
            ((constraints.maxWidth - buttonSize) / 2).coerceAtLeast(0)
        } else {
            ((constraints.maxHeight - buttonSize) / 2).coerceAtLeast(0)
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                val x = if (vertical) {
                    crossOrigin
                } else {
                    primaryOrigins[index]
                }
                val y = if (vertical) {
                    primaryOrigins[index]
                } else {
                    crossOrigin
                }
                placeable.place(x, y)
            }
        }
    }
}

private fun evenlySpacedOrigins(
    availableLength: Int,
    itemSize: Int,
    itemCount: Int
): List<Int> {
    if (itemCount <= 0) return emptyList()

    val freeSpace = (availableLength - itemSize * itemCount).coerceAtLeast(0)
    val gap = freeSpace.toFloat() / (itemCount + 1)
    return List(itemCount) { index ->
        (gap + index * (itemSize + gap)).roundToInt()
    }
}

/** A panel tab: exposes selection to accessibility services. */
@Composable
private fun ControlPanelTab(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .selectable(
                selected = isActive,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
    ) {
        Text(
            text = label,
            color = if (isActive) Color.Yellow else Color.White,
            fontSize = UiTextStyles.Camera.controlTabSize,
            fontWeight = FontWeight.Bold
        )
    }
}
