package com.pilerks1.hdrrecorder.ui.camera.controls.slider

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.pilerks1.hdrrecorder.ui.camera.layout.AxisSpec
import com.pilerks1.hdrrecorder.ui.style.SliderDimensions
import com.pilerks1.hdrrecorder.ui.style.UiTextStyles
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.cancel

private val ClipXAxisShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Rectangle(
            Rect(
                left = 0f,
                top = -1000f, // Don't clip Y (which is the preview/black bar direction)
                right = size.width,
                bottom = size.height + 1000f
            )
        )
    }
}

/**
 * @param modeKey changing this (for example, Auto to manual) cancels a fling in progress.
 */
@Composable
fun RibbonSlider(
    value: Float, // 0f..1f
    onValueChange: (Float) -> Unit,
    axis: AxisSpec,
    ruler: SliderRuler,
    layoutLabels: List<String>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onValueChangeFinished: () -> Unit = {},
    enabled: Boolean = true,
    labelString: String = "",
    modeKey: Any? = null,
    axisReservation: SliderAxisReservation = SliderAxisReservation()
) {
    // ==========================================
    // RIBBON SLIDER INTERNAL LAYOUT CONTROLS
    // ==========================================
    val tickToTextPadding = SliderDimensions.standardElementGap
    val tickStartYOffset = SliderDimensions.centerIndicatorHalfHeight + SliderDimensions.standardElementGap
    // ==========================================

    val ticks = ruler.ticks
    val textRotation = axis.contentCounterRotationDegrees

    val density = LocalDensity.current
    val tickStartYOffsetPx = with(density) { tickStartYOffset.toPx() }
    val tickToTextPaddingPx = with(density) { tickToTextPadding.toPx() }
    val virtualLengthPx = with(density) { SliderDimensions.rulerTickSpacing.toPx() } * ruler.intervalCount
    val labelGapPx = with(density) { SliderDimensions.rulerLabelGap.toPx() }
    val tickHeightPx = with(density) { SliderDimensions.sliderTickHeight.toPx() }
    val axisEdgePaddingPx = with(density) { SliderDimensions.sliderAxisEdgePadding.toPx() }
    val axisReservedStartPx = with(density) { axisReservation.start.toPx() }
    val axisReservedEndPx = with(density) { axisReservation.end.toPx() }
    val centerIndicatorHalfHeightPx = with(density) { SliderDimensions.centerIndicatorHalfHeight.toPx() }
    val standardElementGapPx = with(density) { SliderDimensions.standardElementGap.toPx() }
    val standardPaneEdgePaddingPx = with(density) { SliderDimensions.standardPaneEdgePadding.toPx() }
    val tickTextStyle = remember { UiTextStyles.Slider.tick.copy(color = Color.Gray) }
    val liveTextStyle = remember { UiTextStyles.Slider.liveReadout.copy(color = Color.White) }
    val textMeasurer = rememberTextMeasurer()
    val tickLayouts = remember(ruler, density, textMeasurer) {
        fun layout(tick: SliderTick, isMarker: Boolean, auxiliaryLabel: Boolean) = RibbonTickLayout(
            fraction = tick.position,
            label = tick.label.takeIf(String::isNotEmpty)?.let {
                textMeasurer.measure(AnnotatedString(it), style = tickTextStyle)
            },
            isMarker = isMarker,
            auxiliaryLabel = auxiliaryLabel
        )
        fun coincides(first: SliderTick, second: SliderTick): Boolean =
            abs(first.position - second.position) * ruler.intervalCount < 0.00001f
        val regular = ticks.map { tick ->
            val marker = ruler.markers.firstOrNull { coincides(tick, it) }
            layout(
                tick = if (tick.label.isEmpty() && marker != null) marker else tick,
                isMarker = marker != null,
                auxiliaryLabel = tick.label.isEmpty() && marker != null
            )
        }
        val markers = ruler.markers.filter { marker -> ticks.none { coincides(it, marker) } }
            .map { layout(it, isMarker = true, auxiliaryLabel = true) }
        (regular + markers).sortedBy(RibbonTickLayout::fraction)
    }
    // Draw-time scratch storage for auxiliary label bounds; reused instead of allocated per frame.
    val drawnAuxiliaryLabels = remember { ArrayList<ClosedFloatingPointRange<Float>>() }
    val maximumLiveLabelSize = remember(layoutLabels, density, textMeasurer) {
        layoutLabels.maxMeasuredSize(textMeasurer, liveTextStyle)
    }
    val sliderTopExtentPx = standardSliderTopExtentPx(
        axis = axis,
        liveLabelWidthPx = maximumLiveLabelSize.width.toFloat(),
        liveLabelHeightPx = maximumLiveLabelSize.height.toFloat(),
        dpToPx = { with(density) { it.toPx() } }
    )
    val liveLabelCrossExtentPx = if (axis.usesVerticalTrack) {
        maximumLiveLabelSize.width.toFloat()
    } else {
        maximumLiveLabelSize.height.toFloat()
    }
    val liveLabelRelativeCenterPx = -(
        centerIndicatorHalfHeightPx + standardElementGapPx + liveLabelCrossExtentPx / 2f
        )
    val maximumLiveLabelWidth = with(density) { maximumLiveLabelSize.width.toDp() }

    val drawingMetrics = RibbonDrawingMetrics(
        axisEdgePaddingPx = axisEdgePaddingPx,
        axisReservedStartPx = axisReservedStartPx,
        axisReservedEndPx = axisReservedEndPx,
        standardPaneEdgePaddingPx = standardPaneEdgePaddingPx,
        sliderTopExtentPx = sliderTopExtentPx,
        virtualLengthPx = virtualLengthPx,
        labelGapPx = labelGapPx,
        tickStartYOffsetPx = tickStartYOffsetPx,
        tickHeightPx = tickHeightPx,
        tickToTextPaddingPx = tickToTextPaddingPx,
        centerIndicatorHalfHeightPx = centerIndicatorHalfHeightPx
    )

    val currentVal by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    val scope = rememberCoroutineScope()
    // The animatable only drives flings; bounds keep decay inside the slider range.
    val animatable = remember { Animatable(value).apply { updateBounds(0f, 1f) } }
    val fling = remember { FlingHandle() }
    DisposableEffect(modeKey, enabled, ruler) {
        onDispose { fling.job?.cancel() }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .rotateForAxis(axis)
                .fillMaxSize()
                .clip(ClipXAxisShape)
                .semantics {
                    this.contentDescription = contentDescription
                    stateDescription = labelString
                    progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 1f), 0f..1f)
                    if (enabled) {
                        setProgress { target ->
                            currentOnValueChange(target.coerceIn(0f, 1f))
                            currentOnValueChangeFinished()
                            true
                        }
                    } else disabled()
                }
                .pointerInput(enabled, axisReservation, ruler, virtualLengthPx) {
                    if (!enabled || ruler.intervalCount <= 0f) return@pointerInput
                    detectRibbonSliderGestures(
                        virtualLengthPx = virtualLengthPx,
                        ticks = ticks,
                        currentValue = { currentVal },
                        onValueChange = { currentOnValueChange(it) },
                        onFinished = { currentOnValueChangeFinished() },
                        scope = scope,
                        animatable = animatable,
                        fling = fling
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRibbonSlider(value, textRotation, tickLayouts, drawnAuxiliaryLabels, drawingMetrics)
            }

            if (labelString.isNotEmpty()) {
                Text(
                    text = labelString,
                    style = liveTextStyle,
                    maxLines = 1,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        // Offsets depend on the slider surface size (the incoming constraints), so
                        // they are resolved during layout instead of feeding a size back into composition.
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val surfaceWidth = constraints.maxWidth.toFloat()
                            val surfaceHeight = constraints.maxHeight.toFloat()
                            val geometry = ribbonAxisGeometry(
                                width = surfaceWidth,
                                edgePadding = axisEdgePaddingPx,
                                reservedStart = axisReservedStartPx,
                                reservedEnd = axisReservedEndPx
                            )
                            val x = (geometry.axisStart + geometry.axisEnd) / 2f - surfaceWidth / 2f
                            val y = standardPaneEdgePaddingPx + sliderTopExtentPx - surfaceHeight / 2f +
                                liveLabelRelativeCenterPx
                            layout(placeable.width, placeable.height) {
                                placeable.placeRelative(x.roundToInt(), y.roundToInt())
                            }
                        }
                        .width(maximumLiveLabelWidth)
                        .graphicsLayer { rotationZ = textRotation }
                )
            }
        }
    }
}
