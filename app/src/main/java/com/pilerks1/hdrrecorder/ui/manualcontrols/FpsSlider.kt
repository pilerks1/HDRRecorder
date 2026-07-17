package com.pilerks1.hdrrecorder.ui.manualcontrols

import android.util.Range
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pilerks1.hdrrecorder.data.camera.CameraCapabilities
import kotlin.math.roundToInt

private data class RangeSliderTickLayout(
    val fraction: Float,
    val heightDp: androidx.compose.ui.unit.Dp,
    val label: TextLayoutResult?
)

@Composable
private fun RangeSliderTickAxis(
    ticks: List<Pair<Float, String>>,
    color: Color,
    isLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textStyle = remember(color) { TextStyle(color = color, fontSize = 7.sp) }
    val tickLayouts = remember(ticks, color, density, textMeasurer) {
        if (ticks.isEmpty()) {
            (0..10).map { index ->
                RangeSliderTickLayout(
                    fraction = index / 10f,
                    heightDp = if (index % 5 == 0) 6.dp else 4.dp,
                    label = null
                )
            }
        } else {
            ticks.map { (fraction, label) ->
                RangeSliderTickLayout(
                    fraction = fraction,
                    heightDp = 6.dp,
                    label = label.takeIf(String::isNotEmpty)?.let {
                        textMeasurer.measure(AnnotatedString(it), style = textStyle)
                    }
                )
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(horizontal = 10.dp)
            .offset(y = 16.dp)
    ) {
        tickLayouts.forEach { tick ->
            val tickX = size.width * tick.fraction
            val tickHeight = with(density) { tick.heightDp.toPx() }
            drawLine(
                color = color,
                start = Offset(tickX, 0f),
                end = Offset(tickX, tickHeight),
                strokeWidth = 1.dp.toPx()
            )
            tick.label?.let { label ->
                val topLeft = Offset(
                    x = tickX - label.size.width / 2f,
                    y = tickHeight + with(density) { 2.dp.toPx() }
                )
                if (isLandscape) {
                    rotate(
                        degrees = 90f,
                        pivot = Offset(tickX, topLeft.y + label.size.height / 2f)
                    ) {
                        drawText(label, topLeft = topLeft)
                    }
                } else {
                    drawText(label, topLeft = topLeft)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotatingRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    isLandscape: Boolean,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    isActive: Boolean = true,
    ticks: List<Pair<Float, String>> = emptyList()
) {
    val textRotation = if (isLandscape) 90f else 0f

    val mainColor = if (isActive) Color.White else if (enabled) Color.Gray else Color.DarkGray
    val secondaryColor = if (isActive) Color.Gray else if (enabled) Color.Gray else Color.DarkGray

    val sliderContent = @Composable {
        Box(contentAlignment = Alignment.Center) {
            RangeSliderTickAxis(
                ticks = ticks,
                color = secondaryColor,
                isLandscape = isLandscape
            )
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
        val ticks = remember(caps?.fpsRanges) {
            val uniqueFpsValues = validRanges.flatMap { listOf(it.lower.toFloat(), it.upper.toFloat()) }.distinct().sorted()
            uniqueFpsValues.map { fps ->
                val fraction = if (maxFps == minFps) 0.5f else (fps - minFps) / (maxFps - minFps)
                fraction to fps.roundToInt().toString()
            }
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
