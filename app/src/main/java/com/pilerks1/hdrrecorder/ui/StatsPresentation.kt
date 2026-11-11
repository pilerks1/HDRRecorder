package com.pilerks1.hdrrecorder.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.pilerks1.hdrrecorder.model.StatsSnapshot
import com.pilerks1.hdrrecorder.model.ThermalForecast
import com.pilerks1.hdrrecorder.model.ThermalStatus
import com.pilerks1.hdrrecorder.model.formatBitrateMbps
import com.pilerks1.hdrrecorder.model.formatDataSize
import kotlin.math.roundToInt

/** One source of text styles for both stats measurement and rendering. */
internal data class StatsTypography(
    val fontSize: TextUnit,
    val lineHeight: TextUnit
) {
    private fun style(weight: FontWeight) =
        TextStyle(fontSize = fontSize, lineHeight = lineHeight, letterSpacing = 0.sp, fontWeight = weight)

    val headerStyle = style(FontWeight.Bold)
    val labelStyle = style(FontWeight.Medium)
    val valueStyle = style(FontWeight.Normal)
}

internal data class StatRowModel(
    val label: String,
    val value: String,
    val ceilingValues: List<String>,
    val valueColor: Color = Color.White
)

internal data class StatBlockModel(
    val title: String,
    val rows: List<StatRowModel>
)

internal data class StatBlockMetrics(
    val naturalWidth: Int,
    val minimumWidth: Int,
    val labelWidth: Int,
    val spaceWidth: Int,
    val actualValueWidths: List<Int>
)

/**
 * Titles, labels, and ceiling values never change, so their widths are measured once per
 * typography. Only the current values are measured on each update. One instance belongs to one
 * [TextMeasurer], which is itself recreated when density or fonts change.
 */
internal class StatsMetricsCache(private val textMeasurer: TextMeasurer) {
    private val static = HashMap<StatsTypography, List<StatBlockMetrics>>()
    private val lineHeights = HashMap<StatsTypography, Int>()

    fun metrics(blocks: List<StatBlockModel>, typography: StatsTypography): List<StatBlockMetrics> {
        val fixed = static.getOrPut(typography) { measureStatic(blocks, typography) }
        return blocks.mapIndexed { index, block ->
            fixed[index].copy(actualValueWidths = block.rows.map { width(it.value, typography.valueStyle) })
        }
    }

    /** Every stats line is single-line text at the same fixed line height. */
    fun lineHeight(typography: StatsTypography): Int = lineHeights.getOrPut(typography) {
        textMeasurer.measure("0", style = typography.valueStyle, maxLines = 1).size.height
    }

    fun wrappedValueHeight(value: String, typography: StatsTypography, maxWidth: Int): Int =
        textMeasurer.measure(value, style = typography.valueStyle,
            constraints = Constraints(maxWidth = maxWidth.coerceAtLeast(0))).size.height

    private fun width(text: String, style: TextStyle): Int =
        textMeasurer.measure(text, style = style, maxLines = 1).size.width

    private fun measureStatic(blocks: List<StatBlockModel>, typography: StatsTypography): List<StatBlockMetrics> {
        val spaceWidth = width(" ", typography.labelStyle)
        return blocks.map { block ->
            val labelWidth = block.rows.maxOf { width("${it.label}:", typography.labelStyle) }
            val ceilingWidths = block.rows.map { row -> row.ceilingValues.maxOf { width(it, typography.valueStyle) } }
            val headerWidth = width(block.title, typography.headerStyle)
            StatBlockMetrics(
                naturalWidth = maxOf(headerWidth, ceilingWidths.maxOf { labelWidth + spaceWidth + it }),
                minimumWidth = maxOf(headerWidth, labelWidth),
                labelWidth = labelWidth,
                spaceWidth = spaceWidth,
                actualValueWidths = emptyList()
            )
        }
    }
}

internal fun allocateWrappedWidths(
    metrics: List<StatBlockMetrics>,
    availableWidth: Int
): List<Int> {
    val minimumTotal = metrics.sumOf { it.minimumWidth }
    if (minimumTotal >= availableWidth) {
        return proportionalWidths(metrics.map { it.minimumWidth }, availableWidth)
    }

    val remaining = availableWidth - minimumTotal
    val demands = metrics.map { (it.naturalWidth - it.minimumWidth).coerceAtLeast(0) }
    val demandTotal = demands.sum()
    if (demandTotal == 0) return metrics.map { it.minimumWidth }

    val widths = metrics.indices.map { index ->
        metrics[index].minimumWidth + remaining * demands[index] / demandTotal
    }.toMutableList()
    widths[widths.lastIndex] += availableWidth - widths.sum()
    return widths
}

private fun proportionalWidths(requested: List<Int>, availableWidth: Int): List<Int> {
    val requestedTotal = requested.sum().coerceAtLeast(1)
    val widths = requested.map { availableWidth * it / requestedTotal }.toMutableList()
    widths[widths.lastIndex] += availableWidth - widths.sum()
    return widths
}

internal fun statsBlocks(snapshot: StatsSnapshot, isRecording: Boolean): List<StatBlockModel> = listOf(
    StatBlockModel(
        title = "VIDEO",
        rows = listOf(
            StatRowModel(
                "SS",
                if (snapshot.shutterSpeed > 0) "1/${snapshot.shutterSpeed.roundToInt()}s" else "N/A",
                shutterCeilingValues
            ),
            StatRowModel("ISO", "${snapshot.iso}", isoCeilingValues),
            StatRowModel(
                "FPS",
                if (isRecording) "${snapshot.effectiveFps}" else "N/A",
                fpsCeilingValues
            ),
            StatRowModel(
                "DROP",
                if (isRecording && snapshot.canMeasureDroppedFrames) "${snapshot.droppedFrames}" else "N/A",
                dropCeilingValues
            )
        )
    ),
    StatBlockModel(
        title = "STORAGE",
        rows = listOf(
            StatRowModel(
                "FREE",
                snapshot.storageRemainingFormatted,
                dataSizeCeilingValues
            ),
            StatRowModel(
                "TIME",
                snapshot.storageRemainingTime,
                timeCeilingValues
            ),
            StatRowModel(
                "BIT",
                if (isRecording) formatBitrateMbps(snapshot.actualBitrateMbps) else "N/A",
                bitrateCeilingValues
            ),
            StatRowModel(
                "SIZE",
                if (snapshot.displayedFileSizeWrittenBytes > 0L) {
                    formatDataSize(snapshot.displayedFileSizeWrittenBytes)
                } else {
                    "N/A"
                },
                dataSizeCeilingValues
            )
        )
    ),
    StatBlockModel(
        title = "THERMAL",
        rows = listOf(
            StatRowModel(
                "STATE",
                snapshot.thermalStatus.toStatsLabel(),
                thermalCeilingValues,
                getThermalColorByStatus(snapshot.thermalStatus)
            ),
            StatRowModel(
                "IN 60s",
                snapshot.thermalForecast.toDisplayText(),
                forecastCeilingValues,
                getThermalForecastColor(snapshot.thermalForecast)
            ),
            StatRowModel(
                "NET",
                "%.1f W".format(snapshot.netPowerWatts),
                powerCeilingValues
            )
        )
    )
)

private val shutterCeilingValues = listOf("N/A", "1/99999s")
private val isoCeilingValues = listOf("N/A", "99999")
private val fpsCeilingValues = listOf("N/A", "999")
private val dropCeilingValues = listOf("N/A", "999999")
private val timeCeilingValues = listOf("N/A", "0 Min", "999 Min", "1.00 Hr", "9999 Hr")
private val thermalCeilingValues = listOf("N/A", "SHUTDOWN")
private val forecastCeilingValues = listOf("0.00", "SHUTDOWN")
private val powerCeilingValues = listOf("0.0 W", "-99.9 W")

// Phone storage, including external drives, stays below petabyte scale.
private val dataSizeCeilingValues = listOf(
    "N/A",
    "0.00 MB",
    "999 MB",
    "1.00 GB",
    "999 GB",
    "1.00 TB",
    "99.9 TB"
)

// Video bitrate is limited to Bitrate.MAX_MBPS (999), so Mbps never needs promotion.
private val bitrateCeilingValues = listOf(
    "N/A",
    "0.00 Mbps",
    "999 Mbps"
)

private fun ThermalStatus.toStatsLabel(): String =
    if (this == ThermalStatus.EMERGENCY) "EMER" else label

private fun getThermalColorByStatus(status: ThermalStatus): Color = when (status) {
    ThermalStatus.NONE -> Color.White
    ThermalStatus.LIGHT -> Color.Yellow
    ThermalStatus.MODERATE -> Color(0xFFFFA500)
    else -> Color.Red
}

private fun getThermalForecastColor(forecast: ThermalForecast): Color = when (forecast) {
    is ThermalForecast.Status -> getThermalColorByStatus(forecast.status)
    is ThermalForecast.Headroom -> when {
        forecast.value >= 1.0f -> Color.Red
        forecast.value >= 0.85f -> Color(0xFFFFA500)
        forecast.value >= 0.75f -> Color.Yellow
        else -> Color.White
    }
}

private fun ThermalForecast.toDisplayText(): String = when (this) {
    is ThermalForecast.Status -> status.toStatsLabel()
    is ThermalForecast.Headroom -> "%.2f".format(value)
}
