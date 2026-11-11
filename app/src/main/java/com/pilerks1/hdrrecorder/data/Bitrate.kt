package com.pilerks1.hdrrecorder.data

/** The authoritative bitrate rules: video in Mbps, audio in kbps; empty text means Auto. */
object Bitrate {
    const val MAX_MBPS = 999

    fun parse(value: String): Int? = value.toIntOrNull()?.takeIf { it in 1..MAX_MBPS }

    fun parseAudio(value: String): Int? = value.toIntOrNull()?.takeIf { it in 1..Int.MAX_VALUE / 1_000 }

    fun isValid(value: String): Boolean = value.isEmpty() || parse(value) != null

    fun isValidAudio(value: String): Boolean = value.isEmpty() || parseAudio(value) != null
}
