package com.pilerks1.hdrrecorder.model

import kotlin.math.floor
import kotlin.math.log10

/**
 * Formats a value to a consistent number of significant figures (aiming for ~3),
 * scaling decimal places by magnitude. Shared by storage/bitrate/size readouts.
 */
fun Double.toSigFigs(): String {
    if (this <= 0) return "0.00"
    val magnitude = floor(log10(this)).toInt()
    val scale = (2 - magnitude).coerceAtLeast(0)
    return "%.${scale}f".format(this)
}
