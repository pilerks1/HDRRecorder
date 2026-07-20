package com.pilerks1.hdrrecorder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pilerks1.hdrrecorder.model.StatsSnapshot
import com.pilerks1.hdrrecorder.model.ThermalForecast
import com.pilerks1.hdrrecorder.model.ThermalStatus
import com.pilerks1.hdrrecorder.model.toSigFigs
import com.pilerks1.hdrrecorder.ui.layout.AxisSpec
import com.pilerks1.hdrrecorder.ui.layout.AxisStack
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow

/**
 * Modular Stats UI that displays real-time camera and hardware metrics.
 * Designed to be expandable with consistent styling.
 * 
 * The root camera layout measures this content before assigning preview geometry.
 */
@Composable
fun StatsUI(
    stats: StateFlow<StatsSnapshot>,
    isRecording: Boolean,
    axis: AxisSpec,
    modifier: Modifier = Modifier
) {
    val snapshot by stats.collectAsState()
    AxisStack(axis = axis, modifier = modifier, spacing = 8.dp) {
            // --- SECTION: CAMERA CAPTURE ---
            StatGroup("VIDEO") {
                StatRow(
                    label = "SS",
                    value = if (snapshot.shutterSpeed > 0) "1/${snapshot.shutterSpeed.roundToInt()}s" else "N/A"
                )
                StatRow(label = "ISO", value = "${snapshot.iso}")

                StatRow(
                    label = "FPS",
                    value = if (isRecording) "${snapshot.effectiveFps}" else "N/A"
                )
                StatRow(
                    label = "DROP",
                    value = if (isRecording && snapshot.canMeasureDroppedFrames) "${snapshot.droppedFrames}" else "N/A"
                )
            }

            // --- SECTION: STORAGE ---
            StatGroup("STORAGE") {
                StatRow(label = "FREE", value = snapshot.storageRemainingFormatted)
                StatRow(label = "TIME", value = snapshot.storageRemainingTime)
                StatRow(
                    label = "BIT",
                    value = if (isRecording) "${snapshot.actualBitrateMbps.toSigFigs()} Mbps" else "N/A"
                )
                StatRow(
                    label = "SIZE",
                    value = if (snapshot.displayedFileSizeWrittenBytes > 0L) "${(snapshot.displayedFileSizeWrittenBytes.toDouble() / (1024.0 * 1024.0)).toSigFigs()} MB" else "N/A"
                )
            }

            // --- SECTION: THERMAL ---
            StatGroup("THERMAL") {
                val statusColor = getThermalColorByStatus(snapshot.thermalStatus)
                StatRow(
                    label = "STATE",
                    value = snapshot.thermalStatus.label,
                    valueColor = statusColor
                )

                val forecastColor = getThermalForecastColor(snapshot.thermalForecast)
                StatRow(
                    label = "IN 60s",
                    value = snapshot.thermalForecast.toDisplayText(),
                    valueColor = forecastColor
                )

                StatRow(
                    label = "NET",
                    value = "%.1f W".format(snapshot.netPowerWatts),
                    valueColor = Color.White
                )
            }
    }
}

/**
 * A private helper that groups a header and its stats into one measurable section.
 */
@Composable
private fun StatGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy((-4).dp),
        modifier = Modifier
            .padding(start = 4.dp)
            .width(104.dp)
    ) {
        StatSectionHeader(title)
        content()
    }
}

private fun getThermalColorByStatus(status: ThermalStatus): Color {
    return when (status) {
        ThermalStatus.NONE -> Color.White
        ThermalStatus.LIGHT -> Color.Yellow
        ThermalStatus.MODERATE -> Color(0xFFFFA500)
        else -> Color.Red
    }
}

private fun getThermalForecastColor(forecast: ThermalForecast): Color = when (forecast) {
    is ThermalForecast.Status -> getThermalColorByStatus(forecast.status)
    is ThermalForecast.Headroom -> {
        when {
            forecast.value >= 1.0f -> Color.Red
            forecast.value >= 0.85f -> Color(0xFFFFA500) // Orange
            forecast.value >= 0.75f -> Color.Yellow
            else -> Color.White
        }
    }
}

private fun ThermalForecast.toDisplayText(): String = when (this) {
    is ThermalForecast.Status -> status.label
    is ThermalForecast.Headroom -> "%.2f".format(value)
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
