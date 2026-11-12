package com.pilerks1.hdrrecorder.ui.camera.controls.panels

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.pilerks1.hdrrecorder.camera.model.CameraZoomState
import com.pilerks1.hdrrecorder.ui.camera.controls.slider.CameraSliderScales
import com.pilerks1.hdrrecorder.ui.camera.controls.slider.RibbonSlider
import com.pilerks1.hdrrecorder.ui.camera.controls.slider.SliderActionButton
import com.pilerks1.hdrrecorder.ui.camera.controls.slider.SliderPanelLayout
import com.pilerks1.hdrrecorder.ui.camera.layout.AxisSpec
import kotlin.math.abs

@Composable
internal fun ZoomControlPanel(
    zoomState: CameraZoomState,
    onZoomRatioChange: (Float) -> Unit,
    onSelectZoomLens: (String) -> Unit,
    axis: AxisSpec
) {
    val scale = remember(zoomState.minRatio, zoomState.maxRatio, zoomState.inventory) { CameraSliderScales.zoom(zoomState) }
    val lenses = zoomState.shortcuts
    SliderPanelLayout(
        axis = axis,
        actionCount = lenses.size,
        modifier = Modifier.fillMaxSize(),
        actions = {
            lenses.forEach { lens ->
                val lensRatio = lens.zoomRatio
                val selected = kotlin.math.abs(zoomState.ratio - lensRatio) < 0.005f
                SliderActionButton(
                    text = CameraSliderScales.formatZoomFactor(lensRatio),
                    background = if (selected) Color.White else Color.DarkGray,
                    textColor = if (selected) Color.Black else Color.White,
                    onClick = { onSelectZoomLens(lens.cameraId) }
                )
            }
        },
        content = { axisReservation ->
            RibbonSlider(
                value = scale.progress(zoomState.ratio),
                onValueChange = { progress ->
                    onZoomRatioChange(scale.value(progress))
                },
                axis = axis,
                labelString = if (zoomState.isReady) scale.formatValue(zoomState.ratio) else CameraSliderScales.UNAVAILABLE_LABEL,
                contentDescription = "Zoom",
                ruler = scale.ruler,
                layoutLabels = scale.layoutLabels,
                axisReservation = axisReservation,
                modifier = Modifier.fillMaxSize()
            )
        }
    )
}
