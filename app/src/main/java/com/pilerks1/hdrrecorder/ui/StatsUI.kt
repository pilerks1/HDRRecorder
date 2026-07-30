package com.pilerks1.hdrrecorder.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import com.pilerks1.hdrrecorder.model.StatsSnapshot
import com.pilerks1.hdrrecorder.ui.layout.AxisSpec
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow

private val NormalStatsTypography = StatsTypography(12.sp, 18.sp)
private val CompactStatsTypography = StatsTypography(10.sp, 15.sp)

private enum class StatsMeasureSlot {
    NORMAL,
    COMPACT,
    WRAPPED
}

/** Three independently measured stats blocks that reserve only their required camera rail. */
@Composable
fun StatsUI(
    stats: StateFlow<StatsSnapshot>,
    isRecording: Boolean,
    axis: AxisSpec,
    modifier: Modifier = Modifier
) {
    val snapshot by stats.collectAsStateWithLifecycle()
    StatsRail(
        blocks = statsBlocks(snapshot, isRecording),
        axis = axis,
        modifier = modifier
    )
}

@Composable
private fun StatsRail(
    blocks: List<StatBlockModel>,
    axis: AxisSpec,
    modifier: Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val normalMetrics = measureBlockMetrics(blocks, NormalStatsTypography, textMeasurer)
    val compactMetrics = measureBlockMetrics(blocks, CompactStatsTypography, textMeasurer)

    SubcomposeLayout(modifier = modifier.clipToBounds()) { constraints ->
        check(constraints.maxWidth != Constraints.Infinity && constraints.maxHeight != Constraints.Infinity)

        fun measureBlocks(
            slot: StatsMeasureSlot,
            typography: StatsTypography,
            metrics: List<StatBlockMetrics>,
            widths: List<Int>,
            allowValueWrap: Boolean
        ) = subcompose(slot) {
            blocks.forEachIndexed { index, block ->
                StatBlock(
                    block = block,
                    typography = typography,
                    metrics = metrics[index],
                    assignedWidthPx = widths[index],
                    allowValueWrap = allowValueWrap
                )
            }
        }.mapIndexed { index, measurable ->
            measurable.measure(Constraints.fixedWidth(widths[index].coerceAtLeast(0)))
        }

        fun fits(placeables: List<androidx.compose.ui.layout.Placeable>): Boolean =
            if (axis.usesVerticalTrack) {
                placeables.sumOf { it.height } <= constraints.maxHeight
            } else {
                placeables.sumOf { it.width } <= constraints.maxWidth
            }

        val normalWidths = normalMetrics.map { it.naturalWidth }
        val normalPlaceables = measureBlocks(
            StatsMeasureSlot.NORMAL,
            NormalStatsTypography,
            normalMetrics,
            normalWidths,
            allowValueWrap = false
        )

        val selectedPlaceables = if (fits(normalPlaceables)) {
            normalPlaceables
        } else {
            val compactWidths = compactMetrics.map { it.naturalWidth }
            val compactPlaceables = measureBlocks(
                StatsMeasureSlot.COMPACT,
                CompactStatsTypography,
                compactMetrics,
                compactWidths,
                allowValueWrap = false
            )
            if (
                axis.usesVerticalTrack ||
                fits(compactPlaceables)
            ) {
                compactPlaceables
            } else {
                val wrappedWidths = allocateWrappedWidths(compactMetrics, constraints.maxWidth)
                measureBlocks(
                    StatsMeasureSlot.WRAPPED,
                    CompactStatsTypography,
                    compactMetrics,
                    wrappedWidths,
                    allowValueWrap = true
                )
            }
        }

        val width = if (axis.usesVerticalTrack) {
            selectedPlaceables.maxOfOrNull { it.width } ?: 0
        } else {
            constraints.maxWidth
        }
        val height = if (axis.usesVerticalTrack) {
            constraints.maxHeight
        } else {
            (selectedPlaceables.maxOfOrNull { it.height } ?: 0).coerceAtMost(constraints.maxHeight)
        }
        val primaryAvailable = if (axis.usesVerticalTrack) height else width
        val primarySizes = selectedPlaceables.map {
            if (axis.usesVerticalTrack) it.height else it.width
        }.toIntArray()
        val positions = IntArray(selectedPlaceables.size)
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
            selectedPlaceables.forEachIndexed { index, placeable ->
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
            fontSize = typography.fontSize,
            lineHeight = typography.lineHeight,
            letterSpacing = 0.sp,
            fontWeight = FontWeight.Bold,
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
                        fontSize = typography.fontSize,
                        lineHeight = typography.lineHeight,
                        letterSpacing = 0.sp,
                        fontWeight = FontWeight.Normal,
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
                        fontSize = typography.fontSize,
                        lineHeight = typography.lineHeight,
                        letterSpacing = 0.sp,
                        fontWeight = FontWeight.Normal,
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
        fontSize = typography.fontSize,
        lineHeight = typography.lineHeight,
        letterSpacing = 0.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = modifier
    )
}
