package com.pilerks1.hdrrecorder.ui.manualcontrols

import com.pilerks1.hdrrecorder.ui.layout.CameraLayoutCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SliderPaneSizingTest {
    private val portrait = CameraLayoutCalculator.axisFor(360, 780)
    private val landscape = CameraLayoutCalculator.axisFor(780, 360)
    private val dpToPx: (androidx.compose.ui.unit.Dp) -> Float = { it.value }

    @Test
    fun landscapeThicknessExpandsForLongShutterLabels() {
        val shortLabel = standardPaneThicknessPx(
            axis = landscape,
            liveLabelWidthPx = 28f,
            liveLabelHeightPx = 14f,
            tickLabelWidthPx = 24f,
            tickLabelHeightPx = 10f,
            dpToPx = dpToPx
        )
        val shutterLabel = standardPaneThicknessPx(
            axis = landscape,
            liveLabelWidthPx = 62f,
            liveLabelHeightPx = 14f,
            tickLabelWidthPx = 52f,
            tickLabelHeightPx = 10f,
            dpToPx = dpToPx
        )

        assertTrue(shutterLabel > shortLabel)
    }

    @Test
    fun portraitThicknessUsesLabelHeightRatherThanLabelWidth() {
        val shortLabel = standardPaneThicknessPx(
            axis = portrait,
            liveLabelWidthPx = 28f,
            liveLabelHeightPx = 14f,
            tickLabelWidthPx = 24f,
            tickLabelHeightPx = 10f,
            dpToPx = dpToPx
        )
        val longLabel = standardPaneThicknessPx(
            axis = portrait,
            liveLabelWidthPx = 80f,
            liveLabelHeightPx = 14f,
            tickLabelWidthPx = 72f,
            tickLabelHeightPx = 10f,
            dpToPx = dpToPx
        )

        assertEquals(shortLabel, longLabel, 0f)
    }

    @Test
    fun oneTwoAndThreeButtonGroupsPreserveExistingSpacing() {
        assertEquals(36f, sliderActionGroupLength(1).value, 0f)
        assertEquals(76f, sliderActionGroupLength(2).value, 0f)
        assertEquals(116f, sliderActionGroupLength(3).value, 0f)
    }

    @Test
    fun resolutionRangePaneIncludesItsExistingOuterPadding() {
        val thickness = rangePaneThicknessPx(
            axis = portrait,
            thumbLabelWidthPx = 14f,
            thumbLabelHeightPx = 10f,
            tickLabelWidthPx = 14f,
            tickLabelHeightPx = 7f,
            dpToPx = dpToPx
        )

        assertTrue(thickness >= SliderPaneStyle.actionButtonSize.value + 16f)
    }
}
