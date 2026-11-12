package com.pilerks1.hdrrecorder.ui.camera.controls

import com.pilerks1.hdrrecorder.camera.model.CameraCapabilities
import com.pilerks1.hdrrecorder.camera.model.LowLightBoostPolicy

fun availableControlPanels(
    caps: CameraCapabilities?
): List<CameraControlPanel> = orderedControlPanels(
    hasShutter = caps?.hasManualShutterControl == true,
    hasIso = caps?.hasManualIsoControl == true,
    hasExposureCompensation = caps?.let {
        it.hasExposureCompensationControl || LowLightBoostPolicy.isSupported(it.supportsLowLightBoost)
    } == true,
    hasFocus = caps?.hasManualFocusControl == true,
    hasWhiteBalance = caps?.supportsCCT == true
)

private fun orderedControlPanels(
    hasShutter: Boolean,
    hasIso: Boolean,
    hasExposureCompensation: Boolean,
    hasFocus: Boolean,
    hasWhiteBalance: Boolean
): List<CameraControlPanel> = buildList {
    add(CameraControlPanel.RESOLUTION)
    add(CameraControlPanel.ZOOM)
    if (hasShutter) add(CameraControlPanel.SHUTTER)
    if (hasIso) add(CameraControlPanel.ISO)
    if (hasExposureCompensation) add(CameraControlPanel.EXPOSURE_COMPENSATION)
    if (hasFocus) add(CameraControlPanel.FOCUS)
    if (hasWhiteBalance) add(CameraControlPanel.WHITE_BALANCE)
    if (hasWhiteBalance) add(CameraControlPanel.TINT)
}

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
