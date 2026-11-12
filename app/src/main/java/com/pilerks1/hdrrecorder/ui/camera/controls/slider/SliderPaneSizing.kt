package com.pilerks1.hdrrecorder.ui.camera.controls.slider

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import com.pilerks1.hdrrecorder.camera.model.CameraCapabilities
import com.pilerks1.hdrrecorder.camera.model.CameraZoomState
import com.pilerks1.hdrrecorder.ui.camera.controls.CameraControlPanel
import com.pilerks1.hdrrecorder.ui.camera.layout.AxisSpec
import com.pilerks1.hdrrecorder.ui.camera.layout.CameraLayoutCalculator
import com.pilerks1.hdrrecorder.ui.camera.layout.CameraOrientation
import com.pilerks1.hdrrecorder.ui.style.SliderDimensions
import com.pilerks1.hdrrecorder.ui.style.UiTextStyles
import kotlin.math.max

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
    caps: CameraCapabilities?,
    zoomState: CameraZoomState
): SliderPaneSizing {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    return remember(panels, caps, zoomState.minRatio, zoomState.maxRatio, zoomState.inventory, density, textMeasurer) {
        val orientations = listOf(
            CameraLayoutCalculator.axisFor(1, 2),
            CameraLayoutCalculator.axisFor(2, 1)
        )
        SliderPaneSizing(
            thicknesses = orientations.associate { axis ->
                axis.orientation to panels.associateWith { panel ->
                    val scale = panelScale(panel, caps, zoomState)
                    val panelScales = listOfNotNull(scale) + if (
                        panel == CameraControlPanel.ISO && caps?.supportsPostRawSensitivityBoost == true
                    ) listOf(CameraSliderScales.postRawSensitivity(caps)) else emptyList()
                    val liveLabels = if (scale != null) panelScales.flatMap { it.layoutLabels }
                        else fpsLayoutLabels(caps)
                    val tickLabels = panelScales.flatMap { it.ruler.ticks + it.ruler.markers }
                        .map(SliderTick::label)
                        .filter(String::isNotEmpty)
                        .ifEmpty { liveLabels }
                    val standardLiveSize = liveLabels.maxMeasuredSize(
                        textMeasurer,
                        UiTextStyles.Slider.liveReadout
                    )
                    val standardTickSize = tickLabels.maxMeasuredSize(
                        textMeasurer,
                        UiTextStyles.Slider.tick
                    )
                    val thickness = with(density) {
                        if (panel == CameraControlPanel.RESOLUTION) {
                            val fpsLabels = fpsLayoutLabels(caps)
                            val thumbSize = fpsLabels.maxMeasuredSize(
                                textMeasurer,
                                UiTextStyles.Slider.rangeThumb
                            )
                            val tickSize = fpsLabels.maxMeasuredSize(
                                textMeasurer,
                                UiTextStyles.Slider.rangeTick
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
                    if (
                        panel == CameraControlPanel.ISO &&
                        caps?.supportsPostRawSensitivityBoost == true
                    ) {
                        thickness * 2f
                    } else {
                        thickness
                    }
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
    val upperExtent = standardSliderTopExtentPx(
        axis = axis,
        liveLabelWidthPx = liveLabelWidthPx,
        liveLabelHeightPx = liveLabelHeightPx,
        dpToPx = dpToPx
    )
    val lowerExtent = standardSliderBottomExtentPx(
        axis = axis,
        tickLabelWidthPx = tickLabelWidthPx,
        tickLabelHeightPx = tickLabelHeightPx,
        dpToPx = dpToPx
    )
    val actionMinimum = dpToPx(SliderDimensions.actionButtonSize)
    val edgePadding = 2f * dpToPx(SliderDimensions.standardPaneEdgePadding)
    return max(actionMinimum, upperExtent + lowerExtent + edgePadding)
}

internal fun standardSliderTopExtentPx(
    axis: AxisSpec,
    liveLabelWidthPx: Float,
    liveLabelHeightPx: Float,
    dpToPx: (Dp) -> Float
): Float {
    val liveCrossExtent = if (axis.usesVerticalTrack) liveLabelWidthPx else liveLabelHeightPx
    return dpToPx(SliderDimensions.centerIndicatorHalfHeight) +
        dpToPx(SliderDimensions.standardElementGap) +
        liveCrossExtent
}

internal fun standardSliderBottomExtentPx(
    axis: AxisSpec,
    tickLabelWidthPx: Float,
    tickLabelHeightPx: Float,
    dpToPx: (Dp) -> Float
): Float {
    val labelCrossExtent = if (axis.usesVerticalTrack) tickLabelWidthPx else tickLabelHeightPx
    return dpToPx(SliderDimensions.centerIndicatorHalfHeight) +
        dpToPx(SliderDimensions.standardElementGap) +
        dpToPx(SliderDimensions.sliderTickHeight) +
        dpToPx(SliderDimensions.standardElementGap) +
        labelCrossExtent
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
    val tickCenterOffset = dpToPx(SliderDimensions.rangeTickOffset) +
        dpToPx(SliderDimensions.sliderTickHeight) +
        dpToPx(SliderDimensions.rangeTickTextPadding) +
        tickLabelHeightPx / 2f
    val upperExtent = max(
        dpToPx(SliderDimensions.rangeThumbHalfHeight),
        dpToPx(SliderDimensions.rangeThumbLabelOffset) + thumbCrossHalf
    )
    val lowerExtent = max(dpToPx(SliderDimensions.rangeThumbHalfHeight), tickCenterOffset + tickCrossHalf)
    val contentThickness = 2f * max(upperExtent, lowerExtent)
    val padding = 2f * dpToPx(SliderDimensions.resolutionContentPadding)
    val actionMinimum = dpToPx(SliderDimensions.actionButtonSize) + padding
    return max(actionMinimum, contentThickness + padding)
}

private fun panelScale(
    panel: CameraControlPanel,
    caps: CameraCapabilities?,
    zoomState: CameraZoomState
): SliderScale<*>? = when (panel) {
    CameraControlPanel.RESOLUTION -> null
    CameraControlPanel.ZOOM -> CameraSliderScales.zoom(zoomState)
    CameraControlPanel.ISO -> CameraSliderScales.iso(caps)
    CameraControlPanel.SHUTTER -> CameraSliderScales.shutter(caps)
    CameraControlPanel.EXPOSURE_COMPENSATION -> CameraSliderScales.exposureCompensation(caps)
    CameraControlPanel.FOCUS -> CameraSliderScales.focus(caps)
    CameraControlPanel.WHITE_BALANCE -> CameraSliderScales.whiteBalance(caps)
    CameraControlPanel.TINT -> CameraSliderScales.tint()
}

private fun fpsLayoutLabels(caps: CameraCapabilities?): List<String> {
    val values = caps?.fpsRanges
        ?.flatMap { listOf(it.lower, it.upper) }
        ?.distinct()
        .orEmpty()
    return (values.ifEmpty { listOf(15, 60) }).map(Int::toString)
}

internal fun List<String>.maxMeasuredSize(
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
