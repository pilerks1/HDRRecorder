package com.pilerks1.hdrrecorder.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Places children in the control axis without separate Row/Column composition branches. */
@Composable
fun AxisStack(
    axis: AxisSpec,
    modifier: Modifier = Modifier,
    spacing: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val spacingPx = spacing.roundToPx()
        val vertical = axis.usesVerticalTrack
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(childConstraints) }
        val contentPrimary = placeables.sumOf { if (vertical) it.height else it.width } +
            spacingPx * (placeables.size - 1).coerceAtLeast(0)
        val contentCross = placeables.maxOfOrNull { if (vertical) it.width else it.height } ?: 0
        val width = if (vertical) contentCross.coerceIn(constraints.minWidth, constraints.maxWidth) else contentPrimary.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = if (vertical) contentPrimary.coerceIn(constraints.minHeight, constraints.maxHeight) else contentCross.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            var primary = 0
            placeables.forEach { placeable ->
                if (vertical) {
                    placeable.place((width - placeable.width) / 2, primary)
                    primary += placeable.height + spacingPx
                } else {
                    placeable.place(primary, (height - placeable.height) / 2)
                    primary += placeable.width + spacingPx
                }
            }
        }
    }
}

/** Places actions over a full-size axis surface so its visual and gesture bounds stay identical. */
@Composable
fun AxisOverlay(
    axis: AxisSpec,
    overlay: @Composable () -> Unit,
    content: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    edgePadding: Dp = 8.dp
) {
    Layout(
        content = {
            content(Modifier)
            overlay()
        },
        modifier = modifier
    ) { measurables, constraints ->
        require(measurables.size == 2)
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val main = measurables[0].measure(Constraints.fixed(width, height))
        val actions = measurables[1].measure(Constraints(0, width, 0, height))
        val paddingPx = edgePadding.roundToPx()

        layout(width, height) {
            main.place(0, 0)
            if (axis.usesVerticalTrack) {
                actions.place((width - actions.width) / 2, paddingPx)
            } else {
                actions.place(paddingPx, (height - actions.height) / 2)
            }
        }
    }
}
