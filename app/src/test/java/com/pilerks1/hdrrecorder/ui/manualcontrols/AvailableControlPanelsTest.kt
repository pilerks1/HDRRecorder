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
                CameraControlPanel.ZOOM,
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
    fun resolutionAndZoomPanelsAreAlwaysAvailable() {
        assertEquals(
            listOf(CameraControlPanel.RESOLUTION, CameraControlPanel.ZOOM),
            orderedControlPanels(false, false, false, false, false)
        )
    }
}
