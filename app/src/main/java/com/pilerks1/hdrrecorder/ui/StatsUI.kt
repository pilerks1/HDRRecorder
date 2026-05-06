package com.pilerks1.hdrrecorder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pilerks1.hdrrecorder.model.StatsSnapshot
import kotlin.math.roundToInt

/**
 * Modular Stats UI that displays real-time camera and hardware metrics.
 * Designed to be expandable with consistent styling.
 * 
 * Uses FlowColumn to automatically wrap stats horizontally when in portrait mode,
 * ensuring all stats remain visible and upright.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsUI(
    stats: StatsSnapshot,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopStart
    ) {
        FlowRow(
            //modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.Start),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            // --- SECTION: CAMERA CAPTURE ---
            StatGroup("VIDEO") {
                StatRow(
                    label = "SS",
                    value = if (stats.shutterSpeed > 0) "1/${stats.shutterSpeed.roundToInt()}s" else "N/A"
                )
                StatRow(label = "ISO", value = "${stats.iso}")

                StatRow(
                    label = "FPS",
                    value = if (isRecording) "${stats.effectiveFps}" else "N/A"
                )
                StatRow(
                    label = "DROP",
                    value = if (isRecording) "${stats.droppedFrames}" else "N/A"
                )
            }

            // --- SECTION: STORAGE ---
            StatGroup("STORAGE") {
                StatRow(label = "FREE", value = stats.storageRemainingFormatted)
                StatRow(label = "TIME", value = stats.storageRemainingTime)
                StatRow(
                    label = "BIT",
                    value = if (isRecording) "%.1f Mbps".format(stats.actualBitrateMbps) else "N/A"
                )
                StatRow(
                    label = "SIZE",
                    value = if (stats.displayedFileSizeWrittenBytes > 0L) "%.1f MB".format(stats.displayedFileSizeWrittenBytes.toDouble() / (1024.0 * 1024.0)) else "N/A"
                )
            }

            // --- SECTION: THERMAL ---
            StatGroup("THERMAL") {
                val statusColor = getThermalColorByInt(stats.thermalStatusInt)
                StatRow(
                    label = "STATE",
                    value = stats.thermalStatus,
                    valueColor = statusColor
                )

                val forecastColor = getThermalColorByStatus(stats.thermalForecastStatus)
                StatRow(
                    label = "IN 30s",
                    value = stats.thermalForecastStatus,
                    valueColor = forecastColor
                )

                StatRow(
                    label = "NET",
                    value = "%.1fW".format(stats.netPowerWatts),
                    valueColor = Color.White
                )
            }
        }
    }
}

/**
 * A private helper that groups a header and its stats into a single column.
 * This ensures that when the FlowRow wraps, it wraps the entire section together.
 */
@Composable
private fun StatGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy((-4).dp),
        modifier = Modifier.padding(start = 4.dp)  // left side pad
    ) {
        StatSectionHeader(title)
        content()
    }
}

private fun getThermalColorByInt(status: Int): Color {
    return when (status) {
        android.os.PowerManager.THERMAL_STATUS_NONE -> Color.White
        android.os.PowerManager.THERMAL_STATUS_LIGHT -> Color.Yellow
        android.os.PowerManager.THERMAL_STATUS_MODERATE -> Color(0xFFFFA500)
        else -> Color.Red
    }
}

private fun getThermalColorByStatus(status: String): Color {
    val floatVal = status.toFloatOrNull()
    return if (floatVal != null) {
        when {
            floatVal >= 1.0f -> Color.Red
            floatVal >= 0.85f -> Color(0xFFFFA500) // Orange
            floatVal >= 0.75f -> Color.Yellow
            else -> Color.White
        }
    } else {
        when (status) {
            "NONE" -> Color.White
            "LIGHT" -> Color.Yellow
            "MODERATE" -> Color(0xFFFFA500)
            else -> Color.Red // SEVERE, CRITICAL, EMERGENCY, SHUTDOWN
        }
    }
}

@Composable
private fun StatSectionHeader(text: String) {
    Text(
        text = text,
        color = Color.Gray,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp) // consistent top padding for headers
    )
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Row(
        //verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp) // readout distance from title text. also influences width
    ) {
        Text(
            text = "$label:",
            color = Color.LightGray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(42.dp) // text width/wrapping?
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal
        )
    }
}