package com.pilerks1.hdrrecorder.ui.camera.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pilerks1.hdrrecorder.data.telemetry.StatsSnapshot
import com.pilerks1.hdrrecorder.ui.camera.layout.AxisSpec
import com.pilerks1.hdrrecorder.ui.style.StatsTypography
import com.pilerks1.hdrrecorder.ui.style.UiTextStyles
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow

private val NormalStatsTypography = UiTextStyles.Stats.normal
private val CompactStatsTypography = UiTextStyles.Stats.compact

/** The chosen typography, block widths, and wrapping for one set of constraints. */
internal class StatsArrangement(
    val typography: StatsTypography,
    val metrics: List<StatBlockMetrics>,
    val widths: List<Int>,
    val allowValueWrap: Boolean,
    val size: IntSize
)

/**
 * Three independently measured stats blocks that reserve only their required camera rail.
 *
 * The arrangement is a pure function of the current snapshot and constraints, so the camera
 * layout can size the rail for several candidate bounds while composing it only once. State is
 * read only during measurement, so updates relayout this rail without recomposing its parent.
 */
@Stable
internal class StatsRail(
    private val snapshot: State<StatsSnapshot>,
    private val isRecording: State<Boolean>,
    private val cache: StatsMetricsCache
) {
    val blocks: List<StatBlockModel> get() = statsBlocks(snapshot.value, isRecording.value)

    fun size(axis: AxisSpec, maxWidth: Int, maxHeight: Int): IntSize = arrange(blocks, axis, maxWidth, maxHeight).size

    fun arrange(blocks: List<StatBlockModel>, axis: AxisSpec, maxWidth: Int, maxHeight: Int): StatsArrangement {
        fun heights(typography: StatsTypography, metrics: List<StatBlockMetrics>, widths: List<Int>, wrap: Boolean) =
            blocks.mapIndexed { index, block ->
                val line = cache.lineHeight(typography)
                line + block.rows.withIndex().sumOf { (row, model) ->
                    val metric = metrics[index]
                    if (wrap && metric.labelWidth + metric.spaceWidth + metric.actualValueWidths[row] > widths[index]) {
                        line + cache.wrappedValueHeight(model.value, typography, widths[index])
                    } else line
                }
            }

        fun candidate(
            typography: StatsTypography,
            wrap: Boolean,
            widthsFor: (List<StatBlockMetrics>) -> List<Int>
        ): Pair<StatsArrangement, Boolean> {
            val metrics = cache.metrics(blocks, typography)
            val widths = widthsFor(metrics)
            val heights = heights(typography, metrics, widths, wrap)
            val size = if (axis.usesVerticalTrack) {
                IntSize(widths.maxOrNull() ?: 0, maxHeight)
            } else {
                IntSize(maxWidth, (heights.maxOrNull() ?: 0).coerceAtMost(maxHeight))
            }
            val fits = if (axis.usesVerticalTrack) heights.sum() <= maxHeight else widths.sum() <= maxWidth
            return StatsArrangement(typography, metrics, widths, wrap, size) to fits
        }

        val natural = { metrics: List<StatBlockMetrics> -> metrics.map { it.naturalWidth } }
        val (normal, normalFits) = candidate(NormalStatsTypography, wrap = false, natural)
        if (normalFits) return normal
        val (compact, compactFits) = candidate(CompactStatsTypography, wrap = false, natural)
        if (compactFits || axis.usesVerticalTrack) return compact
        return candidate(CompactStatsTypography, wrap = true) { allocateWrappedWidths(it, maxWidth) }.first
    }
}

@Composable
internal fun rememberStatsRail(stats: StateFlow<StatsSnapshot>, isRecording: Boolean): StatsRail {
    val snapshot = stats.collectAsStateWithLifecycle()
    val recording = rememberUpdatedState(isRecording)
    val textMeasurer = rememberTextMeasurer()
    return remember(snapshot, recording, textMeasurer) {
        StatsRail(snapshot, recording, StatsMetricsCache(textMeasurer))
    }
}

@Composable
internal fun CameraStatsRail(
    rail: StatsRail,
    axis: AxisSpec,
    modifier: Modifier = Modifier
) {
    SubcomposeLayout(modifier = modifier.clipToBounds()) { constraints ->
        check(constraints.maxWidth != Constraints.Infinity && constraints.maxHeight != Constraints.Infinity)
        val blocks = rail.blocks
        val arrangement = rail.arrange(blocks, axis, constraints.maxWidth, constraints.maxHeight)
        // Only the selected arrangement is composed.
        val placeables = subcompose(Unit) {
            blocks.forEachIndexed { index, block ->
                StatBlock(
                    block = block,
                    typography = arrangement.typography,
                    metrics = arrangement.metrics[index],
                    assignedWidthPx = arrangement.widths[index],
                    allowValueWrap = arrangement.allowValueWrap
                )
            }
        }.mapIndexed { index, measurable ->
            measurable.measure(Constraints.fixedWidth(arrangement.widths[index].coerceAtLeast(0)))
        }

        val width = if (axis.usesVerticalTrack) placeables.maxOfOrNull { it.width } ?: 0 else constraints.maxWidth
        val height = if (axis.usesVerticalTrack) {
            constraints.maxHeight
        } else {
            (placeables.maxOfOrNull { it.height } ?: 0).coerceAtMost(constraints.maxHeight)
        }
        val primaryAvailable = if (axis.usesVerticalTrack) height else width
        val primarySizes = placeables.map {
            if (axis.usesVerticalTrack) it.height else it.width
        }.toIntArray()
        val positions = IntArray(placeables.size)
        if (primarySizes.sum() <= primaryAvailable) {
            val gap = (primaryAvailable - primarySizes.sum()).toFloat() /
                (primarySizes.size + 1)
            var next = gap
            primarySizes.forEachIndexed { index, size ->
                positions[index] = next.roundToInt()
                next += size + gap
            }
        } else {
            var next = 0
            primarySizes.forEachIndexed { index, size ->
                positions[index] = next
                next += size
            }
        }

        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                if (axis.usesVerticalTrack) {
                    placeable.place(0, positions[index])
                } else {
                    placeable.place(positions[index], 0)
                }
            }
        }
    }
}

@Composable
private fun StatBlock(
    block: StatBlockModel,
    typography: StatsTypography,
    metrics: StatBlockMetrics,
    assignedWidthPx: Int,
    allowValueWrap: Boolean
) {
    val density = LocalDensity.current
    val labelWidth = with(density) { metrics.labelWidth.toDp() }
    val spaceWidth = with(density) { metrics.spaceWidth.toDp() }
    Column(modifier = Modifier.width(with(density) { assignedWidthPx.toDp() })) {
        Text(
            text = block.title,
            color = Color.Gray,
            style = typography.headerStyle,
            maxLines = 1
        )
        block.rows.forEachIndexed { index, row ->
            val moveValueBelow = allowValueWrap &&
                metrics.labelWidth + metrics.spaceWidth + metrics.actualValueWidths[index] >
                assignedWidthPx
            if (moveValueBelow) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    StatLabel(row.label, typography, Modifier)
                    Text(
                        text = row.value,
                        color = row.valueColor,
                        style = typography.valueStyle,
                        softWrap = true
                    )
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatLabel(row.label, typography, Modifier.width(labelWidth))
                    Spacer(Modifier.width(spaceWidth))
                    Text(
                        text = row.value,
                        color = row.valueColor,
                        style = typography.valueStyle,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}

@Composable
private fun StatLabel(
    label: String,
    typography: StatsTypography,
    modifier: Modifier
) {
    Text(
        text = "$label:",
        color = Color.LightGray,
        style = typography.labelStyle,
        maxLines = 1,
        modifier = modifier
    )
}
