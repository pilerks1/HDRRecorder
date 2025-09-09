package com.pilerks1.hdrrecorder.compatibility

import android.app.Application

/**
 * A simplified manager that returns an empty result.
 * This removes all complex CameraX logic for now.
 */
class CcManager(private val application: Application) {

    fun getCompatibilityData(): CompatibilityResult {
        // Return a blank result to avoid any complex logic for now.
        return CompatibilityResult(
            hardwareLevel = "Checking...",
            maxBitrate = "Checking...",
            tableRows = emptyList(),
        )
    }
}

