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
    fun evTicksAlignWithTheirSupportedDeviceIndices() {
        val scale = SliderScales.exposureCompensationRange(
            minimumIndex = -30,
            maximumIndex = 30,
            stepNumerator = 1,
            stepDenominator = 10
        )

        val representedIndices = scale.ticks.map { scale.value(it.position) }
        assertEquals(61, scale.ticks.size)
        assertEquals((-30..30).toList(), representedIndices)
        assertEquals(representedIndices.size, representedIndices.distinct().size)
        assertTrue(-30 in representedIndices)
        assertTrue(0 in representedIndices)
        assertTrue(30 in representedIndices)
        scale.ticks.forEach { tick ->
            assertEquals(tick.position, scale.progress(scale.value(tick.position)), 0.000001f)
        }
        assertEquals("-3.0", scale.formatValue(-30))
        assertEquals("-0.3", scale.formatValue(-3))
        assertEquals("0.0", scale.formatValue(0))
        assertEquals("+0.3", scale.formatValue(3))
        assertEquals("+3.0", scale.formatValue(30))
        assertEquals(
            listOf("-3.0", "-2.0", "-1.0", "0.0", "+1.0", "+2.0", "+3.0"),
            scale.ticks.map { it.label }.filter(String::isNotEmpty)
        )
    }

    @Test
    fun evFormattingUsesPrecisionNeededForTheDeviceStep() {
        val scale = SliderScales.exposureCompensationRange(-12, 12, 1, 12)

        assertEquals("-0.42", scale.formatValue(-5))
        assertEquals("+0.50", scale.formatValue(6))
        assertEquals("+1.00", scale.formatValue(12))
    }
}
