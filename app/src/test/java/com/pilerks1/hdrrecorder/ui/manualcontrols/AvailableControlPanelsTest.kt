package com.pilerks1.hdrrecorder.ui.manualcontrols

import com.pilerks1.hdrrecorder.ui.CameraControlPanel
import org.junit.Assert.assertEquals
import org.junit.Test

class AvailableControlPanelsTest {
    @Test
    fun supportedPanelsUseTheSingleRailOrderWithoutStandaloneFps() {
        val panels = orderedControlPanels(
            hasShutter = true,
            hasIso = true,
            hasExposureCompensation = true,
            hasFocus = true,
            hasWhiteBalance = true
        )

        assertEquals(
            listOf(
                CameraControlPanel.RESOLUTION,
                CameraControlPanel.SHUTTER,
                CameraControlPanel.ISO,
                CameraControlPanel.EXPOSURE_COMPENSATION,
                CameraControlPanel.FOCUS,
                CameraControlPanel.WHITE_BALANCE,
                CameraControlPanel.TINT
            ),
            panels
        )
    }

    @Test
    fun resolutionPanelIsAlwaysAvailable() {
        assertEquals(
            listOf(CameraControlPanel.RESOLUTION),
            orderedControlPanels(false, false, false, false, false)
        )
    }
}
