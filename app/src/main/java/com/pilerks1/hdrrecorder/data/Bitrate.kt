package com.pilerks1.hdrrecorder.data

/** Validates the user-facing video bitrate in megabits per second. */
object Bitrate {
    const val MAX_MBPS = 999

    fun parse(value: String): Int? = value.toIntOrNull()?.takeIf { it in 1..MAX_MBPS }

    fun parseAudio(value: String): Int? = value.toIntOrNull()?.takeIf { it in 1..Int.MAX_VALUE / 1_000 }
}
