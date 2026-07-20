package com.pilerks1.hdrrecorder.ui.manualcontrols

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SliderScaleTest {
    @Test
    fun isoScaleRoundTripsEndpointsAndMidpoint() {
        val scale = SliderScales.isoRange(100, 6400)

        assertEquals(100, scale.value(0f))
        assertEquals(6400, scale.value(1f))
        val value = 800
        assertTrue(kotlin.math.abs(value - scale.value(scale.progress(value))) <= 1)
    }

    @Test
    fun shutterScaleRoundTripsWithinNanosecondRoundingTolerance() {
        val scale = SliderScales.shutterRange(125_000L, 1_000_000_000L)
        val value = 10_000_000L
        val roundTrip = scale.value(scale.progress(value))

        assertTrue(kotlin.math.abs(roundTrip - value) <= 2L)
    }

    @Test
    fun shutterLayoutLabelsCoverTheFullFormattedRange() {
        val scale = SliderScales.shutterRange(50_000L, 1_000_000_000L)

        assertTrue("1/20000" in scale.layoutLabels)
        assertTrue("1.0s" in scale.layoutLabels)
    }

    @Test
    fun degenerateRangesRemainFinite() {
        val scale = SliderScales.isoRange(400, 400)

        assertTrue(scale.progress(400).isFinite())
        assertEquals(400, scale.value(0f))
        assertEquals(400, scale.value(1f))
    }

    @Test
    fun ticksAreUniqueAndNormalized() {
        val scale = SliderScales.tint()

        assertEquals(scale.ticks.size, scale.ticks.map { it.position }.distinct().size)
        assertTrue(scale.ticks.all { it.position in 0f..1f })
    }

    @Test
    fun standardTicksAreSparseAndVisuallyLinear() {
        val ticks = SliderScales.isoRange(100, 6400).ticks

        assertEquals(15, ticks.size)
        ticks.zipWithNext().forEach { (first, second) ->
            assertEquals(1f / 14f, second.position - first.position, 0.0001f)
        }
        assertEquals(800, SliderScales.isoRange(100, 6400).value(0.5f))
    }

    @Test
    fun labelsAppearEverySecondTickWithoutRepeating() {
        val ticks = SliderScales.tint().ticks
        val labels = ticks.map { it.label }

        labels.forEachIndexed { index, label ->
            if (index % 2 == 1 && index != labels.lastIndex) assertEquals("", label)
        }
        val visibleLabels = labels.filter(String::isNotEmpty)
        assertEquals(visibleLabels.size, visibleLabels.distinct().size)
    }

    @Test
    fun evTicksRepresentSupportedDeviceIndicesExactlyOnce() {
        val scale = SliderScales.exposureCompensationRange(
            minimumIndex = -3,
            maximumIndex = 3,
            stepNumerator = 1,
            stepDenominator = 3
        )

        assertEquals((-3..3).toList(), scale.ticks.map { scale.value(it.position) })
        assertEquals("-1", scale.formatValue(-3))
        assertEquals("-1/3", scale.formatValue(-1))
        assertEquals("+1/3", scale.formatValue(1))
        assertEquals("+1", scale.formatValue(3))
    }

    @Test
    fun evFormattingUsesTheDeviceReportedFraction() {
        val scale = SliderScales.exposureCompensationRange(-6, 6, 1, 6)

        assertEquals("-5/6", scale.formatValue(-5))
        assertEquals("+1/2", scale.formatValue(3))
    }
}
