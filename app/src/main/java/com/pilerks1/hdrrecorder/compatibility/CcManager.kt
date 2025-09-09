package com.pilerks1.hdrrecorder.compatibility

import android.content.Context

/**
 * A simplified manager that returns placeholder data.
 * This removes all complex CameraX/Camera2 logic for now, allowing focus on UI.
 */
class CcManager(private val context: Context) {

    fun getCompatibilityData(): CompatibilityResult {
        // Define placeholder data for both tables.
        val placeholderRows = listOf(
            CompatibilityResult.TableRow(
                quality = "Highest",
                resolution = "4000x3000",
                fps24 = "HLG",
                fps30 = "HLG",
                fps60 = "HLG"
            ),
            CompatibilityResult.TableRow(
                quality = "UHD",
                resolution = "3840x2160",
                fps24 = "HLG",
                fps30 = "HLG",
                fps60 = "None"
            ),
            CompatibilityResult.TableRow(
                quality = "FHD",
                resolution = "1920x1080",
                fps24 = "HLG",
                fps30 = "HLG",
                fps60 = "HLG"
            )
        )

        return CompatibilityResult(
            hardwareLevel = "FULL",
            maxBitrate = "100 Mbps",
            tableRows4by3 = placeholderRows,
            tableRows16by9 = placeholderRows // Using the same placeholder data for both for now
        )
    }
}

