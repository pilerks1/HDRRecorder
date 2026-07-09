package com.pilerks1.hdrrecorder.ui.manualcontrols

import android.util.Range
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pilerks1.hdrrecorder.data.camera.CameraCapabilities
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotatingRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float>,
    isLandscape: Boolean,
    enabled: Boolean = true,
    isActive: Boolean = true,
    ticks: List<Pair<Float, String>> = emptyList(),
    modifier: Modifier = Modifier
) {
    val textRotation = if (isLandscape) 90f else 0f

    val mainColor = if (isActive) Color.White else if (enabled) Color.Gray else Color.DarkGray
    val secondaryColor = if (isActive) Color.Gray else if (enabled) Color.Gray else Color.DarkGray

    val axis = @Composable {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp) // match RangeSlider thumb padding
                .offset(y = 16.dp)
        ) {
            val w = maxWidth
            if (ticks.isEmpty()) {
                // Fallback to 10 even ticks if none provided
                for (i in 0..10) {
                    val fraction = i / 10f
                    Box(modifier = Modifier.offset(x = w * fraction - 0.5.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.width(1.dp).height(if (i % 5 == 0) 6.dp else 4.dp).background(secondaryColor))
                        }
                    }
                }
            } else {
                for (tick in ticks) {
                    val fraction = tick.first
                    val labelStr = tick.second
                    Box(modifier = Modifier.offset(x = w * fraction - 0.5.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.width(1.dp).height(6.dp).background(secondaryColor))
                            if (labelStr.isNotEmpty()) {
                                Text(
                                    text = labelStr,
                                    color = secondaryColor,
                                    fontSize = 7.sp,
                                    maxLines = 1,
                                    modifier = Modifier.offset(y = 2.dp).graphicsLayer { rotationZ = textRotation }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val sliderContent = @Composable {
        Box(contentAlignment = Alignment.Center) {
            axis()
            RangeSlider(
                value = value,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = valueRange,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = mainColor,
                    activeTrackColor = mainColor,
                    inactiveTrackColor = secondaryColor,
                    disabledThumbColor = Color.DarkGray,
                    disabledActiveTrackColor = Color.DarkGray
                ),
                startThumb = {
                    Box(
                        modifier = Modifier.size(width = 8.dp, height = 24.dp).background(mainColor, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${value.start.toInt()}", color = if (isActive) Color.White else mainColor, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .wrapContentSize(unbounded = true)
                                .offset(y = (-24).dp)
                                .graphicsLayer { rotationZ = textRotation }
                        )
                    }
                },
                endThumb = {
                    Box(
                        modifier = Modifier.size(width = 8.dp, height = 24.dp).background(mainColor, androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${value.endInclusive.toInt()}", color = if (isActive) Color.White else mainColor, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .wrapContentSize(unbounded = true)
                                .offset(y = (-24).dp)
                                .graphicsLayer { rotationZ = textRotation }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (isLandscape) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.rotateVertical()) {
                sliderContent()
            }
        }
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            sliderContent()
        }
    }
}

@Composable
fun FpsSlider(
    isManualFps: Boolean,
    currentRange: ClosedFloatingPointRange<Float>,
    autoFps: Int,
    isRecording: Boolean,
    caps: CameraCapabilities?,
    onToggleAuto: () -> Unit,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    isLandscape: Boolean
) {
    val content: @Composable (Modifier) -> Unit = { weightModifier ->
        Button(
            onClick = onToggleAuto,
            enabled = !isRecording,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isManualFps) Color.DarkGray else Color.White,
                disabledContainerColor = Color.DarkGray
            ),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(36.dp),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = if (isManualFps) "MAN" else "$autoFps",
                color = if (isManualFps) Color.LightGray else Color.Black,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        // Get min/max from capabilities
        val validRanges = caps?.fpsRanges ?: emptyList<Range<Int>>()
        val minFps = validRanges.minOfOrNull { it.lower }?.toFloat() ?: 15f
        val maxFps = validRanges.maxOfOrNull { it.upper }?.toFloat() ?: 60f
        
        // Extract unique FPS values
        val uniqueFpsValues = validRanges.flatMap { listOf(it.lower.toFloat(), it.upper.toFloat()) }.distinct().sorted()
        val ticks = uniqueFpsValues.map { fps ->
            val fraction = if (maxFps == minFps) 0.5f else (fps - minFps) / (maxFps - minFps)
            fraction to fps.roundToInt().toString()
        }

        var localRange by remember(currentRange) { 
            mutableStateOf(currentRange) 
        }

        RotatingRangeSlider(
            value = localRange,
            onValueChange = { newRange ->
                if (!isRecording || isManualFps) {
                    localRange = newRange
                }
            },
            onValueChangeFinished = {
                if (!isRecording || isManualFps) {
                    val clamped = SliderMath.clampFpsRange(localRange, currentRange, validRanges)
                    localRange = clamped // visually snap immediately
                    onValueChange(clamped)
                }
            },
            valueRange = minFps..maxFps,
            isLandscape = isLandscape,
            ticks = ticks,
            isActive = isManualFps,
            enabled = !isRecording || isManualFps,
            modifier = weightModifier
        )
    }

    if (isLandscape) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight()) { content(Modifier.weight(1f)) }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { content(Modifier.weight(1f)) }
    }
}
