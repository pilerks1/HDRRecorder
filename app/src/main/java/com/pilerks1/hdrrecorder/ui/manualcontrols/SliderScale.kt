package com.pilerks1.hdrrecorder.ui.manualcontrols

import com.pilerks1.hdrrecorder.data.camera.CameraCapabilities
import com.pilerks1.hdrrecorder.data.camera.core.CameraZoomState
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
    val ticks: List<SliderTick> get() = ruler.ticks
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

    fun formatZoomFactor(ratio: Float): String =
        String.format(Locale.US, "%.2f", ratio).trimEnd('0').trimEnd('.') + "x"

    fun zoom(state: CameraZoomState = CameraZoomState()): SliderScale<Float> = logarithmicScale(
        lower = state.minRatio.toDouble(),
        upper = state.maxRatio.toDouble(),
        reference = 1.0,
        decode = Double::toFloat,
        encode = Float::toDouble,
        format = { String.format(Locale.US, "%.2fx", it) },
        tickFormat = ::formatZoomFactor,
        markers = state.inventory?.buttonLenses.orEmpty().mapNotNull { it.zoomRatio },
        extraLabels = listOf("—")
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
        caps?.ssRangeNanos?.lower ?: (1_000_000_000L / 8000L),
        caps?.ssRangeNanos?.upper ?: (1_000_000_000L / 15L)
    )

    internal fun shutterRange(minimum: Long, maximum: Long): SliderScale<Long> = logarithmicScale(
        lower = minimum.coerceAtLeast(1L).toDouble(),
        upper = maximum.coerceAtLeast(minimum.coerceAtLeast(1L)).toDouble(),
        reference = 1_000_000_000.0,
        decode = { it.roundToLong() },
        encode = Long::toDouble,
        format = ::formatShutter
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
        reference: Double = 0.0,
        decode: (Double) -> T,
        encode: (T) -> Double,
        format: (T) -> String
    ): SliderScale<T> = rulerScale(
        lower, upper, decode, encode, format,
        toCoordinate = { (it - reference) / interval },
        fromCoordinate = { reference + it * interval }
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

    private fun formatShutter(nanos: Long): String {
        val seconds = nanos / 1_000_000_000.0
        return if (seconds < 1.0) {
            "1/${(1.0 / seconds).roundToInt()}"
        } else {
            "${(seconds * 10).roundToInt() / 10.0}s"
        }
    }

    private fun formatEv(index: Int, stepNumerator: Int, stepDenominator: Int): String {
        val safeDenominator = stepDenominator.toLong().takeIf { it != 0L } ?: 1L
        val rawNumerator = index.toLong() * stepNumerator.toLong()
        val decimalPlaces = evDecimalPlaces(stepNumerator, safeDenominator)
        val magnitude = abs(rawNumerator.toDouble() / safeDenominator.toDouble())
        val formattedMagnitude = String.format(Locale.US, "%.${decimalPlaces}f", magnitude)
        return when {
            rawNumerator == 0L -> formattedMagnitude
            (rawNumerator > 0L) == (safeDenominator > 0L) -> "+$formattedMagnitude"
            else -> "-$formattedMagnitude"
        }
    }

    private fun evDecimalPlaces(stepNumerator: Int, stepDenominator: Long): Int {
        val numeratorMagnitude = abs(stepNumerator.toLong())
        if (numeratorMagnitude == 0L) return 1

        val denominatorMagnitude = abs(stepDenominator).coerceAtLeast(1L)
        var decimalPlaces = 1
        var decimalScale = 10L
        while (numeratorMagnitude * decimalScale < denominatorMagnitude) {
            decimalPlaces++
            decimalScale *= 10L
        }
        return decimalPlaces
    }
}

private fun Float.finiteProgress(): Float = if (isFinite()) coerceIn(0f, 1f) else 0.5f
