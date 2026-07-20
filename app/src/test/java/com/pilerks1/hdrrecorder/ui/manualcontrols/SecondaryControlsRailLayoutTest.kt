package com.pilerks1.hdrrecorder.ui.manualcontrols

import org.junit.Assert.assertEquals
import org.junit.Test

class SecondaryControlsRailLayoutTest {
    @Test
    fun buttonsUseEqualSpaceAcrossTheFullRail() {
        val origins = evenlySpacedOrigins(
            availableLength = 594,
            itemSize = 54,
            itemCount = 7
        )
        val gaps = buildList {
            add(origins.first())
            origins.zipWithNext().forEach { (first, second) ->
                add(second - first - 54)
            }
            add(594 - origins.last() - 54)
        }

        assertEquals(List(8) { 27 }, gaps)
    }

    @Test
    fun buttonsRemainFittedWhenThereIsNoFreeRailSpace() {
        assertEquals(
            listOf(0, 20, 40, 60, 80, 100, 120),
            evenlySpacedOrigins(availableLength = 140, itemSize = 20, itemCount = 7)
        )
    }
}
