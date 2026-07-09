package com.pilerks1.hdrrecorder.ui.manualcontrols

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints

/**
 * Rotates content 270° and swaps its measured width/height so a horizontally-authored
 * slider lays out correctly in a vertical (landscape) track. Shared by RibbonSlider and
 * FpsSlider so the rotation/remeasure math lives in exactly one place.
 */
fun Modifier.rotateVertical(): Modifier = this
    .graphicsLayer {
        rotationZ = 270f
        transformOrigin = TransformOrigin(0.5f, 0.5f)
    }
    .layout { measurable, constraints ->
        val placeable = measurable.measure(
            Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth,
            )
        )
        layout(placeable.height, placeable.width) {
            placeable.place(
                -placeable.width / 2 + placeable.height / 2,
                -placeable.height / 2 + placeable.width / 2
            )
        }
    }
