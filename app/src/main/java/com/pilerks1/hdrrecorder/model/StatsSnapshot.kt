package com.pilerks1.hdrrecorder.model

import android.os.PowerManager

/**
 * Represents a snapshot of all camera and hardware statistics at a single point in time.
 * Only fields actually rendered by the UI are kept.
 */
data class StatsSnapshot(
    val iso: Int = 0,
    val shutterSpeed: Double = 0.0,
    val effectiveFps: Int = 0,
    val droppedFrames: Int = 0,

    // Thermal Stats
    val thermalStatus: String = "NONE",
    val thermalStatusInt: Int = PowerManager.THERMAL_STATUS_NONE,
    val thermalForecastStatus: String = "NONE",

    // Power Stats
    val netPowerWatts: Double = 0.0,

    // Storage & Recording Stats
    val storageRemainingFormatted: String = "N/A",
    val storageRemainingTime: String = "N/A",
    val actualBitrateMbps: Double = 0.0,
    val displayedFileSizeWrittenBytes: Long = 0L,
    val hardwareDurationNanos: Long = 0L
)
