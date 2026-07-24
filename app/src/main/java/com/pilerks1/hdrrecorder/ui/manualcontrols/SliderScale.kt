package com.pilerks1.hdrrecorder.ui.manualcontrols

import com.pilerks1.hdrrecorder.data.camera.CameraCapabilities
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

data class SliderTick(val position: Float, val label: String)

/** Immutable typed scale used by all standard single-value controls. */
class SliderScale<T : Any>(
    val fallbackValue: T,
    val valueToProgress: (T) -> Float,
    val progressToValue: (Float) -> T,
    val formatValue: (T) -> String,
    val ticks: List<SliderTick>,
    val layoutLabels: List<String>
) {
    fun progress(value: T): Float = valueToProgress(value).finiteProgress()
    fun value(progress: Float): T = progressToValue(progress.finiteProgress())
}

data class StandardSliderBinding<T : Any>(
    val isManual: Boolean,
    val effectiveValue: T,
    val onToggleAuto: () -> Unit,
    val onManualValue: (T) -> Unit
)

object SliderScales {
    private const val MAX_TICK_COUNT = 15
    private const val LABEL_INTERVAL = 2
    private const val MAX_EV_MAJOR_LABEL_COUNT = 7

    fun iso(caps: CameraCapabilities?): SliderScale<Int> {
        return isoRange(caps?.isoRange?.lower ?: 100, caps?.isoRange?.upper ?: 3200)
    }

    internal fun isoRange(minimum: Int, maximum: Int): SliderScale<Int> {
        val lower = minimum.coerceAtLeast(1)
        val upper = maximum.coerceAtLeast(lower)
        val toProgress: (Int) -> Float = { value -> logarithmicProgress(value.toDouble(), lower.toDouble(), upper.toDouble()) }
        val fromProgress: (Float) -> Int = { progress ->
            logarithmicValue(progress, lower.toDouble(), upper.toDouble()).roundToInt().coerceIn(lower, upper)
        }
        val format: (Int) -> String = Int::toString
        val ticks = uniformTicks(fromProgress, format, discreteIntervals(lower, upper))
        return SliderScale(
            fallbackValue = midpoint(lower, upper),
            valueToProgress = toProgress,
            progressToValue = fromProgress,
            formatValue = format,
            ticks = ticks,
            layoutLabels = layoutLabels(ticks, format(lower), format(upper))
        )
    }

    fun shutter(caps: CameraCapabilities?): SliderScale<Long> {
        return shutterRange(
            caps?.ssRangeNanos?.lower ?: (1_000_000_000L / 8000L),
            caps?.ssRangeNanos?.upper ?: (1_000_000_000L / 15L)
        )
    }

    internal fun shutterRange(minimum: Long, maximum: Long): SliderScale<Long> {
        val lower = minimum.coerceAtLeast(1L)
        val upper = maximum.coerceAtLeast(lower)
        val toProgress: (Long) -> Float = { value -> logarithmicProgress(value.toDouble(), lower.toDouble(), upper.toDouble()) }
        val fromProgress: (Float) -> Long = { progress ->
            logarithmicValue(progress, lower.toDouble(), upper.toDouble()).roundToLong().coerceIn(lower, upper)
        }
        val format: (Long) -> String = ::formatShutter
        val ticks = uniformTicks(fromProgress, format, discreteIntervals(lower, upper))
        return SliderScale(
            fallbackValue = midpoint(lower, upper),
            valueToProgress = toProgress,
            progressToValue = fromProgress,
            formatValue = format,
            ticks = ticks,
            layoutLabels = layoutLabels(ticks, format(lower), format(upper))
        )
    }

    fun exposureCompensation(caps: CameraCapabilities?): SliderScale<Int> {
        val lower = caps?.evRange?.lower ?: -6
        val upper = (caps?.evRange?.upper ?: 6).coerceAtLeast(lower)
        val stepNumerator = caps?.evStep?.numerator ?: 1
        val stepDenominator = caps?.evStep?.denominator ?: 3
        return exposureCompensationRange(lower, upper, stepNumerator, stepDenominator)
    }

    internal fun exposureCompensationRange(
        minimumIndex: Int,
        maximumIndex: Int,
        stepNumerator: Int,
        stepDenominator: Int
    ): SliderScale<Int> {
        val lower = minimumIndex
        val upper = maximumIndex.coerceAtLeast(lower)
        val toProgress: (Int) -> Float = { value -> linearProgress(value.toFloat(), lower.toFloat(), upper.toFloat()) }
        val fromProgress: (Float) -> Int = { progress ->
            linearValue(progress, lower.toFloat(), upper.toFloat()).roundToInt().coerceIn(lower, upper)
        }
        val format: (Int) -> String = { index ->
            formatEv(index, stepNumerator, stepDenominator)
        }
        val ticks = exposureCompensationTicks(
            lower = lower,
            upper = upper,
            stepNumerator = stepNumerator,
            stepDenominator = stepDenominator,
            toProgress = toProgress,
            format = format
        )
        val valueLabels = buildList {
            add(format(lower))
            if (lower <= 0 && upper >= 0) add(format(0))
            add(format(upper))
        }
        return SliderScale(
            fallbackValue = 0.coerceIn(lower, upper),
            valueToProgress = toProgress,
            progressToValue = fromProgress,
            formatValue = format,
            ticks = ticks,
            layoutLabels = layoutLabels(ticks, *valueLabels.toTypedArray())
        )
    }

    fun focus(caps: CameraCapabilities?): SliderScale<Float> {
        val upper = caps?.focusMinDistanceDiopters?.coerceAtLeast(0f) ?: 10f
        val toProgress: (Float) -> Float = { value -> linearProgress(value, 0f, upper) }
        val fromProgress: (Float) -> Float = { progress -> linearValue(progress, 0f, upper).coerceIn(0f, upper) }
        val format: (Float) -> String = { value ->
            val progress = toProgress(value)
            if (progress <= 0.01f) "INF" else String.format(Locale.US, "%.2f", progress)
        }
        val ticks = uniformTicks(
            fromProgress = fromProgress,
            format = format,
            intervalCount = if (upper > 0f) MAX_TICK_COUNT - 1 else 0
        )
        return SliderScale(
            fallbackValue = 0f,
            valueToProgress = toProgress,
            progressToValue = fromProgress,
            formatValue = format,
            ticks = ticks,
            layoutLabels = layoutLabels(ticks, "INF", "0.00", "1.00")
        )
    }

    fun whiteBalance(caps: CameraCapabilities?): SliderScale<Int> {
        val lower = caps?.cctTemperatureRange?.lower ?: 2000
        val upper = (caps?.cctTemperatureRange?.upper ?: 8000).coerceAtLeast(lower)
        val toProgress: (Int) -> Float = { value -> linearProgress(value.toFloat(), lower.toFloat(), upper.toFloat()) }
        val fromProgress: (Float) -> Int = { progress ->
            linearValue(progress, lower.toFloat(), upper.toFloat()).roundToInt().coerceIn(lower, upper)
        }
        val format: (Int) -> String = Int::toString
        val ticks = uniformTicks(fromProgress, format, discreteIntervals(lower, upper))
        return SliderScale(
            fallbackValue = midpoint(lower, upper),
            valueToProgress = toProgress,
            progressToValue = fromProgress,
            formatValue = format,
            ticks = ticks,
            layoutLabels = layoutLabels(ticks, format(lower), format(upper))
        )
    }

    fun tint(): SliderScale<Int> {
        val lower = -50
        val upper = 50
        val toProgress: (Int) -> Float = { value -> linearProgress(value.toFloat(), lower.toFloat(), upper.toFloat()) }
        val fromProgress: (Float) -> Int = { progress ->
            linearValue(progress, lower.toFloat(), upper.toFloat()).roundToInt().coerceIn(lower, upper)
        }
        val format: (Int) -> String = { value -> if (value > 0) "+$value" else value.toString() }
        val ticks = uniformTicks(fromProgress, format, discreteIntervals(lower, upper))
        return SliderScale(
            fallbackValue = 0,
            valueToProgress = toProgress,
            progressToValue = fromProgress,
            formatValue = format,
            ticks = ticks,
            layoutLabels = layoutLabels(ticks, *(lower..upper).map(format).toTypedArray())
        )
    }

    /** Tick positions stay visually linear even when value conversion is logarithmic. */
    private fun <T : Any> uniformTicks(
        fromProgress: (Float) -> T,
        format: (T) -> String,
        intervalCount: Int = MAX_TICK_COUNT - 1
    ): List<SliderTick> {
        if (intervalCount <= 0) {
            return listOf(SliderTick(0.5f, format(fromProgress(0.5f))))
        }

        val seenLabels = mutableSetOf<String>()
        return (0..intervalCount).map { index ->
            val position = index / intervalCount.toFloat()
            val shouldLabel = index % LABEL_INTERVAL == 0
            val formatted = format(fromProgress(position))
            val label = if (shouldLabel && seenLabels.add(formatted)) formatted else ""
            SliderTick(position, label)
        }
    }

    private fun layoutLabels(ticks: List<SliderTick>, vararg values: String): List<String> =
        (ticks.map(SliderTick::label) + values)
            .filter(String::isNotEmpty)
            .distinct()

    private fun exposureCompensationTicks(
        lower: Int,
        upper: Int,
        stepNumerator: Int,
        stepDenominator: Int,
        toProgress: (Int) -> Float,
        format: (Int) -> String
    ): List<SliderTick> {
        val safeDenominator = stepDenominator.toLong().takeIf { it != 0L } ?: 1L
        val stepEv = abs(stepNumerator.toDouble() / safeDenominator.toDouble())
            .takeIf { it > 0.0 && it.isFinite() } ?: 1.0
        val spanEv = (upper.toLong() - lower.toLong()).toDouble() * stepEv
        val targetMajorInterval = maxOf(
            1.0,
            spanEv / (MAX_EV_MAJOR_LABEL_COUNT - 1).toDouble()
        )
        val majorIntervalEv = niceEvInterval(targetMajorInterval)
        val majorIndexStride = (majorIntervalEv / stepEv)
            .roundToLong()
            .coerceAtLeast(1L)

        return (lower..upper).map { evIndex ->
            val shouldLabel = evIndex == lower ||
                evIndex == upper ||
                evIndex == 0 ||
                evIndex.toLong() % majorIndexStride == 0L
            SliderTick(
                position = toProgress(evIndex),
                label = if (shouldLabel) format(evIndex) else ""
            )
        }
    }

    private fun niceEvInterval(target: Double): Double {
        val magnitude = 10.0.pow(floor(log10(target)))
        val normalized = target / magnitude
        val multiplier = when {
            normalized <= 1.0 -> 1.0
            normalized <= 2.0 -> 2.0
            normalized <= 5.0 -> 5.0
            else -> 10.0
        }
        return multiplier * magnitude
    }

    private fun discreteIntervals(lower: Int, upper: Int): Int =
        (upper.toLong() - lower.toLong()).coerceIn(0L, (MAX_TICK_COUNT - 1).toLong()).toInt()

    private fun discreteIntervals(lower: Long, upper: Long): Int =
        (upper - lower).coerceIn(0L, (MAX_TICK_COUNT - 1).toLong()).toInt()

    private fun logarithmicProgress(value: Double, lower: Double, upper: Double): Float {
        if (upper <= lower) return 0.5f
        val safeValue = value.coerceIn(lower, upper)
        return (log2(safeValue / lower) / log2(upper / lower)).toFloat().finiteProgress()
    }

    private fun logarithmicValue(progress: Float, lower: Double, upper: Double): Double {
        if (upper <= lower) return lower
        return lower * 2.0.pow(progress.finiteProgress() * log2(upper / lower))
    }

    private fun linearProgress(value: Float, lower: Float, upper: Float): Float {
        if (upper <= lower) return 0.5f
        return ((value.coerceIn(lower, upper) - lower) / (upper - lower)).finiteProgress()
    }

    private fun linearValue(progress: Float, lower: Float, upper: Float): Float {
        if (upper <= lower) return lower
        return lower + progress.finiteProgress() * (upper - lower)
    }

    private fun midpoint(lower: Int, upper: Int): Int = lower + (upper - lower) / 2
    private fun midpoint(lower: Long, upper: Long): Long = lower + (upper - lower) / 2L

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
