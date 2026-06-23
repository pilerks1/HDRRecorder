package com.pilerks1.hdrrecorder.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

data class ThermalPowerSnapshot(
    val thermalStatus: String = "NONE",
    val thermalStatusInt: Int = PowerManager.THERMAL_STATUS_NONE,
    val thermalForecast: Float = 0.0f,
    val thermalForecastStatus: String = "NONE",
    val netPowerWatts: Double = 0.0
)

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

        // Auto-detect if the device is using mA or uA
        val currentAmps = if (kotlin.math.abs(rawCurrent) < 20000) {
            rawCurrent.toDouble() / 1000.0
        } else {
            rawCurrent.toDouble() / 1_000_000.0
        }

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
            thermalForecast = lastForecast,
            thermalForecastStatus = forecastStatus,
            netPowerWatts = netWatts
        )
    }

    private fun formatThermalStatus(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "NONE"
        PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
        PowerManager.THERMAL_STATUS_MODERATE -> "MOD"
        PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
        PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
        else -> "UNKNOWN"
    }

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