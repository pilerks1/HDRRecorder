package com.pilerks1.hdrrecorder.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.pilerks1.hdrrecorder.model.ThermalStatus

data class ThermalPowerSnapshot(
    val thermalStatus: String = "NONE",
    val thermalStatusInt: Int = PowerManager.THERMAL_STATUS_NONE,
    val thermalForecastStatus: String = "NONE",
    val netPowerWatts: Double = 0.0
)

/**
 * Converts the raw battery-current property to amps.
 *
 * Android documents BATTERY_PROPERTY_CURRENT_NOW as microamps, but some OEM fuel gauges
 * report milliamps instead. Values below 20,000 are treated as milliamps so those devices
 * retain useful wattage reporting while standard microamp readings continue to work.
 */
internal fun batteryCurrentToAmps(rawCurrent: Long): Double? {
    if (rawCurrent == Long.MIN_VALUE) return null
    return if (kotlin.math.abs(rawCurrent) < 20_000L) {
        rawCurrent.toDouble() / 1_000.0
    } else {
        rawCurrent.toDouble() / 1_000_000.0
    }
}

/**
 * Manages thermal and power monitoring.
 * Refactored to be passive: it no longer has its own timer.
 * Its state is queried by the unified timer in StatsManager.
 */
class ThermalManager(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    // Cached values for fast, non-blocking retrieval
    @Volatile private var currentThermalStatus = PowerManager.THERMAL_STATUS_NONE
    @Volatile private var lastForecast = 0.0f
    @Volatile private var cachedVoltageMv = 4000

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // ACTION_BATTERY_CHANGED is a sticky broadcast, we get it immediately on register
            // and then whenever battery state changes.
            cachedVoltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 4000) ?: 4000
        }
    }

    private val thermalStatusListener =
        PowerManager.OnThermalStatusChangedListener { status ->
            currentThermalStatus = status
        }

    init {
        powerManager.addThermalStatusListener(thermalStatusListener)
        // Register receiver for voltage tracking
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    /**
     * Heavy API call. Should be called asynchronously (e.g., every 10-20s) 
     * from the unified stats loop.
     */
    fun updateHeadroom() {
        lastForecast = try {
            powerManager.getThermalHeadroom(60)
        } catch (e: Exception) {
            0.0f
        }
    }

    /**
     * Synchronous, non-blocking retrieval of current thermal and power metrics.
     * Uses cached voltage and listener status, but polls current amperage (fast).
     */
    fun getInstantThermalState(): ThermalPowerSnapshot {
        // Poll current amperage (fast memory read on most modern devices)
        val rawCurrent = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

        val currentAmps = batteryCurrentToAmps(rawCurrent) ?: 0.0

        // Use cached voltage from the broadcast receiver
        val netWatts = (cachedVoltageMv.toDouble() / 1000.0) * currentAmps

        val forecastStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            mapHeadroomToStatus(lastForecast)
        } else {
            "%.2f".format(lastForecast)
        }

        return ThermalPowerSnapshot(
            thermalStatus = formatThermalStatus(currentThermalStatus),
            thermalStatusInt = currentThermalStatus,
            thermalForecastStatus = forecastStatus,
            netPowerWatts = netWatts
        )
    }

    private fun formatThermalStatus(status: Int): String = ThermalStatus.fromInt(status).label

    private fun mapHeadroomToStatus(headroom: Float): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val thresholds = powerManager.thermalHeadroomThresholds
            if (thresholds.isNotEmpty()) {
                val highestStatus = thresholds.keys
                    .filter { headroom >= (thresholds[it] ?: Float.MAX_VALUE) }
                    .maxByOrNull { it }

                return if (highestStatus != null) {
                    formatThermalStatus(highestStatus)
                } else {
                    if (thresholds.containsKey(PowerManager.THERMAL_STATUS_LIGHT)) {
                        "NONE"
                    } else {
                        "%.2f".format(headroom)
                    }
                }
            }
        }
        return "%.2f".format(headroom)
    }

    fun cleanup() {
        powerManager.removeThermalStatusListener(thermalStatusListener)
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Ignore if already unregistered or fails
        }
    }
}
