package com.pilerks1.hdrrecorder.model

import android.os.PowerManager

/**
 * Single source of truth for thermal status vocabulary.
 * Maps PowerManager.THERMAL_STATUS_* ints to a stable enum so the string labels
 * and their UI colors can't silently drift apart across files.
 */
enum class ThermalStatus(val label: String) {
    NONE("NONE"),
    LIGHT("LIGHT"),
    MODERATE("MOD"),
    SEVERE("SEVERE"),
    CRITICAL("CRITICAL"),
    EMERGENCY("EMERGENCY"),
    SHUTDOWN("SHUTDOWN"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromInt(status: Int): ThermalStatus = when (status) {
            PowerManager.THERMAL_STATUS_NONE -> NONE
            PowerManager.THERMAL_STATUS_LIGHT -> LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL -> CRITICAL
            PowerManager.THERMAL_STATUS_EMERGENCY -> EMERGENCY
            PowerManager.THERMAL_STATUS_SHUTDOWN -> SHUTDOWN
            else -> UNKNOWN
        }
    }
}

/** A forecast is either an Android thermal status or the numeric headroom supplied by the device. */
sealed interface ThermalForecast {
    data class Status(val status: ThermalStatus) : ThermalForecast
    data class Headroom(val value: Float) : ThermalForecast
}
