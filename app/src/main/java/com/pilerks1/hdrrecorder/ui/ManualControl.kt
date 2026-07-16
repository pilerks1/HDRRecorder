package com.pilerks1.hdrrecorder.ui

/** The finite set of slider panels that can be opened from the manual-controls grid. */
enum class ManualControl(val gridLabel: String, val sliderLabel: String = gridLabel) {
    ISO("ISO"),
    SHUTTER("SS"),
    EXPOSURE_COMPENSATION("EV"),
    FOCUS("FOC", "Focus"),
    WHITE_BALANCE("WB"),
    TINT("TINT", "Tint"),
    FPS("FPS")
}
