package com.pilerks1.hdrrecorder.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.pilerks1.hdrrecorder.model.StatsSnapshot
import com.pilerks1.hdrrecorder.model.ThermalForecast
import com.pilerks1.hdrrecorder.model.ThermalStatus
import com.pilerks1.hdrrecorder.model.formatBitrateMbps
import com.pilerks1.hdrrecorder.model.formatDataSize
import kotlin.math.roundToInt

internal data class StatsTypography(
    val fontSize: TextUnit,
    val lineHeight: TextUnit
)

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

internal fun measureBlockMetrics(
    blocks: List<StatBlockModel>,
    typography: StatsTypography,
    textMeasurer: TextMeasurer
): List<StatBlockMetrics> {
    val labelStyle = TextStyle(
        fontSize = typography.fontSize,
        lineHeight = typography.lineHeight,
        letterSpacing = 0.sp,
        fontWeight = FontWeight.Medium
    )
    val valueStyle = TextStyle(
        fontSize = typography.fontSize,
        lineHeight = typography.lineHeight,
        letterSpacing = 0.sp,
        fontWeight = FontWeight.Normal
    )
    val headerStyle = TextStyle(
        fontSize = typography.fontSize,
        lineHeight = typography.lineHeight,
        letterSpacing = 0.sp,
        fontWeight = FontWeight.Bold
    )
    val spaceWidth = textMeasurer.measure(" ", style = labelStyle, maxLines = 1).size.width

    return blocks.map { block ->
        val labelWidth = block.rows.maxOf {
            textMeasurer.measure("${it.label}:", style = labelStyle, maxLines = 1).size.width
        }
        val ceilingWidths = block.rows.map { row ->
            row.ceilingValues.maxOf {
                textMeasurer.measure(it, style = valueStyle, maxLines = 1).size.width
            }
        }
        val actualWidths = block.rows.map {
            textMeasurer.measure(it.value, style = valueStyle, maxLines = 1).size.width
        }
        val headerWidth = textMeasurer.measure(block.title, style = headerStyle, maxLines = 1).size.width
        StatBlockMetrics(
            naturalWidth = maxOf(
                headerWidth,
                block.rows.indices.maxOf { labelWidth + spaceWidth + ceilingWidths[it] }
            ),
            minimumWidth = maxOf(headerWidth, labelWidth),
            labelWidth = labelWidth,
            spaceWidth = spaceWidth,
            actualValueWidths = actualWidths
        )
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
                listOf("N/A", "1/99999s")
            ),
            StatRowModel("ISO", "${snapshot.iso}", listOf("N/A", "99999")),
            StatRowModel(
                "FPS",
                if (isRecording) "${snapshot.effectiveFps}" else "N/A",
                listOf("N/A", "999")
            ),
            StatRowModel(
                "DROP",
                if (isRecording && snapshot.canMeasureDroppedFrames) "${snapshot.droppedFrames}" else "N/A",
                listOf("N/A", "999999")
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
                listOf("N/A", "0 Min", "999 Min", "1.00 Hr", "9999 Hr")
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
                listOf("N/A", "SHUTDOWN"),
                getThermalColorByStatus(snapshot.thermalStatus)
            ),
            StatRowModel(
                "IN 60s",
                snapshot.thermalForecast.toDisplayText(),
                listOf("0.00", "SHUTDOWN"),
                getThermalForecastColor(snapshot.thermalForecast)
            ),
            StatRowModel(
                "NET",
                "%.1f W".format(snapshot.netPowerWatts),
                listOf("0.0 W", "-99.9 W")
            )
        )
    )
)

private val dataSizeCeilingValues = listOf(
    "N/A",
    "0.00 MB",
    "999 MB",
    "1.00 GB",
    "999 GB",
    "1.00 TB",
    "999 TB"
)

private val bitrateCeilingValues = listOf(
    "N/A",
    "0.00 Mbps",
    "999 Mbps",
    "1.00 Gbps",
    "999 Gbps"
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
