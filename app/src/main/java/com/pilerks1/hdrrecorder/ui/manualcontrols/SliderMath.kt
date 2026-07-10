package com.pilerks1.hdrrecorder.ui.manualcontrols

import android.util.Range
import com.pilerks1.hdrrecorder.data.camera.CameraCapabilities
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object SliderMath {

    fun formatSliderValue(label: String, progress: Float, caps: CameraCapabilities?): String {
        return when (label) {
            "ISO" -> {
                val iso = mapProgressToIso(progress, caps)
                "${iso ?: "Auto"}"
            }
            "SS" -> {
                val ssNanos = mapProgressToShutter(progress, caps)
                if (ssNanos == null) return "Auto"
                
                val seconds = ssNanos / 1_000_000_000.0
                if (seconds < 1.0) {
                    val fraction = (1.0 / seconds).roundToInt()
                    "1/$fraction"
                } else {
                    "${(seconds * 10).roundToInt() / 10.0}s"
                }
            }
            "EV" -> {
                val evIndex = mapProgressToEvIndex(progress, caps)
                val step = caps?.evStep?.toFloat() ?: (1f / 3f)
                val evValue = evIndex * step
                val thirds = (evValue * 3).roundToInt()
                
                if (thirds == 0) "0"
                else if (thirds % 3 == 0) {
                    val whole = thirds / 3
                    if (whole > 0) "+$whole" else "$whole"
                } else {
                    val whole = thirds / 3
                    val rem = Math.abs(thirds % 3)
                    val sign = if (thirds > 0) "+" else "-"
                    if (whole == 0) "$sign$rem/3" else "$sign${Math.abs(whole)} $rem/3"
                }
            }
            "Focus" -> {
                if (progress <= 0.01f) return "INF"
                return String.format(java.util.Locale.US, "%.2f", progress)
            }
            "WB" -> {
                val temp = mapProgressToWbTemp(progress, caps)
                "$temp"
            }
            "Tint" -> {
                val tint = mapProgressToWbTint(progress)
                val sign = if (tint > 0) "+" else ""
                "$sign$tint"
            }
            else -> ""
        }
    }

    fun getTickPositions(label: String, caps: CameraCapabilities?): List<Pair<Float, String>> {
        val ticks = mutableListOf<Pair<Float, String>>()
        val totalTicks = 18
        
        for (i in 0..totalTicks) {
            val rawFraction = i / totalTicks.toFloat()
            
            when (label) {
                "ISO" -> {
                    val iso = mapProgressToIso(rawFraction, caps)
                    if (iso != null) {
                        val roundedDisplayIso = (Math.round(iso / 5.0) * 5).toInt()
                        val trueFraction = mapIsoToProgress(roundedDisplayIso, caps)
                        ticks.add(trueFraction to formatSliderValue(label, trueFraction, caps))
                    }
                }
                "SS" -> {
                    val ss = mapProgressToShutter(rawFraction, caps)
                    if (ss != null) {
                        val seconds = ss / 1_000_000_000.0
                        if (seconds < 1.0) {
                            val frac = (1.0 / seconds).roundToInt()
                            val roundedFrac = (Math.round(frac / 5.0) * 5).toInt()
                            val roundedExposure = 1.0 / roundedFrac
                            val trueFraction = mapShutterToProgress((roundedExposure * 1_000_000_000).toLong(), caps)
                            ticks.add(trueFraction to formatSliderValue(label, trueFraction, caps))
                        } else {
                            ticks.add(rawFraction to formatSliderValue(label, rawFraction, caps))
                        }
                    }
                }
                "EV" -> {
                    val evIndex = mapProgressToEvIndex(rawFraction, caps)
                    val trueFraction = mapEvIndexToProgress(evIndex, caps)
                    ticks.add(trueFraction to formatSliderValue(label, trueFraction, caps))
                }
                "Focus" -> {
                    ticks.add(rawFraction to formatSliderValue(label, rawFraction, caps))
                }
                "WB" -> {
                    val temp = mapProgressToWbTemp(rawFraction, caps)
                    val roundedTemp = (Math.round(temp / 5.0) * 5).toInt()
                    val trueFraction = mapWbTempToProgress(roundedTemp, caps)
                    ticks.add(trueFraction to formatSliderValue(label, trueFraction, caps))
                }
                "Tint" -> {
                    val tint = mapProgressToWbTint(rawFraction)
                    val roundedTint = (Math.round(tint / 5.0) * 5).toInt()
                    val trueFraction = mapWbTintToProgress(roundedTint)
                    ticks.add(trueFraction to formatSliderValue(label, trueFraction, caps))
                }
            }
        }
        return ticks.distinctBy { it.first }
    }

    fun mapProgressToIso(progress: Float, caps: CameraCapabilities?): Int? {
        val minIso = caps?.isoRange?.lower ?: 100
        val maxIso = caps?.isoRange?.upper ?: 3200
        val stops = log2(maxIso.toDouble() / minIso.toDouble())
        val currentIso = minIso * 2.0.pow(progress.toDouble() * stops)
        return currentIso.roundToInt().coerceIn(minIso, maxIso)
    }
    
    fun mapIsoToProgress(iso: Int, caps: CameraCapabilities?): Float {
        val minIso = caps?.isoRange?.lower ?: 100
        val maxIso = caps?.isoRange?.upper ?: 3200
        val stops = log2(maxIso.toDouble() / minIso.toDouble())
        return (log2(iso.toDouble() / minIso.toDouble()) / stops).toFloat().coerceIn(0f, 1f)
    }

    fun mapProgressToShutter(progress: Float, caps: CameraCapabilities?): Long? {
        val minNanos = caps?.ssRangeNanos?.lower ?: (1_000_000_000L / 8000L)
        val maxNanos = caps?.ssRangeNanos?.upper ?: (1_000_000_000L / 15L)
        val stops = log2(maxNanos.toDouble() / minNanos.toDouble())
        val currentNanos = minNanos * 2.0.pow(progress.toDouble() * stops)
        return currentNanos.roundToLong().coerceIn(minNanos, maxNanos)
    }
    
    fun mapShutterToProgress(nanos: Long, caps: CameraCapabilities?): Float {
        val minNanos = caps?.ssRangeNanos?.lower ?: (1_000_000_000L / 8000L)
        val maxNanos = caps?.ssRangeNanos?.upper ?: (1_000_000_000L / 15L)
        val stops = log2(maxNanos.toDouble() / minNanos.toDouble())
        return (log2(nanos.toDouble() / minNanos.toDouble()) / stops).toFloat().coerceIn(0f, 1f)
    }

    fun mapProgressToEvIndex(progress: Float, caps: CameraCapabilities?): Int {
        val minIndex = caps?.evRange?.lower ?: -6
        val maxIndex = caps?.evRange?.upper ?: 6
        return (minIndex + progress * (maxIndex - minIndex)).roundToInt().coerceIn(minIndex, maxIndex)
    }
    
    fun mapEvIndexToProgress(index: Int, caps: CameraCapabilities?): Float {
        val minIndex = caps?.evRange?.lower ?: -6
        val maxIndex = caps?.evRange?.upper ?: 6
        return if (maxIndex == minIndex) 0.5f else ((index - minIndex).toFloat() / (maxIndex - minIndex).toFloat()).coerceIn(0f, 1f)
    }

    fun mapProgressToFocus(progress: Float, caps: CameraCapabilities?): Float? {
        val maxDiopters = caps?.focusMinDistanceDiopters ?: 10f
        return (progress * maxDiopters).coerceIn(0f, maxDiopters)
    }

    fun mapProgressToWbTemp(progress: Float, caps: CameraCapabilities?): Int {
        val range = caps?.cctTemperatureRange
        val minKelvin = range?.lower ?: 2000
        val maxKelvin = range?.upper ?: 8000
        return (minKelvin + progress * (maxKelvin - minKelvin)).roundToInt().coerceIn(minKelvin, maxKelvin)
    }
    
    fun mapWbTempToProgress(temp: Int, caps: CameraCapabilities?): Float {
        val range = caps?.cctTemperatureRange
        val minKelvin = range?.lower ?: 2000
        val maxKelvin = range?.upper ?: 8000
        return ((temp - minKelvin).toFloat() / (maxKelvin - minKelvin).toFloat()).coerceIn(0f, 1f)
    }

    fun mapProgressToWbTint(progress: Float): Int {
        val minTint = -50
        val maxTint = 50
        return (minTint + progress * (maxTint - minTint)).roundToInt().coerceIn(minTint, maxTint)
    }
    
    fun mapWbTintToProgress(tint: Int): Float {
        val minTint = -50
        val maxTint = 50
        return ((tint - minTint).toFloat() / (maxTint - minTint).toFloat()).coerceIn(0f, 1f)
    }

    // --- FPS Slider with Range Snapping Logic ---
    fun clampFpsRange(newRange: ClosedFloatingPointRange<Float>, oldRange: ClosedFloatingPointRange<Float>, validRanges: List<Range<Int>>): ClosedFloatingPointRange<Float> {
        if (validRanges.isEmpty()) return newRange
        
        val newStart = newRange.start.roundToInt()
        val newEnd = newRange.endInclusive.roundToInt()
        val oldStart = oldRange.start.roundToInt()
        
        val draggedStart = newStart != oldStart
        
        var bestMatch: Range<Int>? = null
        var minDiff = Int.MAX_VALUE
        
        for (range in validRanges) {
            if (draggedStart) {
                // User dragged MIN: find range where lower == newStart, and upper is closest to newEnd
                if (range.lower == newStart) {
                    val diff = kotlin.math.abs(range.upper - newEnd)
                    if (diff < minDiff) {
                        minDiff = diff
                        bestMatch = range
                    }
                }
            } else {
                // User dragged MAX: find range where upper == newEnd, and lower is closest to newStart
                if (range.upper == newEnd) {
                    val diff = kotlin.math.abs(range.lower - newStart)
                    if (diff < minDiff) {
                        minDiff = diff
                        bestMatch = range
                    }
                }
            }
        }
        
        // Fallback if no exact anchor matches (e.g., they dragged to an unsupported min/max entirely)
        if (bestMatch == null) {
            for (range in validRanges) {
                val diff = kotlin.math.abs(range.lower - newStart) + kotlin.math.abs(range.upper - newEnd)
                if (diff < minDiff) {
                    minDiff = diff
                    bestMatch = range
                }
            }
        }
        
        return if (bestMatch != null) bestMatch.lower.toFloat()..bestMatch.upper.toFloat() else newRange
    }
}
