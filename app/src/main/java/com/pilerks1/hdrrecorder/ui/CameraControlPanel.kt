package com.pilerks1.hdrrecorder.ui

/** Every translucent panel that can be opened from the secondary-control rail. */
enum class CameraControlPanel(val gridLabel: String) {
    RESOLUTION("RES"),
    ZOOM("ZOM"),
    ISO("ISO"),
    SHUTTER("SS"),
    EXPOSURE_COMPENSATION("EV"),
    FOCUS("FOC"),
    WHITE_BALANCE("WB"),
    TINT("TINT")
}
