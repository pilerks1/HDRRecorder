package com.pilerks1.hdrrecorder.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualControlsStateTest {
    @Test
    fun tapToMeterReturnsSensorFocusAndEvControlsToAuto() {
        val manualState = ManualControlsState(
            isManualIso = true,
            isoValue = 400,
            isManualSs = true,
            ssValueNanos = 10_000_000L,
            isManualEv = true,
            evValueIndex = 3,
            isManualFocus = true,
            focusDistanceDiopters = 2f,
            isManualWb = true,
            wbTemp = 5000,
            wbTint = 10,
            isManualFps = true
        )

        val automaticState = manualState.resetForTapToMeter()

        assertFalse(automaticState.isManualIso)
        assertEquals(null, automaticState.isoValue)
        assertFalse(automaticState.isManualSs)
        assertEquals(null, automaticState.ssValueNanos)
        assertFalse(automaticState.isManualEv)
        assertEquals(0, automaticState.evValueIndex)
        assertFalse(automaticState.isManualFocus)
        assertEquals(null, automaticState.focusDistanceDiopters)
        assertFalse(automaticState.isManualWb)
        assertEquals(null, automaticState.wbTemp)
        assertEquals(null, automaticState.wbTint)
        assertTrue(automaticState.isManualFps)
    }

    @Test
    fun tapToMeterKeepsAlreadyAutomaticControlsUnchanged() {
        val automaticState = ManualControlsState()

        assertEquals(automaticState, automaticState.resetForTapToMeter())
    }
}
