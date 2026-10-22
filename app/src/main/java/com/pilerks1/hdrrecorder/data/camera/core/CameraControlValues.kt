package com.pilerks1.hdrrecorder.data.camera.core

import android.util.Range

data class CameraControlValues(
    
    // ISO
    val isManualIso: Boolean = false,
    val isoValue: Int? = null,
    val isManualPostRawSensitivity: Boolean = false,
    val postRawSensitivityBoost: Int? = null,
    
    // Shutter Speed (SS)
    val isManualSs: Boolean = false,
    val ssValueNanos: Long? = null,
    
    // Exposure Value (EV)
    val isManualEv: Boolean = false,
    val evValueIndex: Int = 0,
    val isLowLightBoostEnabled: Boolean = false,
    
    // Focus
    val isManualFocus: Boolean = false,
    val focusDistanceDiopters: Float? = null,
    
    // White Balance
    val isManualWb: Boolean = false,
    val wbTemp: Int? = null,
    val wbTint: Int? = null,
    
    // FPS
    val isManualFps: Boolean = false,
    val fpsRange: Range<Int>? = null
)

