package com.pilerks1.hdrrecorder.ui.viewmodels

import com.pilerks1.hdrrecorder.data.camera.CameraCapabilities
import com.pilerks1.hdrrecorder.ui.ManualControlsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualControlStateUpdaterTest {
    private val defaults = ManualControlStateUpdater.ExposureDefaults(
        isoValue = 400,
        shutterNanos = 10_000_000L
    )
    private val fullManualCaps = CameraCapabilities(
        supportsManualSensor = true,
        supportsFullManualExposure = true,
        hasManualIsoControl = true,
        hasManualShutterControl = true
    )

    @Test
    fun unsupportedHybridAeEnablesBothControlsWithCompleteValues() {
        val (state) = ManualControlStateUpdater.calculateNextState(
            oldState = ManualControlsState(),
            caps = fullManualCaps,
            exposureDefaults = defaults
        ) { it.copy(isManualIso = true) }

        assertTrue(state.isManualIso)
        assertTrue(state.isManualSs)
        assertEquals(400, state.isoValue)
        assertEquals(10_000_000L, state.ssValueNanos)
    }

    @Test
    fun unsupportedHybridAeReturnsBothControlsToAutoTogether() {
        val oldState = ManualControlsState(
            isManualIso = true,
            isoValue = 400,
            isManualSs = true,
            ssValueNanos = 10_000_000L
        )
        val (state) = ManualControlStateUpdater.calculateNextState(
            oldState = oldState,
            caps = fullManualCaps,
            exposureDefaults = defaults
        ) { it.copy(isManualIso = false) }

        assertFalse(state.isManualIso)
        assertFalse(state.isManualSs)
    }

    @Test
    fun hybridAeAllowsIndependentControls() {
        val (state) = ManualControlStateUpdater.calculateNextState(
            oldState = ManualControlsState(),
            caps = CameraCapabilities(
                hasManualIsoControl = true,
                supportsIsoPriorityAe = true
            ),
            exposureDefaults = defaults
        ) { it.copy(isManualIso = true, isoValue = 400) }

        assertTrue(state.isManualIso)
        assertFalse(state.isManualSs)
    }

    @Test
    fun partialHybridAeKeepsOnlyTheSupportedControl() {
        val shutterOnlyCaps = CameraCapabilities(
            hasManualShutterControl = true,
            supportsShutterPriorityAe = true
        )

        val (shutterState) = ManualControlStateUpdater.calculateNextState(
            oldState = ManualControlsState(),
            caps = shutterOnlyCaps,
            exposureDefaults = defaults
        ) { it.copy(isManualSs = true, ssValueNanos = 10_000_000L) }
        assertFalse(shutterState.isManualIso)
        assertTrue(shutterState.isManualSs)

        val (isoState) = ManualControlStateUpdater.calculateNextState(
            oldState = ManualControlsState(),
            caps = shutterOnlyCaps,
            exposureDefaults = defaults
        ) { it.copy(isManualIso = true, isoValue = 400) }
        assertFalse(isoState.isManualIso)
        assertFalse(isoState.isManualSs)
        assertEquals(null, isoState.isoValue)
    }

    @Test
    fun returningToAutoClearsManualOverrides() {
        val oldState = ManualControlsState(
            isManualIso = true,
            isoValue = 400,
            isManualSs = true,
            ssValueNanos = 10_000_000L,
            isManualFocus = true,
            focusDistanceDiopters = 2f,
            isManualWb = true,
            wbTemp = 5000,
            wbTint = 10
        )

        val (state) = ManualControlStateUpdater.calculateNextState(
            oldState = oldState,
            caps = CameraCapabilities(
                supportsIsoPriorityAe = true,
                supportsShutterPriorityAe = true,
                supportsCCT = true
            ),
            exposureDefaults = defaults
        ) {
            it.copy(
                isManualIso = false,
                isManualSs = false,
                isManualFocus = false,
                isManualWb = false
            )
        }

        assertEquals(null, state.isoValue)
        assertEquals(null, state.ssValueNanos)
        assertEquals(null, state.focusDistanceDiopters)
        assertEquals(null, state.wbTemp)
        assertEquals(null, state.wbTint)
    }
}
