package com.pilerks1.hdrrecorder.compatibility

import android.content.Context

/**
 * A simplified manager that returns placeholder data for the detailed compatibility UI.
 * This simulates a real device check, showing a mix of supported and unsupported features.
 */
class CcManager(private val context: Context) {

    fun getCompatibilityData(): CompatibilityResult {
        // --- Sample Data Generation ---

        // Simulates a high-end device that supports everything
        val fullSupport = listOf(
            CompatibilityResult.DynamicRangeSupport("HLG", true),
            CompatibilityResult.DynamicRangeSupport("HDR10", true),
            CompatibilityResult.DynamicRangeSupport("HDR10+", true),
            CompatibilityResult.DynamicRangeSupport("DV10", true)
        )

        // Simulates a common scenario where 60fps recording disables stabilization for some formats
        val partial60fpsSupport = listOf(
            CompatibilityResult.DynamicRangeSupport("HLG", true),
            CompatibilityResult.DynamicRangeSupport("HDR10", true),
            CompatibilityResult.DynamicRangeSupport("HDR10+", false), // No stabilization
            CompatibilityResult.DynamicRangeSupport("DV10", false)      // No stabilization
        )

        // Simulates a scenario where only basic HDR is supported
        val basicSupport = listOf(
            CompatibilityResult.DynamicRangeSupport("HLG", true)
        )

        // Simulates no 10-bit support
        val noSupport = emptyList<CompatibilityResult.DynamicRangeSupport>()

        // --- Build Table Rows with Sample Data ---
        val placeholderRows = listOf(
            CompatibilityResult.TableRow(
                quality = "Highest",
                resolution = "4000x3000",
                fps24 = fullSupport,
                fps30 = fullSupport,
                fps60 = partial60fpsSupport
            ),
            CompatibilityResult.TableRow(
                quality = "UHD",
                resolution = "3840x2160",
                fps24 = fullSupport,
                fps30 = partial60fpsSupport,
                fps60 = basicSupport
            ),
            CompatibilityResult.TableRow(
                quality = "FHD",
                resolution = "1920x1080",
                fps24 = basicSupport,
                fps30 = basicSupport,
                fps60 = noSupport
            )
        )

        return CompatibilityResult(
            hardwareLevel = "LEVEL_3",
            maxBitrate = "120 Mbps",
            tableRows4by3 = placeholderRows,
            tableRows16by9 = placeholderRows // Using the same data for both tables for now
        )
    }
}

