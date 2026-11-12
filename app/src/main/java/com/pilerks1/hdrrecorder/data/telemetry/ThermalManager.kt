package com.pilerks1.hdrrecorder.data.telemetry

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import com.pilerks1.hdrrecorder.data.diagnostics.ErrorReporter
import com.pilerks1.hdrrecorder.data.diagnostics.IssueCode
import kotlin.math.abs

/**
 * Converts the raw battery-current property to amps.
 *
 * Android documents BATTERY_PROPERTY_CURRENT_NOW as microamps, but some OEM fuel gauges
 * report milliamps instead. Values below 20,000 are treated as milliamps so those devices
 * retain useful wattage reporting while standard microamp readings continue to work.
 */
private fun batteryCurrentToAmps(rawCurrent: Long): Double? {
    if (rawCurrent == Long.MIN_VALUE) return null
    return if (kotlin.math.abs(rawCurrent) < 20_000L) {
        rawCurrent.toDouble() / 1_000.0
    } else {
        rawCurrent.toDouble() / 1_000_000.0
    }
}

/**
 * Passive thermal and power source. StatsManager's polling loop decides when to query it.
 */
class ThermalManager(private val context: Context, private val reporter: ErrorReporter) {

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

    /** Heavy API call; StatsManager calls it about every 10 seconds off the main thread. */
    fun updateHeadroom() {
        lastForecast = try {
            powerManager.getThermalHeadroom(60)
        } catch (e: Exception) {
            reporter.report(IssueCode.TELEMETRY, "thermalHeadroom", e, notify = false)
            0.0f
        }
    }

    /**
     * Synchronous, non-blocking retrieval of current thermal and power metrics.
     * Uses cached voltage and listener status, but polls current amperage (fast).
     */
    fun getInstantThermalState(): ThermalPowerSample {
        // Poll current amperage (fast memory read on most modern devices)
        val rawCurrent = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

        val currentAmps = batteryCurrentToAmps(rawCurrent) ?: 0.0

        // Use cached voltage from the broadcast receiver
        val netWatts = (cachedVoltageMv.toDouble() / 1000.0) * currentAmps

        val forecast = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            mapHeadroomToStatus(lastForecast)
        } else {
            ThermalForecast.Headroom(lastForecast)
        }

        return ThermalPowerSample(
            thermalStatus = ThermalStatus.fromInt(currentThermalStatus),
            thermalForecast = forecast,
            netPowerWatts = netWatts
        )
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun mapHeadroomToStatus(headroom: Float): ThermalForecast {
        val thresholds = powerManager.thermalHeadroomThresholds
        if (thresholds.isEmpty()) return ThermalForecast.Headroom(headroom)
        val highestStatus = thresholds.keys
            .filter { headroom >= (thresholds[it] ?: Float.MAX_VALUE) }
            .maxByOrNull { it }
        return when {
            highestStatus != null -> ThermalForecast.Status(ThermalStatus.fromInt(highestStatus))
            thresholds.containsKey(PowerManager.THERMAL_STATUS_LIGHT) -> ThermalForecast.Status(ThermalStatus.NONE)
            else -> ThermalForecast.Headroom(headroom)
        }
    }

    fun cleanup() {
        try {
            powerManager.removeThermalStatusListener(thermalStatusListener)
        } finally {
            try {
                context.unregisterReceiver(batteryReceiver)
            } catch (_: IllegalArgumentException) {
                // Already unregistered: cleanup is idempotent.
            }
        }
    }
}
