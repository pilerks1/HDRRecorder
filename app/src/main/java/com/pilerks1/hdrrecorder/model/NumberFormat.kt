package com.pilerks1.hdrrecorder.model

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.roundToLong

const val NANOS_PER_SECOND = 1_000_000_000L

/**
 * Formats a positive value to three significant figures without scientific notation.
 */
fun Double.toSigFigs(): String {
    if (this <= 0) return "0.00"
    if (!isFinite()) return toString()
    var rounded = BigDecimal.valueOf(this)
        .round(MathContext(3, RoundingMode.HALF_UP))
    val missingDigits = 3 - rounded.precision()
    if (missingDigits > 0) {
        rounded = rounded.setScale(rounded.scale() + missingDigits)
    }
    return rounded.toPlainString()
}

/** Fixed decimal places with trailing zeros (and a trailing point) removed. */
fun Double.trimmedDecimal(places: Int): String =
    String.format(Locale.US, "%.${places}f", this).trimEnd('0').trimEnd('.')

/** "1/N" below one second, otherwise seconds with one decimal place. */
fun formatShutterNanos(nanos: Long): String =
    if (nanos in 1 until NANOS_PER_SECOND) "1/${(NANOS_PER_SECOND.toDouble() / nanos).roundToLong()}"
    else "${(nanos.toDouble() / NANOS_PER_SECOND).trimmedDecimal(1)}s"

/** Formats bytes consistently for both free-space and recorded-size readouts. */
fun formatDataSize(bytes: Long): String {
    var value = bytes.coerceAtLeast(0L).toDouble() / 1_000_000.0
    var unitIndex = 0
    while (value >= 1_000.0 && unitIndex < DATA_SIZE_UNITS.lastIndex) {
        value /= 1_000.0
        unitIndex++
    }

    var formatted = value.toSigFigs()
    if (formatted.toDouble() >= 1_000.0 && unitIndex < DATA_SIZE_UNITS.lastIndex) {
        value /= 1_000.0
        unitIndex++
        formatted = value.toSigFigs()
    }
    return "$formatted ${DATA_SIZE_UNITS[unitIndex]}"
}

/** Video bitrate is limited to [com.pilerks1.hdrrecorder.data.Bitrate.MAX_MBPS], so Mbps always fits. */
fun formatBitrateMbps(mbps: Double): String = "${mbps.coerceAtLeast(0.0).toSigFigs()} Mbps"

private val DATA_SIZE_UNITS = listOf("MB", "GB", "TB")
