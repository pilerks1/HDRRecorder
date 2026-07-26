package com.pilerks1.hdrrecorder.model

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

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

/** Formats bytes consistently for both free-space and recorded-size readouts. */
fun formatDataSize(bytes: Long): String {
    val units = listOf("MB", "GB", "TB")
    return formatScaledValue(
        initialValue = bytes.coerceAtLeast(0L).toDouble() / 1_000_000.0,
        units = units
    )
}

/** Formats measured bitrate with Mbps/Gbps promotion for future high-bitrate codecs. */
fun formatBitrateMbps(mbps: Double): String = formatScaledValue(
    initialValue = mbps.coerceAtLeast(0.0),
    units = listOf("Mbps", "Gbps")
)

private fun formatScaledValue(
    initialValue: Double,
    units: List<String>
): String {
    var value = initialValue
    var unitIndex = 0
    while (value >= 1_000.0 && unitIndex < units.lastIndex) {
        value /= 1_000.0
        unitIndex++
    }

    var formatted = value.toSigFigs()
    if (formatted.toDoubleOrNull()?.let { it >= 1_000.0 } == true && unitIndex < units.lastIndex) {
        value /= 1_000.0
        unitIndex++
        formatted = value.toSigFigs()
    }
    return "$formatted ${units[unitIndex]}"
}
