package com.pilerks1.hdrrecorder.data.camera.core

import android.util.Range
import android.util.Rational

data class CameraCapabilities(
    val isoRange: Range<Int>? = null,
    val postRawSensitivityBoostRange: Range<Int>? = null,
    val ssRangeNanos: Range<Long>? = null,
    val evRange: Range<Int>? = null,
    val evStep: Rational? = null,
    val focusMinDistanceDiopters: Float = 0f,
    val fpsRanges: List<Range<Int>> = emptyList(),
    val maxFrameDurationNanos: Long? = null,
    val supportsManualSensor: Boolean = false,
    val supportsFullManualExposure: Boolean = false,
    val hasManualIsoControl: Boolean = false,
    val hasManualShutterControl: Boolean = false,
    val hasExposureCompensationControl: Boolean = false,
    val hasManualFocusControl: Boolean = false,
    val supportsCCT: Boolean = false,
    val cctTemperatureRange: Range<Int>? = null,
    val supportsShutterPriorityAe: Boolean = false,
    val supportsIsoPriorityAe: Boolean = false,
    val supportsAeLock: Boolean = false,
    val supportsPostRawSensitivityBoost: Boolean = false,
    val supportsLowLightBoost: Boolean = false,
    val maxAfRegions: Int = 0,
    val maxAeRegions: Int = 0,
    val maxAwbRegions: Int = 0,
    val afModes: List<Int> = emptyList()
)
