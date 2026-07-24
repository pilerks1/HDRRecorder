package com.pilerks1.hdrrecorder.ui.manualcontrols

import androidx.compose.ui.unit.dp
import com.pilerks1.hdrrecorder.ui.layout.CameraLayoutCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class RibbonAxisGeometryTest {
    @Test
    fun actionReservationUsesTheSideWhereItsTouchTargetIsPlaced() {
        val portrait = sliderAxisReservation(CameraLayoutCalculator.axisFor(360, 780), 52.dp)
        val landscape = sliderAxisReservation(CameraLayoutCalculator.axisFor(780, 360), 52.dp)

        assertEquals(52.dp, portrait.start)
        assertEquals(0.dp, portrait.end)
        assertEquals(0.dp, landscape.start)
        assertEquals(52.dp, landscape.end)
    }

    @Test
    fun actionReservationCentersTheTrackInRemainingSpace() {
        val noCutoff = ribbonAxisGeometry(300f, 8f, 0f, 0f)
        val startCutoff = ribbonAxisGeometry(300f, 8f, 52f, 0f)
        val endCutoff = ribbonAxisGeometry(300f, 8f, 0f, 52f)

        assertEquals(8f, noCutoff.axisStart, 0f)
        assertEquals(292f, noCutoff.axisEnd, 0f)
        assertEquals(52f, startCutoff.axisStart, 0f)
        assertEquals(292f, startCutoff.axisEnd, 0f)
        assertEquals(8f, endCutoff.axisStart, 0f)
        assertEquals(248f, endCutoff.axisEnd, 0f)
        assertEquals(172f, (startCutoff.axisStart + startCutoff.axisEnd) / 2f, 0f)
        assertEquals(128f, (endCutoff.axisStart + endCutoff.axisEnd) / 2f, 0f)
    }

    @Test
    fun landscapeLabelKeepsPaddingAfterCounterRotation() {
        val labelTop = ribbonTickLabelTop(
            tickBottom = 10f,
            labelPadding = 2f,
            labelWidth = 50f,
            labelHeight = 10f,
            textRotation = 90f
        )
        val rotatedVisualTop = labelTop + 10f / 2f - 50f / 2f

        assertEquals(12f, rotatedVisualTop, 0f)
    }
}
