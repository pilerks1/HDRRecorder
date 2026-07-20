package com.pilerks1.hdrrecorder.ui.manualcontrols

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pilerks1.hdrrecorder.data.camera.CameraCapabilities
import com.pilerks1.hdrrecorder.ui.CameraControlPanel
import com.pilerks1.hdrrecorder.ui.layout.AxisSpec
import com.pilerks1.hdrrecorder.ui.layout.CameraLayoutCalculator
import com.pilerks1.hdrrecorder.ui.layout.CameraOrientation
import kotlin.math.max

internal object SliderPaneStyle {
    val actionButtonSize = 36.dp
    val actionButtonGap = 4.dp
    val actionEdgePadding = 8.dp
    val resolutionContentPadding = 8.dp
    val sliderAxisEdgePadding = 8.dp
    val sliderTickHeight = 8.dp
    val centerIndicatorHalfHeight = 12.dp
    val rangeThumbHalfHeight = 12.dp
    val rangeThumbLabelOffset = 24.dp
    val rangeTickOffset = 16.dp
    val rangeTickTextPadding = 2.dp

    val liveReadoutTextStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
    val tickTextStyle = TextStyle(fontSize = 10.sp)
    val rangeTickTextStyle = TextStyle(fontSize = 7.sp)
    val rangeThumbTextStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold)
}

internal data class SliderVisualSpec(
    val liveReadoutOffset: Dp,
    val tickTextPadding: Dp,
    val tickStartOffset: Dp
)

internal fun sliderVisualSpec(axis: AxisSpec): SliderVisualSpec =
    if (axis.usesVerticalTrack) {
        SliderVisualSpec(
            liveReadoutOffset = (-40).dp,
            tickTextPadding = 20.dp,
            tickStartOffset = 20.dp
        )
    } else {
        SliderVisualSpec(
            liveReadoutOffset = (-22).dp,
            tickTextPadding = 2.dp,
            tickStartOffset = 16.dp
        )
    }

internal data class SliderPaneSizing(
    private val thicknesses: Map<CameraOrientation, Map<CameraControlPanel, Float>>
) {
    fun thicknessFor(axis: AxisSpec, panel: CameraControlPanel?): Float =
        panel?.let { thicknesses[axis.orientation]?.get(it) } ?: 0f

    fun maximumThicknessFor(axis: AxisSpec): Float =
        thicknesses[axis.orientation]?.values?.maxOrNull() ?: 0f
}

@Composable
internal fun rememberSliderPaneSizing(
    panels: List<CameraControlPanel>,
    caps: CameraCapabilities?
): SliderPaneSizing {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    return remember(panels, caps, density, textMeasurer) {
        val orientations = listOf(
            CameraLayoutCalculator.axisFor(1, 2),
            CameraLayoutCalculator.axisFor(2, 1)
        )
        SliderPaneSizing(
            thicknesses = orientations.associate { axis ->
                axis.orientation to panels.associateWith { panel ->
                    val labels = panelLayoutLabels(panel, caps)
                    val standardLiveSize = labels.maxMeasuredSize(textMeasurer, SliderPaneStyle.liveReadoutTextStyle)
                    val standardTickSize = labels.maxMeasuredSize(textMeasurer, SliderPaneStyle.tickTextStyle)
                    val thickness = with(density) {
                        if (panel == CameraControlPanel.RESOLUTION) {
                            val fpsLabels = fpsLayoutLabels(caps)
                            val thumbSize = fpsLabels.maxMeasuredSize(
                                textMeasurer,
                                SliderPaneStyle.rangeThumbTextStyle
                            )
                            val tickSize = fpsLabels.maxMeasuredSize(
                                textMeasurer,
                                SliderPaneStyle.rangeTickTextStyle
                            )
                            rangePaneThicknessPx(
                                axis = axis,
                                thumbLabelWidthPx = thumbSize.width.toFloat(),
                                thumbLabelHeightPx = thumbSize.height.toFloat(),
                                tickLabelWidthPx = tickSize.width.toFloat(),
                                tickLabelHeightPx = tickSize.height.toFloat(),
                                dpToPx = { it.toPx() }
                            )
                        } else {
                            standardPaneThicknessPx(
                                axis = axis,
                                liveLabelWidthPx = standardLiveSize.width.toFloat(),
                                liveLabelHeightPx = standardLiveSize.height.toFloat(),
                                tickLabelWidthPx = standardTickSize.width.toFloat(),
                                tickLabelHeightPx = standardTickSize.height.toFloat(),
                                dpToPx = { it.toPx() }
                            )
                        }
                    }
                    thickness
                }
            }
        )
    }
}

internal fun standardPaneThicknessPx(
    axis: AxisSpec,
    liveLabelWidthPx: Float,
    liveLabelHeightPx: Float,
    tickLabelWidthPx: Float,
    tickLabelHeightPx: Float,
    dpToPx: (Dp) -> Float
): Float {
    val visual = sliderVisualSpec(axis)
    val liveCrossHalf = if (axis.usesVerticalTrack) liveLabelWidthPx / 2f else liveLabelHeightPx / 2f
    val tickCrossHalf = if (axis.usesVerticalTrack) tickLabelWidthPx / 2f else tickLabelHeightPx / 2f
    val tickCenterOffset = dpToPx(visual.tickStartOffset) +
        dpToPx(SliderPaneStyle.sliderTickHeight) +
        dpToPx(visual.tickTextPadding) +
        tickLabelHeightPx / 2f
    val upperExtent = max(
        dpToPx(SliderPaneStyle.centerIndicatorHalfHeight),
        -dpToPx(visual.liveReadoutOffset) + liveCrossHalf
    )
    val lowerExtent = max(
        dpToPx(SliderPaneStyle.centerIndicatorHalfHeight),
        tickCenterOffset + tickCrossHalf
    )
    val actionMinimum = dpToPx(SliderPaneStyle.actionButtonSize)
    return max(actionMinimum, 2f * max(upperExtent, lowerExtent))
}

internal fun rangePaneThicknessPx(
    axis: AxisSpec,
    thumbLabelWidthPx: Float,
    thumbLabelHeightPx: Float,
    tickLabelWidthPx: Float,
    tickLabelHeightPx: Float,
    dpToPx: (Dp) -> Float
): Float {
    val thumbCrossHalf = if (axis.usesVerticalTrack) thumbLabelWidthPx / 2f else thumbLabelHeightPx / 2f
    val tickCrossHalf = if (axis.usesVerticalTrack) tickLabelWidthPx / 2f else tickLabelHeightPx / 2f
    val tickCenterOffset = dpToPx(SliderPaneStyle.rangeTickOffset) +
        dpToPx(SliderPaneStyle.sliderTickHeight) +
        dpToPx(SliderPaneStyle.rangeTickTextPadding) +
        tickLabelHeightPx / 2f
    val upperExtent = max(
        dpToPx(SliderPaneStyle.rangeThumbHalfHeight),
        dpToPx(SliderPaneStyle.rangeThumbLabelOffset) + thumbCrossHalf
    )
    val lowerExtent = max(dpToPx(SliderPaneStyle.rangeThumbHalfHeight), tickCenterOffset + tickCrossHalf)
    val contentThickness = 2f * max(upperExtent, lowerExtent)
    val padding = 2f * dpToPx(SliderPaneStyle.resolutionContentPadding)
    val actionMinimum = dpToPx(SliderPaneStyle.actionButtonSize) + padding
    return max(actionMinimum, contentThickness + padding)
}

private fun panelLayoutLabels(
    panel: CameraControlPanel,
    caps: CameraCapabilities?
): List<String> = when (panel) {
    CameraControlPanel.RESOLUTION -> fpsLayoutLabels(caps)
    CameraControlPanel.ISO -> SliderScales.iso(caps).layoutLabels
    CameraControlPanel.SHUTTER -> SliderScales.shutter(caps).layoutLabels
    CameraControlPanel.EXPOSURE_COMPENSATION -> SliderScales.exposureCompensation(caps).layoutLabels
    CameraControlPanel.FOCUS -> SliderScales.focus(caps).layoutLabels
    CameraControlPanel.WHITE_BALANCE -> SliderScales.whiteBalance(caps).layoutLabels
    CameraControlPanel.TINT -> SliderScales.tint().layoutLabels
}

private fun fpsLayoutLabels(caps: CameraCapabilities?): List<String> {
    val values = caps?.fpsRanges
        ?.flatMap { listOf(it.lower, it.upper) }
        ?.distinct()
        .orEmpty()
    return (values.ifEmpty { listOf(15, 60) }).map(Int::toString)
}

private fun List<String>.maxMeasuredSize(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle
): androidx.compose.ui.unit.IntSize {
    var maxWidth = 0
    var maxHeight = 0
    forEach { label ->
        val size = textMeasurer.measure(AnnotatedString(label), style = style).size
        maxWidth = max(maxWidth, size.width)
        maxHeight = max(maxHeight, size.height)
    }
    return androidx.compose.ui.unit.IntSize(maxWidth, maxHeight)
}
