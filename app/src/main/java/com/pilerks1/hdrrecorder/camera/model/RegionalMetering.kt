package com.pilerks1.hdrrecorder.camera.model



enum class MeteringMode { TEMPORARY, PERSISTENT }

/** Position in the displayed preview, independent of its pixel dimensions. */
data class MeteringAnchor(val x: Float, val y: Float)

data class RegionalMeteringState(
    val generation: Long = 0L,
    val requestId: Long = 0L,
    val mode: MeteringMode? = null,
    val anchor: MeteringAnchor? = null,
    val eligibleFlags: Int = 0
)
