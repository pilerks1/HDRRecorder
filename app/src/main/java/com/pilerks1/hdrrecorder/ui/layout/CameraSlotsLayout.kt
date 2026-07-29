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
    STATS_FULLSCREEN,
    PREVIEW,
    SECONDARY_CONTROLS,
    EXPANDED_PANEL,
    PRIMARY_ACTIONS,
    MANUAL_RESET,
    LOW_LIGHT_BOOST_INDICATOR
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
    manualReset: @Composable (CameraLayoutSpec) -> Unit,
    lowLightBoostIndicator: @Composable (CameraLayoutSpec) -> Unit,
    modifier: Modifier = Modifier
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        check(width != Constraints.Infinity && height != Constraints.Infinity)

        val axis = CameraLayoutCalculator.axisFor(width, height)
        val panelThicknessSpec = panelThickness(axis)
        val safeWidth = (
            width - cutoutInsets.left.roundToInt() - cutoutInsets.right.roundToInt()
            ).coerceAtLeast(0)
        val safeHeight = (
            height - cutoutInsets.top.roundToInt() - cutoutInsets.bottom.roundToInt()
            ).coerceAtLeast(0)
        val safeStatsPlaceable = subcompose(CameraSlot.STATS) { stats(axis) }
            .single()
            .measure(Constraints(0, safeWidth, 0, safeHeight))

        fun calculate(statsWidth: Int, statsHeight: Int) = CameraLayoutCalculator.calculate(
                widthPx = width,
                heightPx = height,
                density = density,
                aspectRatio = aspectRatio,
                secondaryButtonCount = secondaryButtonCount,
                statsWidthPx = statsWidth,
                statsHeightPx = statsHeight,
                activePanelThicknessPx = panelThicknessSpec.activeThicknessPx,
                maximumPanelThicknessPx = panelThicknessSpec.maximumThicknessPx,
                cutoutInsets = cutoutInsets
            )

        val safeSpec = calculate(safeStatsPlaceable.width, safeStatsPlaceable.height)
        val useFullscreenStats = safeSpec.mode == CameraLayoutMode.SHRUNK &&
            (safeWidth != width || safeHeight != height)
        val fullscreenStatsPlaceable = if (useFullscreenStats) {
            subcompose(CameraSlot.STATS_FULLSCREEN) { stats(axis) }
                .single()
                .measure(Constraints(0, width, 0, height))
        } else {
            null
        }
        val fullscreenSpec = fullscreenStatsPlaceable?.let {
            calculate(it.width, it.height)
        }
        val keepFullscreenStats = fullscreenSpec?.mode == CameraLayoutMode.SHRUNK
        val statsPlaceable = if (keepFullscreenStats) {
            checkNotNull(fullscreenStatsPlaceable)
        } else {
            safeStatsPlaceable
        }
        val spec = if (keepFullscreenStats) checkNotNull(fullscreenSpec) else safeSpec

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
        val lowLightBoostPlaceable = subcompose(CameraSlot.LOW_LIGHT_BOOST_INDICATOR) {
            Box(Modifier.fillMaxSize()) { lowLightBoostIndicator(spec) }
        }
            .single()
            .measure(spec.lowLightBoostIndicator.fixedConstraints())
        val manualResetPlaceable = subcompose(CameraSlot.MANUAL_RESET) {
            Box(Modifier.fillMaxSize()) { manualReset(spec) }
        }
            .single()
            .measure(spec.manualReset.fixedConstraints())

        layout(width, height) {
            previewPlaceable.place(spec.preview.left.roundToInt(), spec.preview.top.roundToInt())
            statsPlaceable.place(spec.stats.left.roundToInt(), spec.stats.top.roundToInt())
            controlsPlaceable.place(
                spec.secondaryControls.left.roundToInt(),
                spec.secondaryControls.top.roundToInt()
            )
            expandedPlaceable.place(spec.expandedPanel.left.roundToInt(), spec.expandedPanel.top.roundToInt())
            primaryPlaceable.place(spec.primaryActions.left.roundToInt(), spec.primaryActions.top.roundToInt())
            lowLightBoostPlaceable.place(
                spec.lowLightBoostIndicator.left.roundToInt(),
                spec.lowLightBoostIndicator.top.roundToInt()
            )
            manualResetPlaceable.place(
                spec.manualReset.left.roundToInt(),
                spec.manualReset.top.roundToInt()
            )
        }
    }
}

private fun LayoutRect.fixedConstraints(): Constraints = Constraints.fixed(
    width.roundToInt().coerceAtLeast(0),
    height.roundToInt().coerceAtLeast(0)
)
