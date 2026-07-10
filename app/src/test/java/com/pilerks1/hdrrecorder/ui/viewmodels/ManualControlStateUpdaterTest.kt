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

    @Test
    fun unsupportedHybridAeEnablesBothControlsWithCompleteValues() {
        val (state) = ManualControlStateUpdater.calculateNextState(
            oldState = ManualControlsState(),
            caps = CameraCapabilities(),
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
            caps = CameraCapabilities(),
            exposureDefaults = defaults
        ) { it.copy(isManualIso = false) }

        assertFalse(state.isManualIso)
        assertFalse(state.isManualSs)
    }

    @Test
    fun hybridAeAllowsIndependentControlsAndTracksTheLatestManualInput() {
        val (state) = ManualControlStateUpdater.calculateNextState(
            oldState = ManualControlsState(),
            caps = CameraCapabilities(supportsIsoPriorityAe = true, supportsShutterPriorityAe = true),
            exposureDefaults = defaults
        ) { it.copy(isManualIso = true, isoValue = 400) }

        assertTrue(state.isManualIso)
        assertFalse(state.isManualSs)
        assertEquals("ISO", state.lastManualExposureInput)
    }

    @Test
    fun partialHybridAeFallsBackOnlyForTheUnsupportedControl() {
        val shutterOnlyCaps = CameraCapabilities(supportsShutterPriorityAe = true)

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
        assertTrue(isoState.isManualIso)
        assertTrue(isoState.isManualSs)
        assertEquals(10_000_000L, isoState.ssValueNanos)
    }
}
