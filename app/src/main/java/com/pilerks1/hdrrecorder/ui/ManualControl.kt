package com.pilerks1.hdrrecorder.ui

/** The finite set of slider panels that can be opened from the manual-controls rail. */
enum class ManualControl {
    ISO,
    SHUTTER,
    EXPOSURE_COMPENSATION,
    FOCUS,
    WHITE_BALANCE,
    TINT
}

/** Every translucent panel that can be opened from the secondary-control rail. */
enum class CameraControlPanel(val gridLabel: String, val manualControl: ManualControl? = null) {
    RESOLUTION("RES"),
    ISO("ISO", ManualControl.ISO),
    SHUTTER("SS", ManualControl.SHUTTER),
    EXPOSURE_COMPENSATION("EV", ManualControl.EXPOSURE_COMPENSATION),
    FOCUS("FOC", ManualControl.FOCUS),
    WHITE_BALANCE("WB", ManualControl.WHITE_BALANCE),
    TINT("TINT", ManualControl.TINT)
}
