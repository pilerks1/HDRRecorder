package com.pilerks1.hdrrecorder.ui.camera.controls.slider

import com.pilerks1.hdrrecorder.NANOS_PER_SECOND
import com.pilerks1.hdrrecorder.camera.model.CameraCapabilities
import com.pilerks1.hdrrecorder.camera.model.CameraZoomState
import com.pilerks1.hdrrecorder.formatShutterNanos
import com.pilerks1.hdrrecorder.trimmedDecimal
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object CameraSliderScales {
    private const val MAX_ISO_LIVE_LABEL = "99999"
    /** Live readout shown while a value is unavailable. */
    const val UNAVAILABLE_LABEL = "—"

    fun formatZoomFactor(ratio: Float): String = ratio.toDouble().trimmedDecimal(2) + "x"

    fun zoom(state: CameraZoomState = CameraZoomState()): SliderScale<Float> = logarithmicScale(
        lower = state.minRatio.toDouble(),
        upper = state.maxRatio.toDouble(),
        reference = 1.0,
        decode = Double::toFloat,
        encode = Float::toDouble,
        format = { String.format(Locale.US, "%.2fx", it) },
        tickFormat = ::formatZoomFactor,
        markers = state.shortcuts.map { it.zoomRatio },
        extraLabels = listOf(UNAVAILABLE_LABEL)
    )

    fun iso(caps: CameraCapabilities?): SliderScale<Int> = isoRange(
        caps?.isoRange?.lower ?: 100,
        caps?.isoRange?.upper ?: 3200
    )

    private fun isoRange(minimum: Int, maximum: Int): SliderScale<Int> = logarithmicScale(
        lower = minimum.coerceAtLeast(1).toDouble(),
        upper = maximum.coerceAtLeast(minimum.coerceAtLeast(1)).toDouble(),
        reference = 100.0,
        decode = { it.roundToInt() },
        encode = Int::toDouble,
        format = Int::toString,
        extraLabels = listOf(MAX_ISO_LIVE_LABEL)
    )

    fun shutter(caps: CameraCapabilities?): SliderScale<Long> = shutterRange(
        caps?.ssRangeNanos?.lower ?: (NANOS_PER_SECOND / 8000L),
        caps?.ssRangeNanos?.upper ?: (NANOS_PER_SECOND / 15L)
    )

    private fun shutterRange(minimum: Long, maximum: Long): SliderScale<Long> = logarithmicScale(
        lower = minimum.coerceAtLeast(1L).toDouble(),
        upper = maximum.coerceAtLeast(minimum.coerceAtLeast(1L)).toDouble(),
        reference = NANOS_PER_SECOND.toDouble(),
        decode = { it.roundToLong() },
        encode = Long::toDouble,
        format = ::formatShutterNanos
    )

    fun exposureCompensation(caps: CameraCapabilities?): SliderScale<Int> = exposureCompensationRange(
        caps?.evRange?.lower ?: -6,
        caps?.evRange?.upper ?: 6,
        caps?.evStep?.numerator ?: 1,
        caps?.evStep?.denominator ?: 3
    )

    private fun exposureCompensationRange(
        minimumIndex: Int,
        maximumIndex: Int,
        stepNumerator: Int,
        stepDenominator: Int
    ): SliderScale<Int> = linearScale(
        lower = minimumIndex.toDouble(),
        upper = maximumIndex.coerceAtLeast(minimumIndex).toDouble(),
        interval = 1.0,
        decode = { it.roundToInt() },
        encode = Int::toDouble,
        format = { formatEv(it, stepNumerator, stepDenominator) }
    )

    fun focus(caps: CameraCapabilities?): SliderScale<Float> {
        val upper = caps?.focusMinDistanceDiopters?.coerceAtLeast(0f) ?: 10f
        return linearScale(
            lower = 0.0,
            upper = if (upper > 0f) 1.0 else 0.0,
            interval = 0.05,
            decode = { (it * upper).toFloat() },
            encode = { if (upper > 0f) it.toDouble() / upper else 0.0 },
            format = { value ->
                val normalized = if (upper > 0f) value / upper else 0f
                if (normalized <= 0.01f) "INF" else String.format(Locale.US, "%.2f", normalized)
            }
        )
    }

    fun whiteBalance(caps: CameraCapabilities?): SliderScale<Int> {
        val lower = caps?.cctTemperatureRange?.lower ?: 2000
        val upper = (caps?.cctTemperatureRange?.upper ?: 8000).coerceAtLeast(lower)
        return linearScale(
            lower.toDouble(), upper.toDouble(), 250.0,
            decode = { it.roundToInt() }, encode = Int::toDouble, format = Int::toString
        )
    }

    fun tint(): SliderScale<Int> = linearScale(
        -50.0, 50.0, 5.0,
        decode = { it.roundToInt() },
        encode = Int::toDouble,
        format = { if (it > 0) "+$it" else it.toString() }
    )

    fun postRawSensitivity(caps: CameraCapabilities?): SliderScale<Int> {
        val lower = caps?.postRawSensitivityBoostRange?.lower ?: 100
        val upper = (caps?.postRawSensitivityBoostRange?.upper ?: 100).coerceAtLeast(lower)
        return logarithmicScale(
            lower.toDouble(), upper.toDouble(), reference = 100.0,
            decode = { it.roundToInt() }, encode = Int::toDouble,
            format = { String.format(Locale.US, "%.2fx", it / 100.0) }
        )
    }

    /**
     * Camera2 reports the step as a Rational, whose denominator is never negative; a zero
     * denominator cannot reach here because an unusable step disables the EV slider.
     */
    private fun formatEv(index: Int, stepNumerator: Int, stepDenominator: Int): String {
        val denominator = stepDenominator.coerceAtLeast(1)
        val ev = index.toDouble() * stepNumerator / denominator
        val formatted = String.format(Locale.US, "%.${evDecimalPlaces(stepNumerator, denominator)}f", abs(ev))
        return when {
            ev > 0.0 -> "+$formatted"
            ev < 0.0 -> "-$formatted"
            else -> formatted
        }
    }

    private fun evDecimalPlaces(stepNumerator: Int, stepDenominator: Int): Int {
        val numeratorMagnitude = abs(stepNumerator.toLong())
        if (numeratorMagnitude == 0L) return 1
        var decimalPlaces = 1
        var decimalScale = 10L
        while (numeratorMagnitude * decimalScale < stepDenominator) {
            decimalPlaces++
            decimalScale *= 10L
        }
        return decimalPlaces
    }
}
