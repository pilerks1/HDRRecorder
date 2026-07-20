package com.pilerks1.hdrrecorder.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import com.pilerks1.hdrrecorder.model.RecordingAspectRatio
import kotlin.math.roundToInt

private enum class CameraSlot {
    STATS,
    PREVIEW,
    SECONDARY_CONTROLS,
    EXPANDED_PANEL,
    PRIMARY_ACTIONS
}

data class PanelThicknessSpec(
    val activeThicknessPx: Float,
    val maximumThicknessPx: Float
)

/** Measures the stats slot first, then places every camera surface from one geometry spec. */
@Composable
fun CameraSlotsLayout(
    aspectRatio: RecordingAspectRatio,
    secondaryButtonCount: Int,
    cutoutInsets: EdgeInsets,
    panelThickness: (AxisSpec) -> PanelThicknessSpec,
    stats: @Composable (AxisSpec) -> Unit,
    preview: @Composable (CameraLayoutSpec) -> Unit,
    secondaryControls: @Composable (CameraLayoutSpec) -> Unit,
    expandedPanel: @Composable (CameraLayoutSpec) -> Unit,
    primaryActions: @Composable (CameraLayoutSpec) -> Unit,
    modifier: Modifier = Modifier
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        check(width != Constraints.Infinity && height != Constraints.Infinity)

        val axis = CameraLayoutCalculator.axisFor(width, height)
        val panelThicknessSpec = panelThickness(axis)
        val statsPlaceable = subcompose(CameraSlot.STATS) { stats(axis) }
            .single()
            .measure(Constraints(0, width, 0, height))
        val spec = CameraLayoutCalculator.calculate(
            widthPx = width,
            heightPx = height,
            density = density,
            aspectRatio = aspectRatio,
            secondaryButtonCount = secondaryButtonCount,
            statsWidthPx = statsPlaceable.width,
            statsHeightPx = statsPlaceable.height,
            activePanelThicknessPx = panelThicknessSpec.activeThicknessPx,
            maximumPanelThicknessPx = panelThicknessSpec.maximumThicknessPx,
            cutoutInsets = cutoutInsets
        )

        val previewPlaceable = subcompose(CameraSlot.PREVIEW) {
            Box(Modifier.fillMaxSize()) { preview(spec) }
        }
            .single()
            .measure(spec.preview.fixedConstraints())
        val controlsPlaceable = subcompose(CameraSlot.SECONDARY_CONTROLS) {
            Box(Modifier.fillMaxSize()) { secondaryControls(spec) }
        }
            .single()
            .measure(spec.secondaryControls.fixedConstraints())
        val expandedPlaceable = subcompose(CameraSlot.EXPANDED_PANEL) {
            Box(Modifier.fillMaxSize()) { expandedPanel(spec) }
        }
            .single()
            .measure(spec.expandedPanel.fixedConstraints())
        val primaryPlaceable = subcompose(CameraSlot.PRIMARY_ACTIONS) {
            Box(Modifier.fillMaxSize()) { primaryActions(spec) }
        }
            .single()
            .measure(spec.primaryActions.fixedConstraints())

        layout(width, height) {
            previewPlaceable.place(spec.preview.left.roundToInt(), spec.preview.top.roundToInt())
            statsPlaceable.place(spec.stats.left.roundToInt(), spec.stats.top.roundToInt())
            controlsPlaceable.place(
                spec.secondaryControls.left.roundToInt(),
                spec.secondaryControls.top.roundToInt()
            )
            expandedPlaceable.place(spec.expandedPanel.left.roundToInt(), spec.expandedPanel.top.roundToInt())
            primaryPlaceable.place(spec.primaryActions.left.roundToInt(), spec.primaryActions.top.roundToInt())
        }
    }
}

private fun LayoutRect.fixedConstraints(): Constraints = Constraints.fixed(
    width.roundToInt().coerceAtLeast(0),
    height.roundToInt().coerceAtLeast(0)
)
