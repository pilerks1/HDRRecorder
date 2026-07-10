package com.pilerks1.hdrrecorder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThermalManagerTest {
    @Test
    fun supportsStandardMicroampAndOemMilliampReadings() {
        assertEquals(-0.5, batteryCurrentToAmps(-500L)!!, 0.0)
        assertEquals(0.5, batteryCurrentToAmps(500_000L)!!, 0.0)
    }

    @Test
    fun unsupportedBatteryPropertyHasNoReading() {
        assertNull(batteryCurrentToAmps(Long.MIN_VALUE))
    }
}
