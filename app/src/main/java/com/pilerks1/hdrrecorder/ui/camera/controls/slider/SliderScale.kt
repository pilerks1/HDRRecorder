package com.pilerks1.hdrrecorder.ui.camera.controls.slider

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow

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

internal fun <T : Any> linearScale(
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

internal fun <T : Any> logarithmicScale(
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

private fun Float.finiteProgress(): Float = if (isFinite()) coerceIn(0f, 1f) else 0.5f
