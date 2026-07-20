package com.pilerks1.hdrrecorder.ui.manualcontrols

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import com.pilerks1.hdrrecorder.ui.layout.AxisSpec

/**
 * Keeps a horizontally-authored slider's measured, drawn, and hit-test bounds identical
 * after it is rotated into the vertical camera-control axis.
 */
fun Modifier.rotateForAxis(axis: AxisSpec): Modifier {
    if (!axis.usesVerticalTrack) return this

    return layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth
            )
        )
        layout(placeable.height, placeable.width) {
            placeable.placeWithLayer(
                x = (placeable.height - placeable.width) / 2,
                y = (placeable.width - placeable.height) / 2
            ) {
                rotationZ = 270f
            }
        }
    }
}
