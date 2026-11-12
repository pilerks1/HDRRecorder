package com.pilerks1.hdrrecorder.ui.camera.controls.slider

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.pilerks1.hdrrecorder.ui.camera.layout.AxisSpec
import com.pilerks1.hdrrecorder.ui.style.SliderDimensions
import com.pilerks1.hdrrecorder.ui.style.UiTextStyles

private data class RangeSliderTickLayout(
    val fraction: Float,
    val label: TextLayoutResult?
)

@Composable
internal fun FpsSliderThumb(
    value: Float,
    color: Color,
    textColor: Color,
    textRotation: Float
) {
    Box(
        modifier = Modifier
            .size(
                width = SliderDimensions.rangeThumbWidth,
                height = SliderDimensions.rangeThumbHeight
            )
            .background(color, androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${value.toInt()}",
            color = textColor,
            style = UiTextStyles.Slider.rangeThumb,
            modifier = Modifier
                .wrapContentSize(unbounded = true)
                .offset(y = -SliderDimensions.rangeThumbLabelOffset)
                .graphicsLayer { rotationZ = textRotation }
        )
    }
}

@Composable
internal fun RangeSliderTickAxis(
    ticks: List<SliderTick>,
    color: Color,
    axis: AxisSpec,
    reservedStartPx: Float,
    reservedEndPx: Float,
    thumbHalfWidthPx: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textStyle = remember(color) { UiTextStyles.Slider.rangeTick.copy(color = color) }
    val tickLayouts = remember(ticks, color, density, textMeasurer) {
        if (ticks.isEmpty()) {
            (0..10).map { index ->
                RangeSliderTickLayout(
                    fraction = index / 10f,
                    label = null
                )
            }
        } else {
            ticks.map { tick ->
                RangeSliderTickLayout(
                    fraction = tick.position,
                    label = tick.label.takeIf(String::isNotEmpty)?.let {
                        textMeasurer.measure(AnnotatedString(it), style = textStyle)
                    }
                )
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(SliderDimensions.rangeTickAxisHeight)
            .offset(y = SliderDimensions.rangeTickOffset)
    ) {
        val geometry = rangeSliderGeometry(
            width = size.width,
            reservedStart = reservedStartPx,
            reservedEnd = reservedEndPx,
            thumbHalfWidth = thumbHalfWidthPx
        )
        tickLayouts.forEach { tick ->
            val tickX = geometry.positionAt(tick.fraction)
            val tickHeight = with(density) { SliderDimensions.sliderTickHeight.toPx() }
            drawLine(
                color = color,
                start = Offset(tickX, 0f),
                end = Offset(tickX, tickHeight),
                strokeWidth = SliderDimensions.tickStrokeWidth.toPx()
            )
            tick.label?.let { label ->
                val topLeft = Offset(
                    x = tickX - label.size.width / 2f,
                    y = tickHeight + with(density) { SliderDimensions.rangeTickTextPadding.toPx() }
                )
                rotate(
                    degrees = axis.contentCounterRotationDegrees,
                    pivot = Offset(tickX, topLeft.y + label.size.height / 2f)
                ) {
                    drawText(label, topLeft = topLeft)
                }
            }
        }
    }
}
