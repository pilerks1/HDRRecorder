package com.pilerks1.hdrrecorder.ui.manualcontrols

import com.pilerks1.hdrrecorder.data.camera.CameraCapabilities
import com.pilerks1.hdrrecorder.data.camera.core.CameraZoomState
import com.pilerks1.hdrrecorder.model.NANOS_PER_SECOND
import com.pilerks1.hdrrecorder.model.formatShutterNanos
import com.pilerks1.hdrrecorder.model.trimmedDecimal
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// Also used by the independent FPS range slider.
data class SliderTick(val position: Float, val label: String)

/** Coordinates are measured in regular tick intervals; ticks retain normalized positions. */
data class SliderRuler(
    val minimum: Double,
    val maximum: Double,
    val ticks: List<SliderTick>,
    val markers: List<SliderTick>
) {
    val intervalCount: Float get() = (maximum - minimum).toFloat()
}

/** Immutable typed scale used by all standard single-value controls. */
class SliderScale<T : Any>(
    val valueToProgress: (T) -> Float,
    val progressToValue: (Float) -> T,
    val formatValue: (T) -> String,
    val ruler: SliderRuler,
    val layoutLabels: List<String>
) {
    fun progress(value: T): Float = valueToProgress(value).finiteProgress()
    fun value(progress: Float): T = progressToValue(progress.finiteProgress())
}

data class StandardSliderBinding<T : Any>(
    val isManual: Boolean,
    val effectiveValue: T?,
    val onToggleAuto: () -> Unit,
    val onManualValue: (T) -> Unit
)

object SliderScales {
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

    internal fun isoRange(minimum: Int, maximum: Int): SliderScale<Int> = logarithmicScale(
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

    internal fun shutterRange(minimum: Long, maximum: Long): SliderScale<Long> = logarithmicScale(
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

    internal fun exposureCompensationRange(
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

    private fun <T : Any> linearScale(
        lower: Double,
        upper: Double,
        interval: Double,
        decode: (Double) -> T,
        encode: (T) -> Double,
        format: (T) -> String
    ): SliderScale<T> = rulerScale(
        lower, upper, decode, encode, format,
        toCoordinate = { it / interval },
        fromCoordinate = { it * interval }
    )

    private fun <T : Any> logarithmicScale(
        lower: Double,
        upper: Double,
        reference: Double,
        decode: (Double) -> T,
        encode: (T) -> Double,
        format: (T) -> String,
        tickFormat: (T) -> String = format,
        markers: List<T> = emptyList(),
        extraLabels: List<String> = emptyList()
    ): SliderScale<T> = rulerScale(
        lower, upper, decode, encode, format,
        toCoordinate = { 2.0 * log2(it / reference) },
        fromCoordinate = { reference * 2.0.pow(it / 2.0) },
        tickFormat = tickFormat, markerValues = markers, extraLabels = extraLabels
    )

    private fun <T : Any> rulerScale(
        lower: Double,
        upper: Double,
        decode: (Double) -> T,
        encode: (T) -> Double,
        format: (T) -> String,
        toCoordinate: (Double) -> Double,
        fromCoordinate: (Double) -> Double,
        tickFormat: (T) -> String = format,
        markerValues: List<T> = emptyList(),
        extraLabels: List<String> = emptyList()
    ): SliderScale<T> {
        val minimum = toCoordinate(lower)
        val maximum = toCoordinate(upper)
        val span = maximum - minimum
        fun progress(number: Double): Float = if (span > 0.0) {
            ((toCoordinate(number.coerceIn(lower, upper)) - minimum) / span).toFloat().finiteProgress()
        } else 0.5f
        fun value(position: Float): T = decode(
            fromCoordinate(minimum + position.finiteProgress() * span).coerceIn(lower, upper)
        )
        val ticks = if (span <= 0.0) {
            listOf(SliderTick(0.5f, tickFormat(decode(lower))))
        } else {
            (ceil(minimum).toInt()..floor(maximum).toInt()).map { index ->
                val position = ((index - minimum) / span).toFloat().finiteProgress()
                SliderTick(position, if (index % 2 == 0) tickFormat(value(position)) else "")
            }
        }
        val markers = (listOf(decode(lower), decode(upper)) + markerValues)
            .filter { encode(it) in lower..upper }
            .map { SliderTick(progress(encode(it)), tickFormat(it)) }
            .distinctBy(SliderTick::position)
            .sortedBy(SliderTick::position)
        return SliderScale(
            valueToProgress = { progress(encode(it)) },
            progressToValue = ::value,
            formatValue = format,
            ruler = SliderRuler(minimum, maximum, ticks, markers),
            layoutLabels = (ticks.map(SliderTick::label) + markers.map(SliderTick::label) +
                listOf(format(decode(lower)), format(decode(upper))) + extraLabels)
                .filter(String::isNotEmpty).distinct()
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

private fun Float.finiteProgress(): Float = if (isFinite()) coerceIn(0f, 1f) else 0.5f
