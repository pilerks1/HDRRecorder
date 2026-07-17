package com.pilerks1.hdrrecorder.model

/**
 * The latest effective values reported by the active camera repeating request.
 *
 * These values are deliberately separate from persisted manual overrides: automatic controls
 * follow telemetry, while enabling manual mode snapshots the latest valid value as a hold.
 */
data class CameraTelemetry(
    val iso: Int? = null,
    val shutterNanos: Long? = null,
    val focusDistanceDiopters: Float? = null,
    val cctTemperatureKelvin: Int? = null,
    val cctTint: Int? = null
)
